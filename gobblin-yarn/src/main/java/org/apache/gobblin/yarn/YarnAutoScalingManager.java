/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gobblin.yarn;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.helix.HelixManager;
import org.apache.helix.task.JobContext;
import org.apache.helix.task.JobDag;
import org.apache.helix.task.TaskDriver;
import org.apache.helix.task.TaskState;
import org.apache.helix.task.WorkflowConfig;
import org.apache.helix.task.WorkflowContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractIdleService;
import com.typesafe.config.Config;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.gobblin.util.ConfigUtils;
import org.apache.gobblin.util.ExecutorsUtils;


/**
 * The autoscaling manager is responsible for figuring out how many containers are required for the workload and
 * requesting the {@link YarnService} to request that many containers.
 */
@Slf4j
public class YarnAutoScalingManager extends AbstractIdleService {
  private final String AUTO_SCALING_PREFIX = GobblinYarnConfigurationKeys.GOBBLIN_YARN_PREFIX + "autoScaling.";
  private final String AUTO_SCALING_POLLING_INTERVAL_SECS =
      AUTO_SCALING_PREFIX + "pollingIntervalSeconds";
  private final int DEFAULT_AUTO_SCALING_POLLING_INTERVAL_SECS = 60;
  // Only one container will be requested for each N partitions of work
  private final String AUTO_SCALING_PARTITIONS_PER_CONTAINER = AUTO_SCALING_PREFIX + "partitionsPerContainer";
  private final int DEFAULT_AUTO_SCALING_PARTITIONS_PER_CONTAINER = 1;

  private final Config config;
  private final HelixManager helixManager;
  private final ScheduledExecutorService autoScalingExecutor;
  private final YarnService yarnService;
  private final int partitionsPerContainer;

  public YarnAutoScalingManager(GobblinApplicationMaster appMaster) {
    this.config = appMaster.getConfig();
    this.helixManager = appMaster.getMultiManager().getJobClusterHelixManager();
    this.yarnService = appMaster.getYarnService();
    this.partitionsPerContainer = ConfigUtils.getInt(this.config, AUTO_SCALING_PARTITIONS_PER_CONTAINER,
        DEFAULT_AUTO_SCALING_PARTITIONS_PER_CONTAINER);

    Preconditions.checkArgument(this.partitionsPerContainer > 0,
        AUTO_SCALING_PARTITIONS_PER_CONTAINER + " needs to be greater than 0");

    this.autoScalingExecutor = Executors.newSingleThreadScheduledExecutor(
        ExecutorsUtils.newThreadFactory(Optional.of(log), Optional.of("AutoScalingExecutor")));
  }

  @Override
  protected void startUp() throws Exception {
    int scheduleInterval = ConfigUtils.getInt(this.config, AUTO_SCALING_POLLING_INTERVAL_SECS,
        DEFAULT_AUTO_SCALING_POLLING_INTERVAL_SECS);
    log.info("Starting the " + YarnAutoScalingManager.class.getSimpleName());
    log.info("Scheduling the auto scaling task with an interval of {} seconds", scheduleInterval);

    this.autoScalingExecutor.scheduleAtFixedRate(new YarnAutoScalingRunnable(new TaskDriver(this.helixManager),
            this.yarnService, this.partitionsPerContainer), 0,
        scheduleInterval, TimeUnit.SECONDS);
  }

  @Override
  protected void shutDown() throws Exception {
    log.info("Stopping the " + YarnAutoScalingManager.class.getSimpleName());

    ExecutorsUtils.shutdownExecutorService(this.autoScalingExecutor, Optional.of(log));
  }

  /**
   * A {@link Runnable} that figures out the number of containers required for the workload
   * and requests those containers.
   */
  @VisibleForTesting
  @AllArgsConstructor
  static class YarnAutoScalingRunnable implements Runnable {
    private final TaskDriver taskDriver;
    private final YarnService yarnService;
    private final int partitionsPerContainer;

    /**
     * Iterate through the workflows configured in Helix to figure out the number of required partitions
     * and request the {@link YarnService} to scale to the desired number of containers.
     */
    @Override
    public void run() {
      Set<String> inUseInstances = new HashSet<>();

      int numPartitions = 0;
      for (Map.Entry<String, WorkflowConfig> workFlowEntry : taskDriver.getWorkflows().entrySet()) {
        WorkflowContext workflowContext = taskDriver.getWorkflowContext(workFlowEntry.getKey());

        // Only allocate for active workflows
        if (workflowContext == null || !workflowContext.getWorkflowState().equals(TaskState.IN_PROGRESS)) {
          continue;
        }

        log.debug("Workflow name {} config {} context {}", workFlowEntry.getKey(), workFlowEntry.getValue(),
            workflowContext);

        WorkflowConfig workflowConfig = workFlowEntry.getValue();
        JobDag jobDag = workflowConfig.getJobDag();

        Set<String> jobs = jobDag.getAllNodes();

        // sum up the number of partitions
        for (String jobName : jobs) {
          JobContext jobContext = taskDriver.getJobContext(jobName);

          if (jobContext != null) {
            log.debug("JobContext {} num partitions {}", jobContext, jobContext.getPartitionSet().size());

            inUseInstances.addAll(jobContext.getPartitionSet().stream().map(jobContext::getAssignedParticipant)
                .filter(e -> e != null).collect(Collectors.toSet()));

            numPartitions += jobContext.getPartitionSet().size();
          }
        }
      }

      int numTargetContainers = (numPartitions + (this.partitionsPerContainer - 1)) / this.partitionsPerContainer;

      this.yarnService.requestTargetNumberOfContainers(numTargetContainers, inUseInstances);
    }
  }
}
