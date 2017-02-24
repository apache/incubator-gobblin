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
package gobblin.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import gobblin.writer.StreamCodec;


/**
 * Implement GZIP compression and decompression.
 */
public class GzipCodec implements StreamCodec {
  @Override
  public OutputStream encodeOutputStream(OutputStream origStream)
      throws IOException {
    return new GZIPOutputStream(origStream);
  }

  @Override
  public InputStream decodeInputStream(InputStream origStream)
      throws IOException {
    return new GZIPInputStream(origStream);
  }

  @Override
  public String getTag() {
    return "gz";
  }
}
