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

package gobblin.writer;

import gobblin.capability.EncryptionCapabilityParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import gobblin.capability.Capability;
import gobblin.capability.CapabilityParser;
import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;
import gobblin.crypto.InsecureShiftCodec;


/**
 * Unit tests for {@link AvroHdfsDataWriter}.
 *
 * @author Yinan Li
 */
@Test(groups = {"gobblin.writer"})
public class AvroHdfsDataWriterTest {

  private static final Type FIELD_ENTRY_TYPE = new TypeToken<Map<String, Object>>() {
  }.getType();

  private Schema schema;
  private DataWriter<GenericRecord> writer;
  private String filePath;

  @BeforeClass
  public void setUp() throws Exception {
    // Making the staging and/or output dirs if necessary
    File stagingDir = new File(TestConstants.TEST_STAGING_DIR);
    File outputDir = new File(TestConstants.TEST_OUTPUT_DIR);
    if (!stagingDir.exists()) {
      stagingDir.mkdirs();
    }
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }

    this.schema = new Schema.Parser().parse(TestConstants.AVRO_SCHEMA);

    this.filePath =
        TestConstants.TEST_EXTRACT_NAMESPACE.replaceAll("\\.", "/") + "/" + TestConstants.TEST_EXTRACT_TABLE + "/"
            + TestConstants.TEST_EXTRACT_ID + "_" + TestConstants.TEST_EXTRACT_PULL_TYPE;

    State properties = defaultProperties();

    // Build a writer to write test records
    this.writer = new AvroDataWriterBuilder().writeTo(Destination.of(Destination.DestinationType.HDFS, properties))
        .writeInFormat(WriterOutputFormat.AVRO)
        .withWriterId(TestConstants.TEST_WRITER_ID)
        .withSchema(this.schema)
        .withBranches(1)
        .forBranch(0)
        .build();
  }

  private State defaultProperties() {
    State properties = new State();
    properties.setProp(ConfigurationKeys.WRITER_BUFFER_SIZE, ConfigurationKeys.DEFAULT_BUFFER_SIZE);
    properties.setProp(ConfigurationKeys.WRITER_FILE_SYSTEM_URI, TestConstants.TEST_FS_URI);
    properties.setProp(ConfigurationKeys.WRITER_STAGING_DIR, TestConstants.TEST_STAGING_DIR);
    properties.setProp(ConfigurationKeys.WRITER_OUTPUT_DIR, TestConstants.TEST_OUTPUT_DIR);
    properties.setProp(ConfigurationKeys.WRITER_FILE_PATH, this.filePath);
    properties.setProp(ConfigurationKeys.WRITER_FILE_NAME, TestConstants.TEST_FILE_NAME);
    return properties;
  }

  @Test
  public void testWrite() throws IOException {
    // Write all test records
    for (String record : TestConstants.JSON_RECORDS) {
      this.writer.write(convertRecord(record));
    }

    Assert.assertEquals(this.writer.recordsWritten(), 3);

    this.writer.close();
    this.writer.commit();

    File outputFile =
        new File(TestConstants.TEST_OUTPUT_DIR + Path.SEPARATOR + this.filePath, TestConstants.TEST_FILE_NAME);
    DataFileReader<GenericRecord> reader =
        new DataFileReader<>(outputFile, new GenericDatumReader<GenericRecord>(this.schema));
    verifyRecords(reader);

    reader.close();
  }

  private void verifyRecords(DataFileStream<GenericRecord> reader) {
    // Read the records back and assert they are identical to the ones written
    GenericRecord user1 = reader.next();
    // Strings are in UTF8, so we have to call toString() here and below
    Assert.assertEquals(user1.get("name").toString(), "Alyssa");
    Assert.assertEquals(user1.get("favorite_number"), 256);
    Assert.assertEquals(user1.get("favorite_color").toString(), "yellow");

    GenericRecord user2 = reader.next();
    Assert.assertEquals(user2.get("name").toString(), "Ben");
    Assert.assertEquals(user2.get("favorite_number"), 7);
    Assert.assertEquals(user2.get("favorite_color").toString(), "red");

    GenericRecord user3 = reader.next();
    Assert.assertEquals(user3.get("name").toString(), "Charlie");
    Assert.assertEquals(user3.get("favorite_number"), 68);
    Assert.assertEquals(user3.get("favorite_color").toString(), "blue");
  }

  @Test
  public void testEncryptionCapabilitySupport() {
    AvroDataWriterBuilder builder = new AvroDataWriterBuilder();
    builder.writeTo(Destination.of(Destination.DestinationType.HDFS, defaultProperties()))
        .writeInFormat(WriterOutputFormat.AVRO)
        .withWriterId(TestConstants.TEST_WRITER_ID)
        .withSchema(this.schema)
        .withBranches(1)
        .forBranch(0);

    CapabilityParser.CapabilityRecord r = encryptionWithType("any");
    Assert.assertTrue(builder.supportsCapability(r.getCapability(), r.getParameters()));

    r = encryptionWithType("insecure_shift");
    Assert.assertTrue(builder.supportsCapability(r.getCapability(), r.getParameters()));

    r = encryptionWithType("doesntexist");
    Assert.assertFalse(builder.supportsCapability(r.getCapability(), r.getParameters()));
  }

  @Test
  public void testEncryptionSupport() throws IOException {
    final String encryptionName = "insecure_shift";
    State properties = defaultProperties();
    properties.setProp(ConfigurationKeys.WRITER_ENABLE_ENCRYPT, encryptionName);

    DataWriter<GenericRecord> writer = new AvroDataWriterBuilder().writeTo(Destination.of(Destination.DestinationType.HDFS, properties))
        .writeInFormat(WriterOutputFormat.AVRO)
        .withWriterId(TestConstants.TEST_WRITER_ID)
        .withSchema(this.schema)
        .withBranches(1)
        .forBranch(0)
        .build();

    for (String record : TestConstants.JSON_RECORDS) {
      writer.write(convertRecord(record));
    }
    writer.close();
    writer.commit();

    Assert.assertEquals(writer.recordsWritten(), 3);

    File outputFile =
        new File(TestConstants.TEST_OUTPUT_DIR + Path.SEPARATOR + this.filePath,
            TestConstants.TEST_FILE_NAME + ".encrypted_" + encryptionName);

    InputStream readerStream = new FileInputStream(outputFile);
    readerStream = new InsecureShiftCodec(Collections.<String, Object>emptyMap()).wrapInputStream(readerStream);
    DataFileStream<GenericRecord> reader = new DataFileStream<GenericRecord>(readerStream,
        new GenericDatumReader<GenericRecord>(this.schema));

    verifyRecords(reader);
  }

  private CapabilityParser.CapabilityRecord encryptionWithType(String type) {
    return new CapabilityParser.CapabilityRecord(Capability.ENCRYPTION, true,
        ImmutableMap.<String, Object>of(EncryptionCapabilityParser.ENCRYPTION_TYPE_PROPERTY, type));
  }

  @AfterClass
  public void tearDown() throws IOException {
    // Clean up the staging and/or output directories if necessary
    File testRootDir = new File(TestConstants.TEST_ROOT_DIR);
    if (testRootDir.exists()) {
      FileUtil.fullyDelete(testRootDir);
    }
  }

  private GenericRecord convertRecord(String inputRecord) {
    Gson gson = new Gson();
    JsonElement element = gson.fromJson(inputRecord, JsonElement.class);
    Map<String, Object> fields = gson.fromJson(element, FIELD_ENTRY_TYPE);
    GenericRecord outputRecord = new GenericData.Record(this.schema);
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      outputRecord.put(entry.getKey(), entry.getValue());
    }

    return outputRecord;
  }
}
