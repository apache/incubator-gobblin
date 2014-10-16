package com.linkedin.uif.runtime.mapreduce;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.CounterGroup;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import com.google.common.util.concurrent.ServiceManager;

import com.linkedin.uif.configuration.ConfigurationKeys;
import com.linkedin.uif.configuration.WorkUnitState;
import com.linkedin.uif.metastore.FsStateStore;
import com.linkedin.uif.metastore.StateStore;
import com.linkedin.uif.metrics.JobMetrics;
import com.linkedin.uif.runtime.AbstractJobLauncher;
import com.linkedin.uif.runtime.FileBasedJobLock;
import com.linkedin.uif.runtime.JobException;
import com.linkedin.uif.runtime.JobLauncher;
import com.linkedin.uif.runtime.JobLock;
import com.linkedin.uif.runtime.JobState;
import com.linkedin.uif.runtime.Task;
import com.linkedin.uif.runtime.TaskContext;
import com.linkedin.uif.runtime.TaskExecutor;
import com.linkedin.uif.runtime.TaskState;
import com.linkedin.uif.runtime.TaskStateTracker;
import com.linkedin.uif.source.workunit.MultiWorkUnit;
import com.linkedin.uif.source.workunit.WorkUnit;

/**
 * An implementation of {@link JobLauncher} that launches a Gobblin job as a Hadoop MR job.
 *
 * <p>
 *     In the Hadoop MP job, each mapper is responsible for executing one task. The mapper
 *     uses its input to get the path of the file storing serialized work unit, deserializes
 *     the work unit and creates a task, and executes the task. {@link TaskExecutor} and
 *     {@link Task} remain the same as in local single-node mode. Each mapper outputs the
 *     task state upon task completion and the single reducer collects all the task states
 *     and write them out as job output.
 * </p>
 *
 * @author ynli
 */
public class MRJobLauncher extends AbstractJobLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(MRJobLauncher.class);

    private static final String JOB_NAME_PREFIX = "Gobblin-";

    private static final String WORK_UNIT_FILE_EXTENSION = ".wu";
    private static final String MULTI_WORK_UNIT_FILE_EXTENSION = ".mwu";

    private static final Splitter SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

    private final Configuration conf;
    private final FileSystem fs;

    private volatile Job job;
    private volatile boolean isCancelled = false;

    public MRJobLauncher(Properties properties) throws Exception {
        super(properties);
        this.conf = new Configuration();
        URI fsUri = URI.create(this.properties.getProperty(
                ConfigurationKeys.FS_URI_KEY,
                ConfigurationKeys.LOCAL_FS_URI));
        this.fs = FileSystem.get(fsUri, this.conf);
    }

    public MRJobLauncher(Properties properties, Configuration conf) throws Exception {
        super(properties);
        this.conf = conf;
        URI fsUri = URI.create(this.properties.getProperty(
                ConfigurationKeys.FS_URI_KEY,
                ConfigurationKeys.LOCAL_FS_URI));
        this.fs = FileSystem.get(fsUri, conf);
    }

    @Override
    public void cancelJob(Properties jobProps) throws JobException {
        String jobName = jobProps.getProperty(ConfigurationKeys.JOB_NAME_KEY);

        if (this.isCancelled || !Optional.fromNullable(this.job).isPresent()) {
            LOG.info(String.format(
                    "Job %s has already been cancelled or has not started yet", jobName));
            return;
        }

        try {
            // Kill the Hadoop MR if has not completed yet
            if (!this.job.isComplete()) {
                LOG.info("Killing Hadoop MR job " + this.job.getJobID());
                this.job.killJob();
            }
            this.isCancelled = true;
        } catch (IOException ioe) {
            LOG.error("Failed to kill the Hadoop MR job " + this.job.getJobID(), ioe);
            throw new JobException("Failed to kill the Hadoop MR job " + this.job.getJobID(), ioe);
        }
    }

    @Override
    protected void runJob(String jobName, Properties jobProps, JobState jobState,
                          List<WorkUnit> workUnits) throws Exception {

        // Add job config properties that also contains all framework config properties
        for (String name : jobProps.stringPropertyNames()) {
            this.conf.set(name, jobProps.getProperty(name));
        }

        Path mrJobDir = new Path(
                jobProps.getProperty(ConfigurationKeys.MR_JOB_ROOT_DIR_KEY), jobName);
        if (this.fs.exists(mrJobDir)) {
            LOG.warn("Job working directory already exists for job " + jobName);
            this.fs.delete(mrJobDir, true);
        }

        Path jarFileDir = new Path(mrJobDir, "_jars");
        // Add framework jars to the classpath for the mappers/reducer
        if (jobProps.containsKey(ConfigurationKeys.FRAMEWORK_JAR_FILES_KEY)) {
            addJars(jarFileDir, jobProps.getProperty(ConfigurationKeys.FRAMEWORK_JAR_FILES_KEY));
        }
        // Add job-specific jars to the classpath for the mappers
        if (jobProps.containsKey(ConfigurationKeys.JOB_JAR_FILES_KEY)) {
            addJars(jarFileDir, jobProps.getProperty(ConfigurationKeys.JOB_JAR_FILES_KEY));
        }

        // Add other files (if any) the job depends on to DistributedCache
        if (jobProps.containsKey(ConfigurationKeys.JOB_LOCAL_FILES_KEY)) {
            addLocalFiles(new Path(mrJobDir, "_files"),
                    jobProps.getProperty(ConfigurationKeys.JOB_LOCAL_FILES_KEY));
        }

        // Add files (if any) already on HDFS that the job depends on to DistributedCache
        if (jobProps.containsKey(ConfigurationKeys.JOB_HDFS_FILES_KEY)) {
            addHDFSFiles(jobProps.getProperty(ConfigurationKeys.JOB_HDFS_FILES_KEY));
        }

        // Let the job and all mappers finish even if some mappers fail
        this.conf.set("mapred.max.map.failures.percent", "100"); // For Hadoop 1.x
        this.conf.set("mapreduce.map.failures.maxpercent", "100"); // For Hadoop 2.x

        // Preparing a Hadoop MR job
        this.job = Job.getInstance(this.conf, JOB_NAME_PREFIX + jobName);
        this.job.setJarByClass(MRJobLauncher.class);
        this.job.setMapperClass(TaskRunner.class);
        // The job is mapper-only
        this.job.setNumReduceTasks(0);

        this.job.setInputFormatClass(NLineInputFormat.class);
        this.job.setOutputFormatClass(NullOutputFormat.class);
        this.job.setMapOutputKeyClass(NullWritable.class);
        this.job.setMapOutputValueClass(NullWritable.class);

        // Turn off speculative execution
        this.job.setSpeculativeExecution(false);

        // Job input path is where input work unit files are stored
        Path jobInputPath = new Path(mrJobDir, "input");
        // Prepare job input
        Path jobInputFile = prepareJobInput(
                jobProps.getProperty(ConfigurationKeys.JOB_ID_KEY), jobInputPath, workUnits);
        NLineInputFormat.addInputPath(this.job, jobInputFile);

        // Job output path is where serialized task states are stored
        Path jobOutputPath = new Path(mrJobDir, "output");
        SequenceFileOutputFormat.setOutputPath(this.job, jobOutputPath);

        if (jobProps.containsKey(ConfigurationKeys.MR_JOB_MAX_MAPPERS_KEY)) {
            // When there is a limit on the number of mappers, each mapper may run
            // multiple tasks if the total number of tasks is larger than the limit.
            int maxMappers = Integer.parseInt(
                    jobProps.getProperty(ConfigurationKeys.MR_JOB_MAX_MAPPERS_KEY));
            if (workUnits.size() > maxMappers) {
                int numTasksPerMapper = workUnits.size() % maxMappers == 0 ?
                        workUnits.size() / maxMappers :
                        workUnits.size() / maxMappers + 1;
                NLineInputFormat.setNumLinesPerSplit(this.job, numTasksPerMapper);
            }
        }

        try {
            LOG.info("Launching Hadoop MR job " + this.job.getJobName());
            // Start the MR job and wait for it to complete
            this.job.waitForCompletion(true);

            if (this.isCancelled) {
                jobState.setState(JobState.RunningState.CANCELLED);
                return;
            }

            jobState.setState(this.job.isSuccessful() ?
                    JobState.RunningState.SUCCESSFUL : JobState.RunningState.FAILED);

            // Collect the output task states and add them to the job state
            jobState.addTaskStates(collectOutput(
                    new Path(jobOutputPath, jobProps.getProperty(ConfigurationKeys.JOB_ID_KEY))));

            // Create a metrics set for this job run from the Hadoop counters.
            // The metrics set is to be persisted to the metrics store later.
            countersToMetrics(
                    this.job.getCounters(),
                    JobMetrics.get(jobName, jobProps.getProperty(ConfigurationKeys.JOB_ID_KEY)));
        } finally {
            // Cleanup job working directory
            try {
                if (this.fs.exists(mrJobDir)) {
                    this.fs.delete(mrJobDir, true);
                }
            } catch (IOException ioe) {
                LOG.error("Failed to cleanup job working directory for job " + this.job.getJobID());
            }
        }
    }

    @Override
    protected JobLock getJobLock(String jobName, Properties jobProps) throws IOException {
        return new FileBasedJobLock(
                this.fs,
                jobProps.getProperty(ConfigurationKeys.JOB_LOCK_DIR_KEY),
                jobName);
    }

    /**
     * Add framework or job-specific jars to the classpath through DistributedCache
     * so the mappers can use them.
     */
    private void addJars(Path jarFileDir, String jarFileList) throws IOException {
    	LocalFileSystem lfs = FileSystem.getLocal(conf);
        for (String jarFile : SPLITTER.split(jarFileList)) {
            Path srcJarFile = new Path(jarFile);
            FileStatus[] fileStatusList = lfs.globStatus(srcJarFile);
            for (FileStatus status: fileStatusList ){
	            // DistributedCache requires absolute path, so we need to use makeQualified.
	            Path destJarFile = new Path(this.fs.makeQualified(jarFileDir), status.getPath().getName());
	            // Copy the jar file from local file system to HDFS
	            this.fs.copyFromLocalFile(status.getPath(), destJarFile);
	            // Then add the jar file on HDFS to the classpath
	            LOG.info(String.format("Adding %s to classpath", destJarFile));
	            DistributedCache.addFileToClassPath(destJarFile, this.conf, this.fs);

            }
        }
    }

    /**
     * Add local non-jar files the job depends on to DistributedCache.
     */
    private void addLocalFiles(Path jobFileDir, String jobFileList) throws IOException {
        DistributedCache.createSymlink(this.conf);
        for (String jobFile : SPLITTER.split(jobFileList)) {
            Path srcJobFile = new Path(jobFile);
            // DistributedCache requires absolute path, so we need to use makeQualified.
            Path destJobFile = new Path(this.fs.makeQualified(jobFileDir), srcJobFile.getName());
            // Copy the file from local file system to HDFS
            this.fs.copyFromLocalFile(srcJobFile, destJobFile);
            // Create a URI that is in the form path#symlink
            URI destFileUri = URI.create(destJobFile.toUri().getPath() + "#" + destJobFile.getName());
            LOG.info(String.format("Adding %s to DistributedCache", destFileUri));
            // Finally add the file to DistributedCache with a symlink named after the file name
            DistributedCache.addCacheFile(destFileUri, this.conf);
        }
    }

    /**
     * Add non-jar files already on HDFS that the job depends on to DistributedCache.
     */
    private void addHDFSFiles(String jobFileList) throws IOException {
        DistributedCache.createSymlink(this.conf);
        for (String jobFile : SPLITTER.split(jobFileList)) {
            Path srcJobFile = new Path(jobFile);
            // Create a URI that is in the form path#symlink
            URI srcFileUri = URI.create(srcJobFile.toUri().getPath() + "#" + srcJobFile.getName());
            LOG.info(String.format("Adding %s to DistributedCache", srcFileUri));
            // Finally add the file to DistributedCache with a symlink named after the file name
            DistributedCache.addCacheFile(srcFileUri, this.conf);
        }
    }

    /**
     * Prepare the job input.
     */
    private Path prepareJobInput(String jobId, Path jobInputPath,
                                 List<WorkUnit> workUnits) throws IOException {

        Closer closer = Closer.create();
        try {
            // The job input is a file named after the job ID listing all work unit file paths
            Path jobInputFile = new Path(jobInputPath, jobId + ".wulist");
            // Open the job input file
            OutputStream os = closer.register(this.fs.create(jobInputFile));
            Writer osw = closer.register(new OutputStreamWriter(os));
            Writer bw = closer.register(new BufferedWriter(osw));

            // Serialize each work unit into a file named after the task ID
            for (WorkUnit workUnit : workUnits) {
                Path workUnitFile = new Path(jobInputPath,
                        workUnit.getProp(ConfigurationKeys.TASK_ID_KEY) +
                                ((workUnit instanceof MultiWorkUnit) ?
                                        MULTI_WORK_UNIT_FILE_EXTENSION : WORK_UNIT_FILE_EXTENSION));
                os = closer.register(this.fs.create(workUnitFile));
                DataOutputStream dos = closer.register(new DataOutputStream(os));
                workUnit.write(dos);
                // Append the work unit file path to the job input file
                bw.write(workUnitFile.toUri().getPath() + "\n");
            }

            return jobInputFile;
        } finally {
            closer.close();
        }
    }

    /**
     * Collect the output {@link TaskState}s of the job as a list.
     */
    private List<TaskState> collectOutput(Path taskStatePath) throws IOException {
        List<TaskState> taskStates = Lists.newArrayList();

        FileStatus[] fileStatuses = this.fs.listStatus(taskStatePath, new PathFilter() {
            @Override
            public boolean accept(Path path) {
                return path.getName().endsWith(TASK_STATE_STORE_TABLE_SUFFIX);
            }
        });
        if (fileStatuses == null || fileStatuses.length == 0) {
            return taskStates;
        }

        for (FileStatus status : fileStatuses) {
            Closer closer = Closer.create();
            try {
                // Read out the task states
                SequenceFile.Reader reader = closer.register(
                        new SequenceFile.Reader(this.fs, status.getPath(), this.fs.getConf()));
                Text text = new Text();
                TaskState taskState = new TaskState();
                while (reader.next(text, taskState)) {
                    taskStates.add(taskState);
                    taskState = new TaskState();
                }
            } finally {
                closer.close();
            }
        }

        LOG.info(String.format("Collected task state of %d completed tasks", taskStates.size()));

        return taskStates;
    }

    /**
     * Create a {@link JobMetrics} instance for this job run from the Hadoop counters.
     */
    private void countersToMetrics(Counters counters, JobMetrics metrics) {
        // Write job-level counters
        CounterGroup jobCounterGroup = counters.getGroup(JobMetrics.MetricGroup.JOB.name());
        for (Counter jobCounter : jobCounterGroup) {
            metrics.getCounter(jobCounter.getName()).inc(jobCounter.getValue());
        }

        // Write task-level counters
        CounterGroup taskCounterGroup = counters.getGroup(JobMetrics.MetricGroup.TASK.name());
        for (Counter taskCounter : taskCounterGroup) {
            metrics.getCounter(taskCounter.getName()).inc(taskCounter.getValue());
        }
    }

    /**
     * The mapper class that runs a given {@link Task}.
     */
    public static class TaskRunner extends Mapper<LongWritable, Text, NullWritable, NullWritable> {

        private FileSystem fs;
        private StateStore taskStateStore;
        private TaskExecutor taskExecutor;
        private TaskStateTracker taskStateTracker;
        private ServiceManager serviceManager;

        @Override
        protected void setup(Context context) {
            try {
                this.fs = FileSystem.get(context.getConfiguration());
                this.taskStateStore = new FsStateStore(
                        this.fs,
                        SequenceFileOutputFormat.getOutputPath(context).toUri().getPath(),
                        TaskState.class);
            } catch (IOException ioe) {
                LOG.error("Failed to setup the mapper task", ioe);
                return;
            }

            this.taskExecutor = new TaskExecutor(context.getConfiguration());
            this.taskStateTracker = new MRTaskStateTracker(context);
            this.serviceManager = new ServiceManager(Lists.newArrayList(
                    this.taskExecutor,
                    this.taskStateTracker));
            try {
                this.serviceManager.startAsync().awaitHealthy(5, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // Ignored
            }
        }

        @Override
        public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {

            if (!Optional.fromNullable(this.fs).isPresent() || !this.serviceManager.isHealthy()) {
                LOG.error("Not running the task because the mapper was not setup properly");
                return;
            }

            WorkUnit workUnit = (value.toString().endsWith(MULTI_WORK_UNIT_FILE_EXTENSION) ?
                    new MultiWorkUnit() : new WorkUnit());
            Closer closer = Closer.create();
            // Deserialize the work unit of the assigned task
            try {
                InputStream is = closer.register(this.fs.open(new Path(value.toString())));
                DataInputStream dis = closer.register((new DataInputStream(is)));
                workUnit.readFields(dis);
            } finally {
                closer.close();
            }

            runWorkUnits(workUnit instanceof MultiWorkUnit ?
                    ((MultiWorkUnit) workUnit).getWorkUnits() : Lists.newArrayList(workUnit));
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            try {
                this.serviceManager.stopAsync().awaitStopped(5, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // Ignored
            }
        }

        /**
         * Run the given list of {@link WorkUnit}s sequentially. If any work unit/task fails,
         * an {@link java.io.IOException} is thrown so the mapper is failed and retried.
         */
        private void runWorkUnits(List<WorkUnit> workUnits) throws IOException {
            boolean hasTaskFailure = false;
            for (WorkUnit workUnit : workUnits) {
                String jobId = workUnit.getProp(ConfigurationKeys.JOB_ID_KEY);
                String taskId = workUnit.getProp(ConfigurationKeys.TASK_ID_KEY);

                // Delete the task state file for the task if it already exists.
                // This usually happens if the task is retried upon failure.
                if (this.taskStateStore.exists(jobId, taskId + TASK_STATE_STORE_TABLE_SUFFIX)) {
                    this.taskStateStore.delete(jobId, taskId + TASK_STATE_STORE_TABLE_SUFFIX);
                }

                WorkUnitState workUnitState = new WorkUnitState(workUnit);
                workUnitState.setId(taskId);
                workUnitState.setProp(ConfigurationKeys.JOB_ID_KEY, jobId);
                workUnitState.setProp(ConfigurationKeys.TASK_ID_KEY, taskId);

                // Create a new task from the work unit and register the task
                Task task = new Task(new TaskContext(workUnitState), this.taskStateTracker);
                this.taskStateTracker.registerNewTask(task);
                LOG.info(String.format("Submitting and waiting for task %s to complete...", taskId));
                try {
                    // Submit the task to run and wait for it to complete
                    this.taskExecutor.submit(task).get();
                } catch (Exception e) {
                    LOG.error("Failed to submit and execute task " + task.getTaskId(), e);
                }

                // Write out the task state upon task completion
                LOG.info("Writing task state for task " + taskId);
                this.taskStateStore.put(jobId, taskId + TASK_STATE_STORE_TABLE_SUFFIX, task.getTaskState());

                if (task.getTaskState().getWorkingState() == WorkUnitState.WorkingState.FAILED) {
                    LOG.error(String.format("Task %s of job %s failed", task.getTaskId(), task.getJobId()));
                    hasTaskFailure = true;
                }
            }

            if (hasTaskFailure) {
                throw new IOException("Not all tasks completed successfully");
            }
        }
    }
}
