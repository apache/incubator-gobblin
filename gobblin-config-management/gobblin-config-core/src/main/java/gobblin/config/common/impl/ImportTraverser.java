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

package gobblin.config.common.impl;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.util.concurrent.UncheckedExecutionException;

import lombok.RequiredArgsConstructor;


/**
 * This class computes a traversal of a graph. Starting at the provided node, it uses the {@link #traversalFunction}
 * to generate a DFS traversal of the graph. The traversal is guaranteed to contain each node at most once. If a cycle
 * is detected during traversal, a {@link CircularDependencyException} will be thrown.
 *
 * Note: This class may dead-lock if used concurrently and there are cycles in the traversed graph.
 */
@RequiredArgsConstructor
class ImportTraverser<T> {
  /** The function returning the ordered neighbors for the input node. */
  private final Function<T, List<T>> traversalFunction;
  /** A cache used for storing traversals at various nodes. */
  private final Cache<T, LinkedList<T>> traversalCache;


  /**
   * Traverse the graph starting at the provided node.
   * @param startingNode starting node.
   * @return a List containing the DFS traversal starting at the node.
   * @throws CircularDependencyException if there is a circular dependency in the loaded traversal.
   */
  List<T> traverseGraphRecursively(T startingNode) {
    return doTraverseGraphRecursively(startingNode, new HashSet<>());
  }

  private List<T> doTraverseGraphRecursively(T node, Set<T> nodePath) {
    try {
      return this.traversalCache.get(node, () -> computeRecursiveTraversal(node, nodePath));
    } catch (ExecutionException | UncheckedExecutionException ee) {
      throw unpackExecutionException(ee);
    }
  }

  /**
   * Actually compute the traversal if it is not in the cache.
   */
  private LinkedList<T> computeRecursiveTraversal(T node, Set<T> nodePath) {
    try {

      nodePath.add(node);

      LinkedList<T> imports = new LinkedList<>();
      Set<T> alreadyIncludedImports = new HashSet<>();

      for (T neighbor : this.traversalFunction.apply(node)) {
        addSubtraversal(neighbor, imports, alreadyIncludedImports, nodePath);
      }

      nodePath.remove(node);
      return imports;
    } catch (ExecutionException ee) {
      throw new RuntimeException(ee);
    }
  }

  /**
   * Add a sub-traversal for a neighboring node.
   */
  private void addSubtraversal(T node, LinkedList<T> imports, Set<T> alreadyIncludedImports, Set<T> nodePath)
      throws ExecutionException {

    if (nodePath.contains(node)) {
      throw new CircularDependencyException(node + " is part of a cycle.");
    }

    if (addNodeIfNotAlreadyIncluded(node, imports, alreadyIncludedImports)) {
      nodePath.add(node);
      for (T inheritedFromParent : doTraverseGraphRecursively(node, nodePath)) {
        addNodeIfNotAlreadyIncluded(inheritedFromParent, imports, alreadyIncludedImports);
      }
      nodePath.remove(node);
    }
  }

  /**
   * Only add node to traversal if it is not already included in it.
   */
  private boolean addNodeIfNotAlreadyIncluded(T thisImport,
      LinkedList<T> imports, Set<T> alreadyIncludedImports) {
    if (alreadyIncludedImports.contains(thisImport)) {
      return false;
    }
    imports.add(thisImport);
    alreadyIncludedImports.add(thisImport);
    return true;
  }

  /**
   * Due to recursive nature of algorithm, we may end up with multiple layers of exceptions. Unpack them.
   */
  private RuntimeException unpackExecutionException(Throwable exc) {
    while (exc instanceof ExecutionException || exc instanceof UncheckedExecutionException) {
      exc = exc.getCause();
    }
    return Throwables.propagate(exc);
  }

}
