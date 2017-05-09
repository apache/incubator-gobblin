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

package gobblin.broker;

import java.io.Closeable;
import java.io.IOException;

import org.slf4j.Logger;

import com.google.common.util.concurrent.Service;

import gobblin.broker.iface.SharedResourceFactoryResponse;


/**
 * A {@link SharedResourceFactoryResponse} containing a instance of a resource.
 */
public interface ResourceEntry<T> extends SharedResourceFactoryResponse<T> {
  /**
   * @return The instance of the resource.
   */
  T getResource();

  /**
   * @return Whether this entry is valid. If the entry is invalid, it will be invalidated from the cache, causing a new
   * call to the {@link gobblin.broker.iface.SharedResourceFactory}.
   */
  boolean isValid();

  /**
   * This method will be called when the entry is invalidated. It may or may not close the contained resource depending
   * on the semantics the {@link gobblin.broker.iface.SharedResourceFactory} wishes to provide (e.g. whether already
   * acquired objects should be closed). As much as possible, this method should be non-blocking.
   */
  void onInvalidate();

  static void shutdownObject(Object obj, Logger log) {
    if (obj instanceof Service) {
      ((Service) obj).stopAsync();
    } else if (obj instanceof Closeable) {
      try {
        ((Closeable) obj).close();
      } catch (IOException ioe) {
        log.error("Failed to close {}.", obj);
      }
    }
  }
}
