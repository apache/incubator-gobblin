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

package org.apache.gobblin.runtime.job_monitor;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import com.codahale.metrics.Counter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.typesafe.config.Config;

import org.apache.gobblin.metastore.DatasetStateStore;
import org.apache.gobblin.runtime.api.JobSpec;
import org.apache.gobblin.runtime.api.JobSpecMonitor;
import org.apache.gobblin.runtime.api.MutableJobCatalog;
import org.apache.gobblin.runtime.api.SpecExecutor;
import org.apache.gobblin.runtime.kafka.HighLevelConsumer;
import org.apache.gobblin.runtime.metrics.RuntimeMetrics;
import org.apache.gobblin.util.ConfigUtils;

import kafka.message.MessageAndMetadata;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


/**
 * Abstract {@link JobSpecMonitor} that reads {@link JobSpec}s from a Kafka stream. Subclasses should implement
 * {@link KafkaJobMonitor#parseJobSpec(byte[])} to transform the message into one or multiple {@link JobSpec}s.
 */
@Slf4j
public abstract class KafkaJobMonitor extends HighLevelConsumer<byte[], byte[]> implements JobSpecMonitor {

  public static final String KAFKA_JOB_MONITOR_PREFIX = "jobSpecMonitor.kafka";
  public static final String KAFKA_AUTO_OFFSET_RESET_KEY = KAFKA_JOB_MONITOR_PREFIX + ".auto.offset.reset";
  public static final String KAFKA_AUTO_OFFSET_RESET_SMALLEST = "smallest";
  public static final String KAFKA_AUTO_OFFSET_RESET_LARGEST = "largest";
  public static final String DELETE_STATE_STORE_KEY = "delete.state.store";
  private DatasetStateStore datasetStateStore;
  private final MutableJobCatalog jobCatalog;

  @Getter
  private Counter newSpecs;
  @Getter
  private Counter removedSpecs;

  /**
   * @return A collection of either {@link JobSpec}s to add/update or {@link URI}s to remove from the catalog,
   *        parsed from the Kafka message.
   * @throws IOException
   */
  public abstract Collection<JobSpec> parseJobSpec(byte[] message) throws IOException;

  public KafkaJobMonitor(String topic, MutableJobCatalog catalog, Config config) {
    super(topic, ConfigUtils.getConfigOrEmpty(config, KAFKA_JOB_MONITOR_PREFIX), 1);
    this.jobCatalog = catalog;
    try {
      this.datasetStateStore = DatasetStateStore.buildDatasetStateStore(config);
    } catch (Exception e) {
      log.warn("DatasetStateStore could not be created.", e);
    }
  }

  @Override
  protected void createMetrics() {
    super.createMetrics();
    this.newSpecs = this.getMetricContext().counter(RuntimeMetrics.GOBBLIN_JOB_MONITOR_KAFKA_NEW_SPECS);
    this.removedSpecs = this.getMetricContext().counter(RuntimeMetrics.GOBBLIN_JOB_MONITOR_KAFKA_REMOVED_SPECS);
  }

  @VisibleForTesting
  @Override
  protected void buildMetricsContextAndMetrics() {
    super.buildMetricsContextAndMetrics();
  }

  @VisibleForTesting
  @Override
  protected void shutdownMetrics()
      throws IOException {
    super.shutdownMetrics();
  }

  @Override
  protected void processMessage(MessageAndMetadata<byte[], byte[]> message) {
    try {
      Collection<JobSpec> parsedCollection = parseJobSpec(message.message());
      for (JobSpec parsedMessage : parsedCollection) {
        if (parsedMessage.getMetadata().get(JobSpec.VERB_KEY).equalsIgnoreCase(SpecExecutor.Verb.DELETE.name())) {
          this.removedSpecs.inc();
          URI jobSpecUri = parsedMessage.getUri();
          this.jobCatalog.remove(jobSpecUri);

          // Refer FlowConfigsResources:delete to understand the pattern of flow URI
          // FlowToJobSpec Compilers use the flowSpecURI to derive jobSpecURI
          if (parsedMessage.getConfig().hasPath(DELETE_STATE_STORE_KEY) &&
              Boolean.parseBoolean(parsedMessage.getConfig().getString(DELETE_STATE_STORE_KEY))) {
            // Delete the job state if it is a delete spec request
            String[] uriTokens = jobSpecUri.getPath().split("/");
            if (null == this.datasetStateStore) {
              log.warn("Job state store deletion failed as datasetstore is not initialized.");
              continue;
            }
            if (uriTokens.length != 3) {
              log.error("Invalid URI {}.", jobSpecUri);
              continue;
            }
            String jobName = uriTokens[2];
            if (this.datasetStateStore.delete(jobName)) {
              log.info("JobSpec {} deleted with statestore.", jobSpecUri);
            } else {
              log.info("Statestore deletion failed for JobSpec {}.", jobSpecUri);
            }
          } else {
            log.info("JobSpec {} deleted keeping statestore.", jobSpecUri);
          }
        } else {
          this.newSpecs.inc();
          this.jobCatalog.put(parsedMessage);
        }
      }
    } catch (IOException ioe) {
      String messageStr = new String(message.message(), Charsets.UTF_8);
      log.error(String.format("Failed to parse kafka message with offset %d: %s.", message.offset(), messageStr), ioe);
    }
  }

}
