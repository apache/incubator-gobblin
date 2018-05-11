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

package org.apache.gobblin.service.modules.flowgraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.gobblin.runtime.spec_executorInstance.BaseServiceNodeImpl;
import org.apache.gobblin.util.ConfigUtils;

import com.google.common.base.Preconditions;
import com.typesafe.config.Config;

import lombok.Getter;
import lombok.Setter;


/**
 * An implementation of {@link DataNode}.
 */
public class BaseDataNode implements DataNode {
  @Getter
  private String id;
  @Getter
  private Set<FlowEdge> flowEdges;
  @Getter
  private Config props;
  @Getter
  @Setter
  private boolean active;

  public BaseDataNode(String id) {
    this(id, new Properties());
  }

  public BaseDataNode(String id, Properties props) {
    this(id, props, true);
  }

  public BaseDataNode(String id, Properties props, boolean active) {
    Preconditions.checkNotNull(id);
    this.id = id;
    this.props = ConfigUtils.propertiesToConfig(props);
    this.active = active;
  }

  public void addFlowEdge(FlowEdge edge) {
    if(this.flowEdges == null) {
      this.flowEdges = new HashSet<>();
    }
    this.flowEdges.add(edge);
  }

  /**
   * The comparison between two nodes should involve the configuration.
   * Node name is the identifier for the node.
   * */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BaseDataNode that = (BaseDataNode) o;

    return id.equals(that.getId());
  }

  @Override
  public int hashCode() {
    return this.id.hashCode();
  }
}
