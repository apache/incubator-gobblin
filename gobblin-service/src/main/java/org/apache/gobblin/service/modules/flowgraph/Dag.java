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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;

import lombok.Getter;

import org.apache.gobblin.annotation.Alpha;


/**
 * An implementation of Dag. Assumes that nodes have unique values. Nodes with duplicate values will produce
 * unpredictable behavior.
 */
@Alpha
@Getter
public class Dag<T> {
  private List<DagNode<T>> startNodes;
  private List<DagNode<T>> endNodes;
  // Map to maintain parent to children mapping.
  private Map<DagNode, List<DagNode<T>>> parentChildMap;
  private List<DagNode<T>> nodes;

  public Dag(List<DagNode<T>> dagNodes) {
    this.nodes = dagNodes;
    //Build dag
    this.build();
  }

  /**
   * Constructs the dag from the Node list.
   */
  private void build() {
    this.startNodes = new ArrayList<>();
    this.endNodes = new ArrayList<>();
    this.parentChildMap = new HashMap<>();
    for (DagNode node : this.nodes) {
      //If a Node has no parent Node, add it to the list of start Nodes
      if (node.getParentNodes() == null) {
        this.startNodes.add(node);
      } else {
        List<DagNode> parentNodeList = node.getParentNodes();
        for (DagNode parentNode : parentNodeList) {
          if (parentChildMap.containsKey(parentNode)) {
            parentChildMap.get(parentNode).add(node);
          } else {
            parentChildMap.put(parentNode, Lists.newArrayList(node));
          }
        }
      }
    }
    //Iterate over all the Nodes and add a Node to the list of endNodes if it is not present in the parentChildMap
    for (DagNode node : this.nodes) {
      if (!parentChildMap.containsKey(node)) {
        this.endNodes.add(node);
      }
    }
  }

  public List<DagNode<T>> getChildren(DagNode node) {
    return parentChildMap.getOrDefault(node, Collections.EMPTY_LIST);
  }

  public List<DagNode<T>> getParents(DagNode node) {
    return (node.parentNodes != null)? node.parentNodes : Collections.EMPTY_LIST;
  }

  public boolean isEmpty() {
    return this.nodes.isEmpty();
  }

  /**
   * Concatenate two dags together. Join the "other" dag to "this" dag and return "this" dag.
   * The concatenate method ensures that all the jobs of "this" dag (which may have multiple end nodes)
   * are completed before starting any job of the "other" dag. This is done by adding each endNode of this dag as
   * a parent of every startNode of the other dag.
   *
   * @param other dag to concatenate to this dag
   * @return the concatenated dag
   */
  public Dag<T> concatenate(Dag<T> other) {
    return concatenate(other, new HashSet<>());
  }

  /**
   * Concatenate two dags together. Join the "other" dag to "this" dag and return "this" dag.
   * The concatenate method ensures that all the jobs of "this" dag (which may have multiple end nodes)
   * are completed before starting any job of the "other" dag. This is done by adding each endNode of this dag, which is
   * not a fork node, as a parent of every startNode of the other dag.
   *
   * @param other dag to concatenate to this dag
   * @param forkNodes a set of nodes from this dag which are marked as forkable nodes. Each of these nodes will be added
   *                  to the list of end nodes of the concatenated dag. Essentially, a forkable node has no dependents
   *                  in the concatenated dag.
   * @return the concatenated dag
   */
  public Dag<T> concatenate(Dag<T> other, Set<DagNode<T>> forkNodes) {
    if (other == null || other.isEmpty()) {
      return this;
    }
    if (this.isEmpty()) {
      return other;
    }

    for (DagNode node : this.endNodes) {
      //Create a dependency for non-forkable nodes
      if (!forkNodes.contains(node)) {
        this.parentChildMap.put(node, Lists.newArrayList());
        for (DagNode otherNode : other.startNodes) {
          this.parentChildMap.get(node).add(otherNode);
          otherNode.addParentNode(node);
        }
      } else {
        for (DagNode otherNode: other.startNodes) {
          List<DagNode<T>> parentNodes = this.getParents(node);
          parentNodes.forEach(parentNode -> this.parentChildMap.get(parentNode).add(otherNode));
          parentNodes.forEach(otherNode::addParentNode);
        }
      }
    }
    //Each node which is a forkable node is added to list of end nodes of the concatenated dag
    other.endNodes.addAll(forkNodes);
    this.endNodes = other.endNodes;

    //Append all the entries from the other dag's parentChildMap to this dag's parentChildMap
    for (Iterator<Map.Entry<DagNode, List<DagNode<T>>>> iterator = other.parentChildMap.entrySet().iterator();
        iterator.hasNext(); ) {
      Map.Entry<DagNode, List<DagNode<T>>> entry = iterator.next();
      this.parentChildMap.put(entry.getKey(), entry.getValue());
    }

    //If there exists a node in the other dag with no parent nodes, add it to the list of start nodes of the
    // concatenated dag.
    other.startNodes.stream().filter(node -> other.getParents(node).isEmpty())
        .forEach(node -> this.startNodes.add(node));

    this.nodes.addAll(other.nodes);
    return this;
  }

  /**
   * Merge the "other" dag to "this" dag and return "this" dag as a forest of the two dags.
   * More specifically, the merge() operation takes two dags and returns a disjoint union of the two dags.
   *
   * @param other dag to merge to this dag
   * @return the disjoint union of the two dags
   */

  public Dag<T> merge(Dag<T> other) {
    if (other == null || other.isEmpty()) {
      return this;
    }
    if (this.isEmpty()) {
      return other;
    }
    //Append all the entries from the other dag's parentChildMap to this dag's parentChildMap
    for (Map.Entry<DagNode, List<DagNode<T>>> entry: other.parentChildMap.entrySet()) {
      this.parentChildMap.put(entry.getKey(), entry.getValue());
    }
    //Append the startNodes, endNodes and nodes from the other dag to this dag.
    this.startNodes.addAll(other.startNodes);
    this.endNodes.addAll(other.endNodes);
    this.nodes.addAll(other.nodes);
    return this;
  }

  @Getter
  public static class DagNode<T> {
    private T value;
    //List of parent Nodes that are dependencies of this Node.
    private List<DagNode<T>> parentNodes;

    //Constructor
    public DagNode(T value) {
      this.value = value;
    }

    public void addParentNode(DagNode<T> node) {
      if (parentNodes == null) {
        parentNodes = Lists.newArrayList(node);
        return;
      }
      parentNodes.add(node);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DagNode that = (DagNode) o;
      return this.getValue().equals(that.getValue());
    }

    @Override
    public int hashCode() {
      return this.getValue().hashCode();
    }
  }
}
