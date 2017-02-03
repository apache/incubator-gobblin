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

package gobblin.kafka.writer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.avro.generic.GenericRecord;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import kafka.security.auth.Write;
import lombok.extern.slf4j.Slf4j;

import gobblin.capability.Capability;
import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;
import gobblin.kafka.KafkaTestBase;
import gobblin.kafka.schemareg.ConfigDrivenMd5SchemaRegistry;
import gobblin.kafka.schemareg.KafkaSchemaRegistryConfigurationKeys;
import gobblin.kafka.schemareg.SchemaRegistryException;
//import gobblin.kafka.serialize.LiAvroDeserializer;
import gobblin.kafka.serialize.LiAvroDeserializer;
import gobblin.kafka.serialize.LiAvroSerializer;
import gobblin.test.TestUtils;
import gobblin.writer.DataWriter;
import gobblin.writer.Destination;
import gobblin.writer.StreamEncoder;
import gobblin.writer.WriteCallback;
import gobblin.writer.WriteResponse;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@Slf4j
public class Kafka09DataWriterTest {


  private final KafkaTestBase _kafkaTestHelper;
  public Kafka09DataWriterTest()
      throws InterruptedException, RuntimeException {
    _kafkaTestHelper = new KafkaTestBase();
  }

  @BeforeSuite
  public void beforeSuite() {
    log.warn("Process id = " + ManagementFactory.getRuntimeMXBean().getName());

    _kafkaTestHelper.startServers();
  }

  @AfterSuite
  public void afterSuite()
      throws IOException {
    try {
      _kafkaTestHelper.stopClients();
    }
    finally {
      _kafkaTestHelper.stopServers();
    }
  }

  @Test
  public void testStringSerialization()
      throws IOException, InterruptedException, ExecutionException {
    String topic = "testStringSerialization08";
    _kafkaTestHelper.provisionTopic(topic);
    Properties props = new Properties();
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_TOPIC, topic);
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX+"bootstrap.servers", "localhost:" + _kafkaTestHelper.getKafkaServerPort());
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX+"value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    Kafka09DataWriter<String> kafka09DataWriter = new Kafka09DataWriter<String>(props, Collections.<StreamEncoder>emptyList());
    String messageString = "foobar";
    WriteCallback callback = mock(WriteCallback.class);
    Future<WriteResponse> future;

    try {
      future = kafka09DataWriter.write(messageString, callback);
      kafka09DataWriter.flush();
      verify(callback, times(1)).onSuccess(isA(WriteResponse.class));
      verify(callback, never()).onFailure(isA(Exception.class));
      Assert.assertTrue(future.isDone(), "Future should be done");
      System.out.println(future.get().getStringResponse());
      byte[] message = _kafkaTestHelper.getIteratorForTopic(topic).next().message();
      String messageReceived = new String(message);
      Assert.assertEquals(messageReceived, messageString);
    }
    finally
    {
      kafka09DataWriter.close();
    }


  }

  @Test
  public void testBinarySerialization()
      throws IOException, InterruptedException {
    String topic = "testBinarySerialization08";
    _kafkaTestHelper.provisionTopic(topic);
    Properties props = new Properties();
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_TOPIC, topic);
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX+"bootstrap.servers", "localhost:" + _kafkaTestHelper.getKafkaServerPort());
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX+"value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    Kafka09DataWriter<byte[]> kafka09DataWriter = new Kafka09DataWriter<byte[]>(props, Collections.<StreamEncoder>emptyList());
    WriteCallback callback = mock(WriteCallback.class);
    byte[] messageBytes = TestUtils.generateRandomBytes();

    try {
      kafka09DataWriter.write(messageBytes, callback);
    }
    finally
    {
      kafka09DataWriter.close();
    }

    verify(callback, times(1)).onSuccess(isA(WriteResponse.class));
    verify(callback, never()).onFailure(isA(Exception.class));
    byte[] message = _kafkaTestHelper.getIteratorForTopic(topic).next().message();
    Assert.assertEquals(message, messageBytes);
  }

  @Test
  public void testAvroSerialization()
      throws IOException, InterruptedException, SchemaRegistryException {
    String topic = "testAvroSerialization08";
    _kafkaTestHelper.provisionTopic(topic);
    Properties props = new Properties();
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_TOPIC, topic);
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX + "bootstrap.servers",
        "localhost:" + _kafkaTestHelper.getKafkaServerPort());
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX + "value.serializer",
        LiAvroSerializer.class.getName());

    // set up mock schema registry

    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX
        + KafkaSchemaRegistryConfigurationKeys.KAFKA_SCHEMA_REGISTRY_CLASS,
        ConfigDrivenMd5SchemaRegistry.class.getCanonicalName());

    Kafka09DataWriter<GenericRecord> kafka09DataWriter = new Kafka09DataWriter<>(props, Collections.<StreamEncoder>emptyList());
    WriteCallback callback = mock(WriteCallback.class);

    GenericRecord record = TestUtils.generateRandomAvroRecord();
    try {
      kafka09DataWriter.write(record, callback);
    }
    finally
    {
      kafka09DataWriter.close();
    }

    verify(callback, times(1)).onSuccess(isA(WriteResponse.class));
    verify(callback, never()).onFailure(isA(Exception.class));

    byte[] message = _kafkaTestHelper.getIteratorForTopic(topic).next().message();
    ConfigDrivenMd5SchemaRegistry schemaReg = new ConfigDrivenMd5SchemaRegistry(topic, record.getSchema());
    LiAvroDeserializer deser = new LiAvroDeserializer(schemaReg);
    GenericRecord receivedRecord = deser.deserialize(topic, message);
    Assert.assertEquals(record.toString(), receivedRecord.toString());
  }

  @Test
  public void testEncryption() throws IOException {
    String topic = "testAvroSerializationEncryption";
    _kafkaTestHelper.provisionTopic(topic);
    Properties props = new Properties();
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_TOPIC, topic);
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX + "bootstrap.servers",
        "localhost:" + _kafkaTestHelper.getKafkaServerPort());
    props.setProperty(ConfigurationKeys.WRITER_ENABLE_ENCRYPT, "simple");
    props.setProperty(KafkaWriterConfigurationKeys.KAFKA_PRODUCER_CONFIG_PREFIX + KafkaWriterConfigurationKeys.VALUE_SERIALIZER_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");

    KafkaDataWriterBuilder builder = (KafkaDataWriterBuilder)new KafkaDataWriterBuilder()
        .withBranches(1)
        .forBranch(1)
        .writeTo(Destination.of(Destination.DestinationType.KAFKA, new State(props)));
    Assert.assertTrue(builder.supportsCapability(Capability.ENCRYPTION,
        ImmutableMap.<String, Object>of(Capability.ENCRYPTION_TYPE, "simple")),
        "Expected writer builder to support encryption");

    // TODO this cast is ugly, but we need to test that the builder passes down streamencoders properly
    // and KafkaDataWriterBuilder shouldn't really be hardcoding GenericRecord as its type
    // since it lets user pass in arbitrary serializers
    @SuppressWarnings("unchecked")
    DataWriter writer = (DataWriter)builder.build();
    WriteCallback callback = mock(WriteCallback.class);
    final String recordText = "hello";
    try {
      writer.write(recordText);
    } finally {
      writer.close();
    }

    byte[] message = _kafkaTestHelper.getIteratorForTopic(topic).next().message();
    byte[] expectedMessage = new byte[recordText.length()];
    for (int i = 0; i < expectedMessage.length; i++) {
      expectedMessage[i] = (byte)(recordText.charAt(i) + 1);
    }

    Assert.assertEquals(message, expectedMessage, "Expected message to come back encrypted");
  }



}
