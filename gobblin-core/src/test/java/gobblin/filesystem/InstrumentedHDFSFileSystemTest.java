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

package gobblin.filesystem;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import lombok.Getter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;

import org.testng.Assert;
import org.testng.annotations.Test;


public class InstrumentedHDFSFileSystemTest {

  private final String originalURI = "hdfs://localhost:9000";
  private final String instrumentedURI = "instrumented-hdfs://localhost:9000";

  public class HDFSRoot {
    @Getter
    private FileSystem fs;
    @Getter
    private Path rootPath;
    @Getter
    private Path filePath1, filePath2, filePath3, filePath4, filePath5, filePath6, filePath7, filePath8;
    @Getter
    private Path dirPath1, dirPath2, dirPath3, dirPath4, dirPath5, dirPath6;

    // /tmp -> root -> file1
    //              -> file2
    //              -> dir1
    //              -> dir2.ext -> file3
    //                          -> file4
    //              -> dir3     -> file5.ext
    //                          -> file6.ext
    //                          -> dir4      -> dir5
    //                                       -> dir6 -> file7.ext
    //              -> file8.ext

    public HDFSRoot(String root) throws IOException, URISyntaxException {

      this.fs = FileSystem.get(new URI(originalURI), new Configuration());
      this.rootPath = new Path(root);
      fs.delete(rootPath, true);
      fs.mkdirs(rootPath);

      // Test absolute paths
      String file1 = "file1";
      filePath1 = new Path(root, file1);
      fs.createNewFile(filePath1);

      String file2 = "file2";
      filePath2 = new Path(root, file2);
      fs.createNewFile(filePath2);

      String dir1 = "dir1";
      dirPath1 = new Path(root, dir1);
      fs.mkdirs(dirPath1);

      String dir2 = "dir2";
      dirPath2 = new Path(root, dir2 + ".ext");
      fs.mkdirs(dirPath2);

      String dir3 = "dir3";
      dirPath3 = new Path(root, dir3);
      fs.mkdirs(dirPath3);

      String file3 = "file3";
      filePath3 = new Path(dirPath2, file3);
      fs.createNewFile(filePath3);

      String file4 = "file4";
      filePath4 = new Path(dirPath2, file4);
      fs.createNewFile(filePath4);

      String file5 = "file5";
      filePath5 = new Path(dirPath3, file5 + ".ext");
      fs.createNewFile(filePath5);

      String file6 = "file6";
      filePath6 = new Path(dirPath3, file6 + ".ext");
      fs.createNewFile(filePath6);

      String dir4 = "dir4";
      dirPath4 = new Path(dirPath3, dir4);
      fs.mkdirs(dirPath4);

      String dir5 = "dir5";
      dirPath5 = new Path(dirPath4, dir5);
      fs.mkdirs(dirPath5);

      String dir6 = "dir6";
      dirPath6 = new Path(dirPath4, dir6);
      fs.mkdirs(dirPath6);

      String file7 = "file7";
      filePath7 = new Path(dirPath6, file7 + ".ext");
      fs.createNewFile(filePath7);

      String file8 = "file8";
      filePath8 = new Path(root, file8 + ".ext");
      fs.createNewFile(filePath8);
    }

    public void cleanupRoot() throws IOException {
      fs.delete(rootPath, true);
    }
  }

  /**
   * This test is disabled because it requires a local hdfs cluster at localhost:8020, which requires installation and setup.
   * Changes to {@link InstrumentedHDFSFileSystem} should be followed by a manual run of this tests.
   *
   * TODO: figure out how to fully automate this test.
   * @throws Exception
   */
  @Test(enabled = false)
  public void test() throws Exception {

    String uri = "instrumented-hdfs://localhost:9000";

    FileSystem fs = FileSystem.get(new URI(uri), new Configuration());

    String name = UUID.randomUUID().toString();
    fs.mkdirs(new Path("/tmp"));

    // Test absolute paths
    Path absolutePath = new Path("/tmp", name);
    Assert.assertFalse(fs.exists(absolutePath));
    fs.createNewFile(absolutePath);
    Assert.assertTrue(fs.exists(absolutePath));
    Assert.assertEquals(fs.getFileStatus(absolutePath).getLen(), 0);
    fs.delete(absolutePath, false);
    Assert.assertFalse(fs.exists(absolutePath));


    // Test fully qualified paths
    Path fqPath = new Path(uri + "/tmp", name);
    Assert.assertFalse(fs.exists(fqPath));
    fs.createNewFile(fqPath);
    Assert.assertTrue(fs.exists(fqPath));
    Assert.assertEquals(fs.getFileStatus(fqPath).getLen(), 0);
    fs.delete(fqPath, false);
    Assert.assertFalse(fs.exists(fqPath));
  }

  @Test(enabled = false)
  public void testListStatusPath() throws IOException, URISyntaxException  {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/ListStatusPath");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());
    Path rootPath = hdfsRoot.getRootPath();
    FileStatus[] status = fs.listStatus(rootPath);
    Assert.assertEquals(fs.listStatusPathTimer.getCount(), 1);
    Assert.assertEquals(fs.listStatusPathsTimer.getCount(), 0);
    Assert.assertEquals(fs.listStatusPathWithFilterTimer.getCount(), 0);
    Assert.assertEquals(fs.listStatusPathsWithFilterTimer.getCount(), 0);
    Assert.assertEquals(status.length, 6);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testListStatusPathError() throws IOException, URISyntaxException {

    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/ListStatusPathError");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());
    try {
      fs.listStatus(new Path("/tmp/nonexistence"));
    } catch (Exception e) {
      // stop search when a non-existed directory was encountered, the visit of non-existed path is sill considered as one visit.
      Assert.assertEquals(fs.listStatusPathTimer.getCount(), 1);
      Assert.assertEquals(fs.listStatusPathsTimer.getCount(), 0);
      Assert.assertEquals(fs.listStatusPathWithFilterTimer.getCount(), 0);
      Assert.assertEquals(fs.listStatusPathsWithFilterTimer.getCount(), 0);
    } finally {
      hdfsRoot.cleanupRoot();
    }
  }

  @Test(enabled = false)
  public void testListStatusPaths() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/ListStatusPaths");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    Path[] paths = {hdfsRoot.filePath2, hdfsRoot.dirPath2};
    FileStatus[] status = fs.listStatus(paths);

    Assert.assertEquals(fs.listStatusPathTimer.getCount(), 2);
    Assert.assertEquals(fs.listStatusPathsTimer.getCount(), 1);
    Assert.assertEquals(fs.listStatusPathsWithFilterTimer.getCount(), 1);
    Assert.assertEquals(fs.listStatusPathWithFilterTimer.getCount(), 0);
    Assert.assertEquals(status.length, 3);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testListStatusPathsError() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/ListStatusPathsError");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    try {
      Path[] paths = {hdfsRoot.dirPath2, new Path("/tmp/nonexistence"), hdfsRoot.filePath2};
      fs.listStatus(paths);
    } catch (Exception e) {
      // stop search when a non-existed directory was encountered, the visit of non-existed path is sill considered as one visit.
      Assert.assertEquals(fs.listStatusPathTimer.getCount(), 2);
      Assert.assertEquals(fs.listStatusPathsTimer.getCount(), 1);
      Assert.assertEquals(fs.listStatusPathsWithFilterTimer.getCount(), 1);
      Assert.assertEquals(fs.listStatusPathWithFilterTimer.getCount(), 0);
    } finally {
      hdfsRoot.cleanupRoot();
    }
  }

  @Test(enabled = false)
  public void testListStatusPathWithFilter() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/ListStatusPathWithFilter");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());
    FileStatus[] status = fs.listStatus(hdfsRoot.getDirPath3(), new PathFilter() {
      @Override
      public boolean accept(Path path) {
        return path.toString().endsWith(".ext");
      }
    });
    Assert.assertEquals(fs.listStatusPathTimer.getCount(), 1);
    Assert.assertEquals(fs.listStatusPathsTimer.getCount(), 0);
    Assert.assertEquals(fs.listStatusPathWithFilterTimer.getCount(), 1);
    Assert.assertEquals(fs.listStatusPathsWithFilterTimer.getCount(), 0);
    Assert.assertEquals(status.length, 2);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testListStatusPathsWithFilter() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/ListStatusPathsWithFilter");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    Path[] paths = {hdfsRoot.filePath2, hdfsRoot.dirPath2, hdfsRoot.dirPath3};
    FileStatus[] status = fs.listStatus(paths, new PathFilter() {
      @Override
      public boolean accept(Path path) {
        return path.toString().endsWith(".ext");
      }
    });

    Assert.assertEquals(fs.listStatusPathTimer.getCount(), 3);
    Assert.assertEquals(fs.listStatusPathsTimer.getCount(), 0);
    Assert.assertEquals(fs.listStatusPathsWithFilterTimer.getCount(), 1);
    Assert.assertEquals(fs.listStatusPathWithFilterTimer.getCount(), 0);
    Assert.assertEquals(status.length, 2);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testListFiles() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/ListFiles");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    fs.listFiles(hdfsRoot.getRootPath(), true);
    Assert.assertEquals(fs.listFilesTimer.getCount(), 1);
    Assert.assertEquals(fs.listStatusPathTimer.getCount(), 0);
    Assert.assertEquals(fs.listStatusPathsTimer.getCount(), 0);
    Assert.assertEquals(fs.listStatusPathsWithFilterTimer.getCount(), 0);
    Assert.assertEquals(fs.listStatusPathWithFilterTimer.getCount(), 0);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testGlobStatus() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/GlobStatus");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    FileStatus[] status = fs.globStatus(new Path("/tmp/GlobStatus/*/*.ext"));
    Assert.assertEquals(fs.globStatusTimer.getCount(), 1);
    Assert.assertEquals(status.length, 2);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testGlobStatusWithFilter() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/GlobStatusWithFilter");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    FileStatus[] status = fs.globStatus(new Path("/tmp/GlobStatusWithFilter/*/*"), new PathFilter() {
      @Override
      public boolean accept(Path path) {
        return path.toString().endsWith(".ext");
      }
    });
    Assert.assertEquals(fs.globStatusTimer.getCount(), 1);
    Assert.assertEquals(status.length, 2);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testMakeDirWithPermission() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/mkdirWithPermission");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    Path newDir = new Path (hdfsRoot.getRootPath(), new Path("X"));
    fs.mkdir(newDir, new FsPermission(FsAction.ALL, FsAction.ALL, FsAction.READ));
    Assert.assertEquals(fs.mkdirTimer.getCount(), 1);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testMakeDirs() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/mkdirs");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    Path newDir = new Path (hdfsRoot.getRootPath(), new Path("X/Y/Z"));
    fs.mkdirs(newDir);
    Assert.assertEquals(fs.mkdirTimer.getCount(), 1);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testMakeDirsWithPermission() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/mkdirsWithPermission");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    Path newDir = new Path (hdfsRoot.getRootPath(), new Path("X/Y/Z"));
    fs.mkdirs(newDir, new FsPermission(FsAction.ALL, FsAction.ALL, FsAction.READ));
    Assert.assertEquals(fs.mkdirTimer.getCount(), 1);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testDelete() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/delete");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());

    fs.delete(hdfsRoot.getDirPath3(), true);
    Assert.assertEquals(fs.deleteTimer.getCount(), 1);
    hdfsRoot.cleanupRoot();
  }

  @Test(enabled = false)
  public void testRename() throws IOException, URISyntaxException {
    HDFSRoot hdfsRoot = new HDFSRoot("/tmp/rename");
    InstrumentedHDFSFileSystem fs = (InstrumentedHDFSFileSystem) FileSystem.get(new URI(instrumentedURI), new Configuration());
    Path newDir = new Path("/tmp/rename/AfterRename");
    fs.rename(hdfsRoot.getDirPath3(), newDir);
    Assert.assertEquals(fs.renameTimer.getCount(), 1);

    Assert.assertFalse(fs.exists(hdfsRoot.getDirPath3()));
    Assert.assertTrue(fs.exists(newDir));
    hdfsRoot.cleanupRoot();
  }
}
