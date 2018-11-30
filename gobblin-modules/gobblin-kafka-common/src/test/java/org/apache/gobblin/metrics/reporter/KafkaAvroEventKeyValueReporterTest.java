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

package org.apache.gobblin.metrics.reporter;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.gobblin.metrics.GobblinTrackingEvent;
import org.apache.gobblin.metrics.MetricContext;
import org.apache.gobblin.metrics.kafka.KafkaAvroEventKeyValueReporter;
import org.apache.gobblin.metrics.kafka.KafkaEventReporter;
import org.apache.gobblin.metrics.kafka.Pusher;
import org.apache.gobblin.metrics.reporter.util.EventUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class KafkaAvroEventKeyValueReporterTest extends KafkaAvroEventReporterTest {
  Properties properties;

  @BeforeClass
  public void setup() {
    properties = new Properties();
    properties.setProperty(KafkaAvroEventKeyValueReporter.keyPropertyName, "k1,k2,k3");
  }

  @Override
  public KafkaEventReporter.Builder<? extends KafkaEventReporter.Builder> getBuilder(MetricContext context,
                                                                                     Pusher pusher) {
    return new KafkaAvroEventKeyValueReporter.BuilderImpl(context, properties).withKafkaPusher(pusher);
  }

  private Pair<String, GobblinTrackingEvent> nextKVEvent(Iterator<Pair<String, byte[]>> it) throws IOException {
    Assert.assertTrue(it.hasNext());
    Pair<String, byte[]> event = it.next();
    return Pair.of(event.getKey(), EventUtils.deserializeReportFromAvroSerialization(new GobblinTrackingEvent(), event.getValue()));
  }

  @Test
  public void testKafkaEventReporter() throws IOException {
    MetricContext context = MetricContext.builder("context").build();

    MockKafkaKeyValuePusher pusher = new MockKafkaKeyValuePusher();
    KafkaEventReporter kafkaReporter = getBuilder(context, pusher).build("localhost:0000", "topic");

    String namespace = "gobblin.metrics.test";
    String eventName = "testEvent";

    GobblinTrackingEvent event = new GobblinTrackingEvent();
    event.setName(eventName);
    event.setNamespace(namespace);
    Map<String, String> metadata = Maps.newHashMap();
    metadata.put("m1", "v1");
    metadata.put("m2", null);
    event.setMetadata(metadata);
    context.submitEvent(event);

    try {
      Thread.sleep(100);
    } catch(InterruptedException ex) {
      Thread.currentThread().interrupt();
    }

    kafkaReporter.report();

    try {
      Thread.sleep(100);
    } catch(InterruptedException ex) {
      Thread.currentThread().interrupt();
    }

    Pair<String, GobblinTrackingEvent> retrievedEvent = nextKVEvent(pusher.messageIterator());
    Assert.assertNull(retrievedEvent.getKey());

    event = new GobblinTrackingEvent();
    event.setName(eventName);
    event.setNamespace(namespace);
    metadata = Maps.newHashMap();
    metadata.put("k1", "v1");
    metadata.put("k2", "v2");
    metadata.put("k3", "v3");
    event.setMetadata(metadata);
    context.submitEvent(event);

    try {
      Thread.sleep(100);
    } catch(InterruptedException ex) {
      Thread.currentThread().interrupt();
    }

    kafkaReporter.report();

    try {
      Thread.sleep(100);
    } catch(InterruptedException ex) {
      Thread.currentThread().interrupt();
    }

    retrievedEvent = nextKVEvent(pusher.messageIterator());
    Assert.assertEquals(retrievedEvent.getKey(), "v1v2v3");
  }
}
