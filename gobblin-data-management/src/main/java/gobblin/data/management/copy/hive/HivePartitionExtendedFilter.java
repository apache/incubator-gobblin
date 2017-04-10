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

package gobblin.data.management.copy.hive;

import org.apache.hadoop.hive.metastore.api.Partition;


/**
 * The {@link org.apache.hadoop.hive.metastore.IMetaStoreClient} provides `listPartitionsByFilter` method which
 * contains a String-type filter parameter. Given the fact that this filter is limited on simple arithmetic filtering
 * on partition column, the {@link HivePartitionExtendedFilter} interface extends the semantics of partition filters.
 * One example is that you can filter partitions based on the granularity it is partitioned.
 */
public interface HivePartitionExtendedFilter {

  /**
   * Given some filtering metric from partition, return the filter result.
   * @param partition
   * @return If a partition should be accepted or not.
   */
  public boolean accept(Partition partition);
}
