package gobblin.compaction.mapreduce;

import gobblin.compaction.suite.CompactionAvroSuite;
import gobblin.compaction.action.CompactionCompleteAction;
import gobblin.compaction.suite.CompactionSuite;
import gobblin.compaction.verify.CompactionVerifier;
import gobblin.dataset.Dataset;
import gobblin.runtime.TaskContext;
import gobblin.runtime.mapreduce.MRTask;

import java.util.List;
import java.io.IOException;


import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.mapreduce.Job;

/**
 * Customized task of type {@link MRTask}, which runs MR job to compact dataset.
 * The job creation is delegated to {@link CompactionSuite#createJob(Dataset)}
 * After job is created, {@link MRCompactionTask#run()} is invoked and after compaction is finished.
 * a callback {@link CompactionSuite#getCompactionCompleteActions()} will be invoked
 */
@Slf4j
public class MRCompactionTask extends MRTask {
  protected final CompactionSuite suite;
  protected final Dataset dataset;

  /**
   * Constructor
   */
  public MRCompactionTask(TaskContext taskContext) throws IOException {
    super(taskContext);
    // TODO: should use reflection to get real suite
    this.suite = new CompactionAvroSuite(taskContext.getTaskState());
    this.dataset = this.suite.load(taskContext.getTaskState());
  }

  /**
   * Below three steps are performed for a compaction task:
   * Do verifications before a map-reduce job is launched.
   * Start a map-reduce job and wait until it is finished
   * Do post-actions after map-reduce job is finished
   */
  @Override
  public void run() {
    List<CompactionVerifier> verifiers = this.suite.getMapReduceVerifiers();
    for (CompactionVerifier verifier : verifiers) {
      verifier.verify(dataset);
    }

    super.run();

    List<CompactionCompleteAction> actions = this.suite.getCompactionCompleteActions();
    for (CompactionCompleteAction action: actions) {
      action.onCompactionJobCompelete(dataset);
    }
  }

  /**
   * Create a map-reduce job
   * The real job configuration is delegated to {@link CompactionSuite#createJob(Dataset)}
   *
   * @return a map-reduce job
   */
  protected Job createJob() throws IOException {
    return this.suite.createJob(dataset);
  }
}
