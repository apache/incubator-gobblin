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
package gobblin.runtime.api;

import java.util.Properties;


/**
 * A typical edge consists of two types of attributes:
 * - Numerical value based: Return an numerical value for evaluation.
 * - Boolean value based: Return either true or false.
 */
public interface FlowEdge {

  /**
   * @return Identifier of edge which is, by default consisting of source and destination Name with colon in the between.
   */
  String getEdgeIdentity();

  /**
   * @return If an edge is safe to use.
   */
  boolean isEdgeSafe();

  /**
   * Edge safety is supposed to be dynamic.
   */
  void setEdgeSafety(boolean safety);

  /**
   * There could be multiple edge properties besides typical ones like load, security, etc.
   * Edge Properties are supposed to be read-only.
   * @return
   */
  Properties getEdgeProperties();

  /**
   * @return If a edge should be considered as part of flow spec compilation result,
   * based on all boolean-based properties like safety.
   */
  boolean isEdgeValid();

}
