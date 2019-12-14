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

package org.apache.gobblin.data.management.dataset;

import org.apache.hadoop.fs.Path;

import org.apache.gobblin.dataset.FileSystemDataset;


public class SimpleFileSystemDataset implements FileSystemDataset {

  private final Path path;
  private final boolean isVirtual;

  public SimpleFileSystemDataset(Path path) {
    this(path, false);
  }

  public SimpleFileSystemDataset(Path path, boolean isVirtual) {
    this.path = path;
    this.isVirtual = isVirtual;
  }

  @Override
  public Path datasetRoot() {
    return path;
  }

  @Override
  public String datasetURN() {
    return path.toString();
  }

  public boolean getIsVirtual() {
    return isVirtual;
  }
}