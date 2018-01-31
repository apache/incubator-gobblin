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

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.gobblin.data.management.version.DatasetVersion;
import org.apache.gobblin.data.management.version.FileSystemDatasetVersion;
import org.apache.hadoop.fs.Path;


/*
 * Select dataset versions that pass the hidden path filter i.e. accept paths that do not have sub-dirs whose names start with "." or "_".
 */
public class HiddenFilterSelectionPolicy implements VersionSelectionPolicy<FileSystemDatasetVersion> {
  private static final String[] HIDDEN_FILE_PREFIX = {"_", "."};

  @Override
  public Class<? extends DatasetVersion> versionClass() {
    return DatasetVersion.class;
  }

  boolean isPathHidden(Path path) {
    while (path != null) {
      String name = path.getName();
      for (String prefix : HIDDEN_FILE_PREFIX) {
        if (name.startsWith(prefix)) {
          return true;
        }
      }
      path = path.getParent();
    }
    return false;
  }

  private Predicate<FileSystemDatasetVersion> getSelectionPredicate() {
    return new Predicate<FileSystemDatasetVersion>() {
      @Override
      public boolean apply(FileSystemDatasetVersion version) {
        Set<Path> paths = version.getPaths();
        for (Path path : paths) {
          Path p = path.getPathWithoutSchemeAndAuthority(path);
          if (isPathHidden(p)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  @Override
  public Collection<FileSystemDatasetVersion> listSelectedVersions(List<FileSystemDatasetVersion> allVersions) {
    return Lists.newArrayList(Collections2.filter(allVersions, getSelectionPredicate()));
  }
}
