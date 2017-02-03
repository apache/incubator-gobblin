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

import java.io.IOException;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import gobblin.capability.Capability;
import gobblin.capability.CapabilityParser;
import gobblin.capability.CapabilityParsers;
import gobblin.configuration.State;


/**
 * A {@link DataWriterBuilder} for building {@link DataWriter} that writes in Avro format.
 *
 * @author Yinan Li
 */
public class AvroDataWriterBuilder extends FsDataWriterBuilder<Schema, GenericRecord> {

  @Override
  public DataWriter<GenericRecord> build() throws IOException {
    Preconditions.checkNotNull(this.destination);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(this.writerId));
    Preconditions.checkNotNull(this.schema);
    Preconditions.checkArgument(this.format == WriterOutputFormat.AVRO);

    switch (this.destination.getType()) {
      case HDFS:
        return new AvroHdfsDataWriter(this, this.destination.getProperties(), getEncoders());
      default:
        throw new RuntimeException("Unknown destination type: " + this.destination.getType());
    }
  }

  @Override
  public boolean supportsCapability(Capability c, Map<String, Object> properties) {
    if (super.supportsCapability(c, properties)) {
      return true;
    }

    String encryptionType = (String) properties.get(Capability.ENCRYPTION_TYPE);

    // TODO Feels a little weird to hardcode AvroHdfsDataWriter here since we need to manually keep
    // this block in-sync with the switch statement in build() - should refactor to be a little more generic
    return c.equals(Capability.ENCRYPTION) && AvroHdfsDataWriter.SUPPORTED_ENCRYPTION_TYPES.contains(encryptionType);
  }
}
