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

package org.apache.gobblin.dataset;

/**
 * Interface representing a dataset.
 */
public interface Dataset extends URNIdentified {

  /**
   * the expected schema for the dataset, used for schema check part
   */
  String expectedSchema = null;
  /**
   * URN for this dataset.
   * @deprecated use {@link #getUrn()}
   */
  @Deprecated
  public String datasetURN();

  @Override
  default String getUrn() {
    return datasetURN();
  }
}
