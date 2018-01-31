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

package org.apache.gobblin.data.management.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.apache.gobblin.data.management.version.FileSystemDatasetVersion;
import org.apache.gobblin.data.management.version.TimestampedDatasetVersion;
import org.apache.hadoop.fs.Path;

import org.joda.time.DateTime;

import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


@Slf4j
public class HiddenFilterSelectionPolicyTest {
  @Test
  public void testListSelectedVersions() throws Exception {
    List<FileSystemDatasetVersion> versionList = new ArrayList<>();
    Set<String> pathSet = new HashSet<>();
    Path path1 = new Path("/data/dataset/versions/version1");
    pathSet.add(path1.toString());
    Path path2 = new Path("/data/dataset/versions/version2");
    pathSet.add(path2.toString());
    Path path3 = new Path("/data/dataset/.temp/tmpPath");
    versionList.add(new TimestampedDatasetVersion(new DateTime(), path1));
    versionList.add(new TimestampedDatasetVersion(new DateTime(), path2));
    versionList.add(new TimestampedDatasetVersion(new DateTime(), path3));

    HiddenFilterSelectionPolicy policy = new HiddenFilterSelectionPolicy();
    Collection<FileSystemDatasetVersion> selectedVersions = policy.listSelectedVersions(versionList);
    Assert.assertEquals(selectedVersions.size(), 2);
    for (FileSystemDatasetVersion version : selectedVersions) {
      Set<Path> paths = version.getPaths();
      for (Path path : paths) {
        Assert.assertTrue(pathSet.contains(path.toString()));
      }
    }
  }
}