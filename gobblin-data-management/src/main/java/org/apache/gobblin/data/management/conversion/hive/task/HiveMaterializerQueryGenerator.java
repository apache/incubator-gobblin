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

package org.apache.gobblin.data.management.conversion.hive.task;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.gobblin.configuration.WorkUnitState;
import org.apache.gobblin.converter.DataConversionException;
import org.apache.gobblin.data.management.conversion.hive.avro.AvroSchemaManager;
import org.apache.gobblin.data.management.conversion.hive.converter.AbstractAvroToOrcConverter;
import org.apache.gobblin.data.management.conversion.hive.dataset.ConvertibleHiveDataset;
import org.apache.gobblin.data.management.conversion.hive.entities.QueryBasedHiveConversionEntity;
import org.apache.gobblin.data.management.conversion.hive.entities.QueryBasedHivePublishEntity;
import org.apache.gobblin.data.management.conversion.hive.entities.SchemaAwareHivePartition;
import org.apache.gobblin.data.management.conversion.hive.entities.SchemaAwareHiveTable;
import org.apache.gobblin.data.management.conversion.hive.events.EventWorkunitUtils;
import org.apache.gobblin.data.management.conversion.hive.query.HiveAvroORCQueryGenerator;
import org.apache.gobblin.data.management.conversion.hive.source.HiveWorkUnit;
import org.apache.gobblin.data.management.copy.hive.HiveDatasetFinder;
import org.apache.gobblin.hive.HiveMetastoreClientPool;
import org.apache.gobblin.util.AutoReturnableObject;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.Table;


@Slf4j

public class HiveMaterializerQueryGenerator implements QueryGenerator {
  private final FileSystem fs;
  private final ConvertibleHiveDataset.ConversionConfig conversionConfig;
  private final ConvertibleHiveDataset hiveDataset;
  private final String inputDbName;
  private final String inputTableName;
  private final String outputDatabaseName;
  private final String outputTableName;
  private final String outputDataLocation;
  private final String outputStagingTableName;
  private final String outputStagingDataLocation;
  private final List<String> sourceDataPathIdentifier;
  private final String stagingDataPartitionDirName;
  private final String stagingDataPartitionLocation;
  private final Map<String, String> partitionsDDLInfo;
  private final Map<String, String> partitionsDMLInfo;
  private final Optional<Table> destinationTableMeta;
  private final HiveWorkUnit workUnit;
  private final HiveMetastoreClientPool pool;
  private final QueryBasedHiveConversionEntity conversionEntity;
  private final WorkUnitState workUnitState;

  public HiveMaterializerQueryGenerator(WorkUnitState workUnitState) throws Exception {
    this.workUnitState = workUnitState;
    this.workUnit = new HiveWorkUnit(workUnitState.getWorkunit());
    this.hiveDataset = (ConvertibleHiveDataset) workUnit.getHiveDataset();
    this.inputDbName = hiveDataset.getDbAndTable().getDb();
    this.inputTableName = hiveDataset.getDbAndTable().getTable();
    this.fs = HiveConverterUtils.getSourceFs(workUnitState);
    this.conversionConfig = hiveDataset.getConversionConfigForFormat("sameAsSource").get();
    this.outputDatabaseName = conversionConfig.getDestinationDbName();
    this.outputTableName = conversionConfig.getDestinationTableName();
    this.outputDataLocation = HiveConverterUtils.getOutputDataLocation(conversionConfig.getDestinationDataPath());
    this.outputStagingTableName = HiveConverterUtils.getStagingTableName(conversionConfig.getDestinationStagingTableName());
    this.outputStagingDataLocation = HiveConverterUtils.getStagingDataLocation(conversionConfig.getDestinationDataPath(), outputStagingTableName);
    this.sourceDataPathIdentifier = conversionConfig.getSourceDataPathIdentifier();
    this.pool = HiveMetastoreClientPool.get(workUnitState.getJobState().getProperties(),
        Optional.fromNullable(workUnitState.getJobState().getProp(HiveDatasetFinder.HIVE_METASTORE_URI_KEY)));
    this.conversionEntity = getConversionEntity();
    this.stagingDataPartitionDirName = HiveConverterUtils.getStagingDataPartitionDirName(conversionEntity, sourceDataPathIdentifier);
    this.stagingDataPartitionLocation = outputStagingDataLocation + Path.SEPARATOR + stagingDataPartitionDirName;
    this.partitionsDDLInfo = Maps.newHashMap();
    this.partitionsDMLInfo = Maps.newHashMap();
    HiveConverterUtils.populatePartitionInfo(conversionEntity, partitionsDDLInfo, partitionsDMLInfo);
    this.destinationTableMeta = HiveConverterUtils.getDestinationTableMeta(outputDatabaseName,
        outputTableName, workUnitState).getLeft();
  }

  @Override
  public List<String> generateQueries() {

    List<String> hiveQueries = Lists.newArrayList();

    Preconditions.checkNotNull(this.workUnit, "Workunit must not be null");
    EventWorkunitUtils.setBeginDDLBuildTimeMetadata(this.workUnit, System.currentTimeMillis());

    HiveConverterUtils.createStagingDirectory(fs, conversionConfig.getDestinationDataPath(),
        conversionEntity, this.workUnitState);

    // Create DDL statement for table
    String createStagingTableDDL =
        HiveConverterUtils.generateCreateDuplicateTableDDL(
            inputDbName,
            inputTableName,
            outputStagingTableName,
            outputStagingDataLocation,
            Optional.of(outputDatabaseName));
    hiveQueries.add(createStagingTableDDL);
    log.debug("Create staging table DDL:\n" + createStagingTableDDL);

    // Create DDL statement for partition
    if (partitionsDMLInfo.size() > 0) {
      List<String> createStagingPartitionDDL =
          HiveAvroORCQueryGenerator.generateCreatePartitionDDL(outputDatabaseName,
              outputStagingTableName,
              stagingDataPartitionLocation,
              partitionsDMLInfo);

      hiveQueries.addAll(createStagingPartitionDDL);
      log.debug("Create staging partition DDL: " + createStagingPartitionDDL);
    }

    hiveQueries.add("SET hive.exec.dynamic.partition.mode=nonstrict");

    String insertInStagingTableDML =
        HiveConverterUtils
            .generateTableCopy(conversionEntity.getHiveTable().getAvroSchema(),
                inputTableName,
                outputStagingTableName,
                Optional.of(conversionEntity.getHiveTable().getDbName()),
                Optional.of(outputDatabaseName),
                Optional.of(partitionsDMLInfo),
                Optional.<Boolean>absent(),
                Optional.<Boolean>absent());
    hiveQueries.add(insertInStagingTableDML);
    log.debug("Conversion staging DML: " + insertInStagingTableDML);

    log.info("Conversion Query {}",  hiveQueries);

    EventWorkunitUtils.setEndDDLBuildTimeMetadata(workUnit, System.currentTimeMillis());
    return hiveQueries;
  }

  public QueryBasedHivePublishEntity generatePublishQueries() throws DataConversionException {

    QueryBasedHivePublishEntity publishEntity = new QueryBasedHivePublishEntity();
    List<String> publishQueries = publishEntity.getPublishQueries();
    Map<String, String> publishDirectories = publishEntity.getPublishDirectories();
    List<String> cleanupQueries = publishEntity.getCleanupQueries();
    List<String> cleanupDirectories = publishEntity.getCleanupDirectories();

    String createFinalTableDDL =
        HiveConverterUtils.generateCreateDuplicateTableDDL(inputDbName, inputTableName, outputTableName,
            outputDataLocation, Optional.of(outputDatabaseName));
    publishQueries.add(createFinalTableDDL);
    log.debug("Create final table DDL:\n" + createFinalTableDDL);

    if (partitionsDDLInfo.size() == 0) {
      HiveConverterUtils.cleanUpNonPartitionedTable(publishDirectories, cleanupQueries, outputStagingDataLocation,
          cleanupDirectories, outputDataLocation, outputDatabaseName, outputStagingTableName);
    } else {
      String finalDataPartitionLocation = outputDataLocation + Path.SEPARATOR + stagingDataPartitionDirName;
      Optional<Path> destPartitionLocation =
            HiveConverterUtils.getDestinationPartitionLocation(destinationTableMeta, this.workUnitState,
                conversionEntity.getHivePartition().get().getName());
        finalDataPartitionLocation = HiveConverterUtils.updatePartitionLocation(finalDataPartitionLocation, this.workUnitState,
            destPartitionLocation);

      log.debug("Partition directory to move: " + stagingDataPartitionLocation + " to: " + finalDataPartitionLocation);
      publishDirectories.put(stagingDataPartitionLocation, finalDataPartitionLocation);
      List<String> dropPartitionsDDL =
          HiveAvroORCQueryGenerator.generateDropPartitionsDDL(outputDatabaseName, outputTableName, partitionsDMLInfo);
      log.debug("Drop partitions if exist in final table: " + dropPartitionsDDL);
      publishQueries.addAll(dropPartitionsDDL);
      List<String> createFinalPartitionDDL =
          HiveAvroORCQueryGenerator.generateCreatePartitionDDL(outputDatabaseName, outputTableName,
              finalDataPartitionLocation, partitionsDMLInfo, Optional.<String>absent());

      log.debug("Create final partition DDL: " + createFinalPartitionDDL);
      publishQueries.addAll(createFinalPartitionDDL);

      String dropStagingTableDDL =
          HiveAvroORCQueryGenerator.generateDropTableDDL(outputDatabaseName, outputStagingTableName);

      log.debug("Drop staging table DDL: " + dropStagingTableDDL);
      cleanupQueries.add(dropStagingTableDDL);

      log.debug("Staging table directory to delete: " + outputStagingDataLocation);
      cleanupDirectories.add(outputStagingDataLocation);
    }

    publishQueries.addAll(HiveAvroORCQueryGenerator.generateDropPartitionsDDL(outputDatabaseName, outputTableName,
        AbstractAvroToOrcConverter.getDropPartitionsDDLInfo(conversionEntity)));

    log.info("Publish partition entity: " + publishEntity);
    return publishEntity;
  }

  private QueryBasedHiveConversionEntity getConversionEntity() throws Exception {


    try (AutoReturnableObject<IMetaStoreClient> client = this.pool.getClient()) {

      Table table = client.get().getTable(this.inputDbName, this.inputTableName);

      SchemaAwareHiveTable schemaAwareHiveTable = new SchemaAwareHiveTable(table, AvroSchemaManager.getSchemaFromUrl(workUnit.getTableSchemaUrl(), fs));

      SchemaAwareHivePartition schemaAwareHivePartition = null;

      if (workUnit.getPartitionName().isPresent() && workUnit.getPartitionSchemaUrl().isPresent()) {
        org.apache.hadoop.hive.metastore.api.Partition
            partition = client.get().getPartition(this.inputDbName, this.inputTableName, workUnit.getPartitionName().get());
        schemaAwareHivePartition =
            new SchemaAwareHivePartition(table, partition, AvroSchemaManager.getSchemaFromUrl(workUnit.getPartitionSchemaUrl().get(), fs));
      }
      return new QueryBasedHiveConversionEntity(this.hiveDataset, schemaAwareHiveTable, Optional.fromNullable(schemaAwareHivePartition));
    }
  }
}