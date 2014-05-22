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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import com.google.common.util.concurrent.ServiceManager;

import com.linkedin.uif.configuration.ConfigurationKeys;
import com.linkedin.uif.configuration.WorkUnitState;
import com.linkedin.uif.runtime.AbstractJobLauncher;
import com.linkedin.uif.runtime.JobLauncher;
import com.linkedin.uif.runtime.JobLock;
import com.linkedin.uif.runtime.JobState;
import com.linkedin.uif.runtime.Task;
import com.linkedin.uif.runtime.TaskContext;
import com.linkedin.uif.runtime.TaskExecutor;
import com.linkedin.uif.runtime.TaskState;
import com.linkedin.uif.runtime.TaskStateTracker;
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

    private static final Splitter SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

    private final Configuration conf;
    private final FileSystem fs;

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
    protected void runJob(String jobName, Properties jobProps, JobState jobState,
                          List<WorkUnit> workUnits) throws Exception {

        // Add job config properties that also contains all framework config properties
        for (String name : jobProps.stringPropertyNames()) {
            this.conf.set(name, jobProps.getProperty(name));
        }

        Path jarFileDir = new Path(
                jobProps.getProperty(ConfigurationKeys.MR_JOB_ROOT_DIR_KEY),
                jobName + Path.SEPARATOR + "_jars");
        boolean hasJobJars = false;
        if (jobProps.containsKey(ConfigurationKeys.JOB_JAR_FILES_KEY)) {
            hasJobJars = true;
            Iterable<String> jarFiles = SPLITTER.split(
                    jobProps.getProperty(ConfigurationKeys.JOB_JAR_FILES_KEY));
            for (String jarFile : jarFiles) {
                Path srcJarFile = new Path(jarFile);
                // DistributedCache requires absolute path, so we need to use makeQualified.
                Path destJarFile = new Path(this.fs.makeQualified(jarFileDir), srcJarFile.getName());
                // Copy the jar file from local file system to HDFS
                this.fs.copyFromLocalFile(srcJarFile, destJarFile);
                // Then add the jar file on HDFS to the classpath
                LOG.info(String.format("Adding %s to classpath", destJarFile));
                DistributedCache.addFileToClassPath(destJarFile, this.conf, this.fs);
            }
        }

        // Whether to use a reducer to combine task states output by the mappers
        boolean useReducer = Boolean.valueOf(
                jobProps.getProperty(ConfigurationKeys.MR_JOB_USE_REDUCER_KEY, "true"));

        // Preparing a Hadoop MR job
        Job job = Job.getInstance(this.conf, JOB_NAME_PREFIX + jobName);
        job.setJarByClass(MRJobLauncher.class);

        job.setMapperClass(TaskRunner.class);
        job.setReducerClass(TaskStateCollector.class);
        // We need one reducer to collect task states output by the mappers if a
        // reducer is to be used, otherwise the job is mapper-only.
        job.setNumReduceTasks(useReducer ? 1 : 0);

        job.setInputFormatClass(NLineInputFormat.class);
        job.setOutputFormatClass(SequenceFileOutputFormat.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(TaskState.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(TaskState.class);

        // Turn off speculative execution
        job.setSpeculativeExecution(false);

        // Job input path is where input work unit files are stored
        Path jobInputPath = new Path(
                jobProps.getProperty(ConfigurationKeys.MR_JOB_ROOT_DIR_KEY),
                jobName + Path.SEPARATOR + "input");
        // Delete the job input path if it already exists
        if (this.fs.exists(jobInputPath)) {
            LOG.warn("Job input path already exists for job " + job.getJobName());
            this.fs.delete(jobInputPath, true);
        }
        // Job output path is where serialized task states are stored
        Path jobOutputPath = new Path(
                jobProps.getProperty(ConfigurationKeys.MR_JOB_ROOT_DIR_KEY),
                jobName + Path.SEPARATOR + "output");
        // Delete the job output path if it already exists
        if (this.fs.exists(jobOutputPath)) {
            LOG.warn("Job output path already exists for job " + job.getJobName());
            this.fs.delete(jobOutputPath, true);
        }

        // Prepare job input
        Path jobInputFile = prepareJobInput(
                jobProps.getProperty(ConfigurationKeys.JOB_ID_KEY), jobInputPath, workUnits);
        // Set input and output paths
        NLineInputFormat.addInputPath(job, jobInputFile);
        SequenceFileOutputFormat.setOutputPath(job, jobOutputPath);

        try {
            LOG.info("Launching Hadoop MR job " + job.getJobName());
            // Start the MR job and wait for it to complete
            job.waitForCompletion(true);
            jobState.setState(job.isSuccessful() ?
                    JobState.RunningState.SUCCESSFUL :
                    JobState.RunningState.FAILED);
            // Collect the output task states and add them to the job state
            jobState.addTaskStates(collectOutput(jobOutputPath));
        } catch (Exception t) {
            jobState.setState(JobState.RunningState.FAILED);
        } finally {
            // Cleanup job input and output paths upon job completion
            try {
                if (this.fs.exists(jobInputPath)) {
                    this.fs.delete(jobInputPath, true);
                }
            } catch (IOException ioe) {
                LOG.error("Failed to cleanup job input path for job " + job.getJobName());
            }

            try {
                if (this.fs.exists(jobOutputPath)) {
                    this.fs.delete(jobOutputPath, true);
                }
            } catch (IOException ioe) {
                LOG.error("Failed to cleanup job output path for job " + job.getJobName());
            }

            // Cleanup job jar file directory
            try {
                if (hasJobJars) {
                    this.fs.delete(jarFileDir, true);
                }
            } catch (IOException ioe) {
                LOG.error("Failed to cleanup job jars for job " + job.getJobName());
            }
        }
    }

    @Override
    protected JobLock getJobLock(String jobName, Properties jobProps) throws IOException {
        return new MRJobLock(
                this.fs,
                jobProps.getProperty(ConfigurationKeys.MR_JOB_LOCK_DIR_KEY),
                jobName);
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
                Path workUnitFile = new Path(
                        jobInputPath,
                        workUnit.getProp(ConfigurationKeys.TASK_ID_KEY) + ".wu");
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
    private List<TaskState> collectOutput(Path jobOutputPath) throws IOException {
        List<TaskState> taskStates = Lists.newArrayList();
        FileStatus[] fileStatuses = this.fs.globStatus(new Path(jobOutputPath, "part-*"));
        if (fileStatuses == null || fileStatuses.length == 0) {
            return taskStates;
        }

        for (FileStatus status : fileStatuses) {
            // Read out the task states
            SequenceFile.Reader reader = new SequenceFile.Reader(
                    this.fs, status.getPath(), this.fs.getConf());
            Text text = new Text();
            TaskState taskState = new TaskState();
            while (reader.next(text, taskState)) {
                taskStates.add(taskState);
                taskState = new TaskState();
            }
        }

        return taskStates;
    }

    /**
     * The mapper class that runs a given {@link Task}.
     */
    public static class TaskRunner extends Mapper<LongWritable, Text, Text, TaskState> {

        // This is used for the mapper to wait for the task to complete
        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        private FileSystem fs;
        private TaskExecutor taskExecutor;
        private TaskStateTracker taskStateTracker;
        private ServiceManager serviceManager;

        @Override
        protected void setup(Context context) {
            try {
                this.fs = FileSystem.get(context.getConfiguration());
            } catch (IOException ioe) {
                LOG.error("Failed to get a FileSystem instance", ioe);
                return;
            }

            this.taskExecutor = new TaskExecutor(context.getConfiguration());
            this.taskStateTracker = new MRTaskStateTracker(context, this.countDownLatch);
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

            if (this.fs == null || !this.serviceManager.isHealthy()) {
                return;
            }

            WorkUnit workUnit = new WorkUnit(null, null);
            Closer closer = Closer.create();
            // Deserialize the work unit of the assigned task
            try {
                InputStream is = closer.register(this.fs.open(new Path(value.toString())));
                DataInputStream dis = closer.register((new DataInputStream(is)));
                workUnit.readFields(dis);
            } finally {
                closer.close();
            }

            // Create a task for the given work unit and execute the task
            String taskId = workUnit.getProp(ConfigurationKeys.TASK_ID_KEY);
            WorkUnitState workUnitState = new WorkUnitState(workUnit);
            workUnitState.setId(taskId);
            workUnitState.setProp(ConfigurationKeys.JOB_ID_KEY,
                    workUnit.getProp(ConfigurationKeys.JOB_ID_KEY));
            workUnitState.setProp(ConfigurationKeys.TASK_ID_KEY, taskId);
            Task task = new Task(new TaskContext(workUnitState), this.taskStateTracker);
            this.taskStateTracker.registerNewTask(task);
            this.taskExecutor.execute(task);

            LOG.info(String.format("Waiting for task %s to complete...", taskId));
            // Wait for the task to complete
            this.countDownLatch.await();

            // Emit the task state as the output upon task completion
            context.write(new Text(), task.getTaskState());

            // Let the mapper fail if the task failed by throwing an exception
            if (task.getTaskState().getWorkingState() == WorkUnitState.WorkingState.FAILED) {
                throw new IOException(String.format("Task %s of job %s failed",
                        task.getTaskId(), task.getJobId()));
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            try {
                this.serviceManager.stopAsync().awaitStopped(5, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // Ignored
            }
        }
    }

    /**
     * The reducer class that collects {@link TaskState}s output by the mappers.
     */
    public static class TaskStateCollector extends Reducer<Text, TaskState, Text, TaskState> {

        @Override
        protected void reduce(Text key, Iterable<TaskState> values, Context context)
                throws IOException, InterruptedException {

            for (TaskState taskState : values) {
                context.write(key, taskState);
            }
        }
    }
}
