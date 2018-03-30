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

package org.apache.gobblin.metastore;

import java.io.IOException;

import org.apache.gobblin.configuration.State;
import org.apache.gobblin.metastore.metadata.StateStoreEntryManager;


/**
 * A {@link StateStoreEntryManager} generated by {@link MysqlStateStore}.
 */
public class MysqlStateStoreEntryManager<T extends State> extends StateStoreEntryManager<T> {
  private final MysqlStateStore<T> stateStore;

  public MysqlStateStoreEntryManager(String storeName, String tableName, long modificationTime,
      MysqlStateStore<T> stateStore) {
    super(storeName, tableName, modificationTime, stateStore);
    this.stateStore = stateStore;
  }

  @Override
  public T readState() throws IOException {
    return this.stateStore.get(getStoreName(), getTableName(), "");
  }

  @Override
  public void delete() throws IOException {
    this.stateStore.delete(getStoreName(), getTableName());
  }
}
