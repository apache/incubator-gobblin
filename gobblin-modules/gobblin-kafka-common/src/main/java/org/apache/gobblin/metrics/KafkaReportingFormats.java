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

package org.apache.gobblin.metrics;

import java.util.Properties;

import org.apache.gobblin.configuration.ConfigurationKeys;
import org.apache.gobblin.metrics.kafka.KafkaAvroEventKeyValueReporter;
import org.apache.gobblin.metrics.kafka.KafkaAvroEventReporter;
import org.apache.gobblin.metrics.kafka.KafkaAvroReporter;
import org.apache.gobblin.metrics.kafka.KafkaAvroSchemaRegistry;
import org.apache.gobblin.metrics.kafka.KafkaEventReporter;
import org.apache.gobblin.metrics.kafka.KafkaReporter;
import org.apache.gobblin.metrics.kafka.PusherUtils;


/**
 * Kafka reporting formats enumeration.
 */
public enum KafkaReportingFormats {

  AVRO,
  AVRO_KEY_VALUE,
  JSON;

  /**
   * Get a {@link org.apache.gobblin.metrics.kafka.KafkaReporter.Builder} for this reporting format.
   *
   * @param properties {@link Properties} containing information to build reporters.
   * @return {@link org.apache.gobblin.metrics.kafka.KafkaReporter.Builder}.
   */
  public KafkaReporter.Builder<?> metricReporterBuilder(Properties properties) {
    switch (this) {
      case AVRO_KEY_VALUE:
      case AVRO:
        KafkaAvroReporter.Builder<?> builder = KafkaAvroReporter.BuilderFactory.newBuilder();
        if (Boolean.valueOf(properties.getProperty(ConfigurationKeys.METRICS_REPORTING_KAFKA_USE_SCHEMA_REGISTRY,
            ConfigurationKeys.DEFAULT_METRICS_REPORTING_KAFKA_USE_SCHEMA_REGISTRY))) {
          builder.withSchemaRegistry(new KafkaAvroSchemaRegistry(properties));
        }
        return builder;
      case JSON:
        return KafkaReporter.BuilderFactory.newBuilder();
      default:
        // This should never happen.
        throw new IllegalArgumentException("KafkaReportingFormat not recognized.");
    }
  }

  /**
   * Get a {@link org.apache.gobblin.metrics.kafka.KafkaEventReporter.Builder} for this reporting format.
   * @param context {@link MetricContext} that should be reported.
   * @param properties {@link Properties} containing information to build reporters.
   * @return {@link org.apache.gobblin.metrics.kafka.KafkaEventReporter.Builder}.
   */
  public KafkaEventReporter.Builder<?> eventReporterBuilder(MetricContext context, Properties properties) {
    switch (this) {
      case AVRO:
        KafkaAvroEventReporter.BuilderImpl kafkaAvroEventReporterBuilder = new KafkaAvroEventReporter.BuilderImpl(context);
        if (Boolean.valueOf(properties.getProperty(ConfigurationKeys.METRICS_REPORTING_KAFKA_USE_SCHEMA_REGISTRY,
            ConfigurationKeys.DEFAULT_METRICS_REPORTING_KAFKA_USE_SCHEMA_REGISTRY))) {
          kafkaAvroEventReporterBuilder.withSchemaRegistry(new KafkaAvroSchemaRegistry(properties));
        }
        return kafkaAvroEventReporterBuilder;

      case AVRO_KEY_VALUE:
        KafkaAvroEventKeyValueReporter.BuilderImpl kafkaAvroEventKeyValueReporter = new KafkaAvroEventKeyValueReporter.BuilderImpl(context, properties);
        if (Boolean.valueOf(properties.getProperty(ConfigurationKeys.METRICS_REPORTING_KAFKA_USE_SCHEMA_REGISTRY,
            ConfigurationKeys.DEFAULT_METRICS_REPORTING_KAFKA_USE_SCHEMA_REGISTRY))) {
          kafkaAvroEventKeyValueReporter.withSchemaRegistry(new KafkaAvroSchemaRegistry(properties));
        }
        return kafkaAvroEventKeyValueReporter;

      case JSON:
        return new KafkaEventReporter.BuilderImpl(context);

      default:
        // This should never happen.
        throw new IllegalArgumentException("KafkaReportingFormat not recognized.");
    }
  }
}
