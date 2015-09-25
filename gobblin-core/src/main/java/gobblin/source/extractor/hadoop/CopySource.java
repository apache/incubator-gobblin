/*
 * Copyright (C) 2014-2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.source.extractor.hadoop;

import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.SourceState;
import gobblin.configuration.State;
import gobblin.configuration.WorkUnitState;
import gobblin.data.management.copy.CopyableDataset;
import gobblin.data.management.copy.CopyableFile;
import gobblin.data.management.dataset.Dataset;
import gobblin.data.management.dataset.DatasetUtils;
import gobblin.data.management.partition.Partition;
import gobblin.data.management.partition.PartitionableDataset;
import gobblin.data.management.retention.dataset.finder.DatasetFinder;
import gobblin.source.extractor.Extractor;
import gobblin.source.extractor.extract.AbstractSource;
import gobblin.source.workunit.Extract;
import gobblin.source.workunit.WorkUnit;
import gobblin.util.ForkOperatorUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;


/**
 * {@link gobblin.source.Source} that generates work units from {@link gobblin.data.management.copy.CopyableDataset}s.
 * Used for doing byte-level copies of files.
 */
public class CopySource extends AbstractSource<String, byte[]> {

  private static final Logger LOG = LoggerFactory.getLogger(CopySource.class);

  private static final String CONFIG_PREFIX = "gobblin.copy.source.";
  static final String SERIALIZED_COPYABLE_FILE = CONFIG_PREFIX + "serialized.copyable.file";
  static final String COPYABLE_FILE_PATH = CONFIG_PREFIX + "copyable.file.path";

  /**
   * Parses origin path for a work unit representing a {@link gobblin.data.management.copy.CopyableFile}.
   * @param state work unit state.
   * @return path of the file that should be copied.
   */
  public static Path getOriginPath(WorkUnitState state) {
    return new Path(state.getProp(COPYABLE_FILE_PATH));
  }

  /**
   * Parses a {@link gobblin.data.management.copy.CopyableFile} from a work unit containing a serialized
   * {@link gobblin.data.management.copy.CopyableFile}.
   * @param state work unit state.
   * @return Deserialized {@link gobblin.data.management.copy.CopyableFile}.
   * @throws IOException
   */
  public static CopyableFile getCopyableFile(WorkUnitState state) throws IOException {
    return deserializeCopyableFile(state.getProp(SERIALIZED_COPYABLE_FILE));
  }

  /**
   * Does the following:
   *  1. Instantiate a {@link DatasetFinder}.
   *  2. Find all {@link Dataset} using {@link DatasetFinder}.
   *  3. For each {@link CopyableDataset} get all {@link CopyableFile}s.
   *  4. Create a {@link WorkUnit} per {@link CopyableFile}.
   * @param state see {@link gobblin.configuration.SourceState}
   * @return Work units for copying files.
   */
  @Override public List<WorkUnit> getWorkunits(SourceState state) {

    List<WorkUnit> workUnits = Lists.newArrayList();

    try {
      FileSystem originFs = getSourceFileSystem(state);
      FileSystem targetFs = getTargetFileSystem(state);

      DatasetFinder datasetFinder = DatasetUtils.instantiateDatasetFinder(state.getProperties(), originFs);
      Collection<Dataset> datasets = datasetFinder.findDatasets();

      for (Dataset dataset : datasets) {

        Collection<Partition<CopyableFile>> partitions;
        if(dataset instanceof CopyableDataset && dataset instanceof PartitionableDataset &&
            CopyableFile.class.isAssignableFrom(((PartitionableDataset) dataset).fileClass())) {
          partitions = Lists.<Partition<CopyableFile>>newArrayList(
              ((PartitionableDataset) dataset).partitionFiles(((CopyableDataset) dataset).getCopyableFiles(targetFs)));
        } else if(dataset instanceof CopyableDataset) {
          partitions = Lists.newArrayList(
              new Partition.Builder<CopyableFile>(dataset.datasetRoot().toString()).
                  add(((CopyableDataset) dataset).getCopyableFiles(originFs)).build());
        } else {
          partitions = Lists.newArrayList();
        }

        for(Partition<CopyableFile> partition : partitions) {
          Extract extract = new Extract(Extract.TableType.SNAPSHOT_ONLY, "gobblin.copy", partition.getName());
          for(CopyableFile copyableFile : partition.getFiles()) {
            WorkUnit workUnit = new WorkUnit(extract);
            workUnit.addAll(state);

            workUnit.setProp(COPYABLE_FILE_PATH, copyableFile.getOrigin().getPath().toString());
            workUnit.setProp(ForkOperatorUtils.getPropertyNameForBranch(ConfigurationKeys.WRITER_FILE_NAME, 0, 0),
                copyableFile.getDestination().getName());
            workUnit.setProp(ForkOperatorUtils
                    .getPropertyNameForBranch(ConfigurationKeys.DATA_PUBLISHER_FINAL_DIR, 0, 0),
                copyableFile.getDestination().getParent().toString());
            try {
              workUnit.setProp(SERIALIZED_COPYABLE_FILE, serializeCopyableFile(copyableFile));
            } catch (IOException ioe) {
              // Failed to serialize copyable file
            }

            workUnits.add(workUnit);
          }
        }

      }
    } catch (IOException ioe) {
      LOG.error("Failed to fetch work units.", ioe);
      return Lists.newArrayList();
    }

    return workUnits;
  }

  /**
   * @param state a {@link gobblin.configuration.WorkUnitState} carrying properties needed by the returned {@link Extractor}
   * @return a {@link RawByteExtractor}.
   * @throws IOException
   */
  @Override public Extractor<String, byte[]> getExtractor(WorkUnitState state) throws IOException {
    FileSystem fs = getSourceFileSystem(state);
    return new RawByteExtractor(fs, getOriginPath(state));
  }

  @Override public void shutdown(SourceState state) {

  }

  static String serializeCopyableFile(CopyableFile copyableFile) throws IOException {

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    copyableFile.write(new DataOutputStream(os));
    String toReturn = Hex.encodeHexString(os.toByteArray());
    os.close();

    return toReturn;
  }

  private static CopyableFile deserializeCopyableFile(String string) throws IOException {

    try {
      byte[] actualBytes = Hex.decodeHex(string.toCharArray());
      ByteArrayInputStream is = new ByteArrayInputStream(actualBytes);
      CopyableFile copyableFile = CopyableFile.read(new DataInputStream(is));
      is.close();

      return copyableFile;
    } catch(DecoderException de) {
      throw new IOException(de);
    }

  }

  private FileSystem getSourceFileSystem(State state) throws IOException {
    return getFileSystem(state.getProp(ConfigurationKeys.SOURCE_FILEBASED_FS_URI));
  }

  private FileSystem getTargetFileSystem(State state) throws IOException {
    return getFileSystem(state.getProp(ConfigurationKeys.WRITER_FILE_SYSTEM_URI));
  }

  private FileSystem getFileSystem(String uriString) throws IOException {
    try {
      URI uri = new URI(uriString);
      return FileSystem.get(uri, new Configuration());
    } catch(URISyntaxException use) {
      throw new IOException(use);
    }
  }
}
