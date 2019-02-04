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

package org.apache.gobblin.service.monitoring;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import lombok.extern.slf4j.Slf4j;

import org.apache.gobblin.configuration.ConfigurationKeys;
import org.apache.gobblin.kafka.schemareg.KafkaSchemaRegistryConfigurationKeys;
import org.apache.gobblin.metrics.kafka.KafkaAvroSchemaRegistry;
import org.apache.gobblin.util.ConfigUtils;
import org.apache.gobblin.util.reflection.GobblinConstructorUtils;


/**
 * A factory implementation that returns a {@link KafkaJobStatusMonitor} instance.
 */
@Slf4j
public class KafkaJobStatusMonitorFactory {
  private static final String METRICS_REPORTING_KAFKA_SSL_CONFIG_PREFIX = "metrics.reporting.kafka.config";

  public KafkaJobStatusMonitor createJobStatusMonitor(Config config)
      throws ReflectiveOperationException {
    Config jobStatusConfig = config.getConfig(KafkaJobStatusMonitor.JOB_STATUS_MONITOR_PREFIX);

    String topic = jobStatusConfig.getString(KafkaJobStatusMonitor.JOB_STATUS_MONITOR_TOPIC_KEY);
    int numThreads = ConfigUtils.getInt(jobStatusConfig, KafkaJobStatusMonitor.JOB_STATUS_MONITOR_NUM_THREADS_KEY, 5);
    Class jobStatusMonitorClass = Class.forName(ConfigUtils.getString(jobStatusConfig, KafkaJobStatusMonitor.JOB_STATUS_MONITOR_CLASS_KEY,
        KafkaJobStatusMonitor.DEFAULT_JOB_STATUS_MONITOR_CLASS));

    Config kafkaSslConfig = config.getConfig(METRICS_REPORTING_KAFKA_SSL_CONFIG_PREFIX);

    boolean useSchemaRegistry = ConfigUtils.getBoolean(config, ConfigurationKeys.METRICS_REPORTING_KAFKA_USE_SCHEMA_REGISTRY,
        false);
    Config schemaRegistryConfig = ConfigFactory.empty().withValue(ConfigurationKeys.METRICS_REPORTING_KAFKA_USE_SCHEMA_REGISTRY,
        ConfigValueFactory.fromAnyRef(useSchemaRegistry));
    if (useSchemaRegistry) {
      //Use KafkaAvroSchemaRegistry
      schemaRegistryConfig = schemaRegistryConfig
          .withValue(KafkaAvroSchemaRegistry.KAFKA_SCHEMA_REGISTRY_URL, config.getValue(KafkaAvroSchemaRegistry.KAFKA_SCHEMA_REGISTRY_URL));
      schemaRegistryConfig = schemaRegistryConfig.withValue(KafkaSchemaRegistryConfigurationKeys.KAFKA_SCHEMA_REGISTRY_OVERRIDE_NAMESPACE,
          config.getValue(KafkaSchemaRegistryConfigurationKeys.KAFKA_SCHEMA_REGISTRY_OVERRIDE_NAMESPACE));
    }
    jobStatusConfig = jobStatusConfig.withFallback(kafkaSslConfig).withFallback(schemaRegistryConfig);
    return (KafkaJobStatusMonitor) GobblinConstructorUtils.invokeLongestConstructor(jobStatusMonitorClass, topic, jobStatusConfig, numThreads);
  }
}
