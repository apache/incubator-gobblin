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

package org.apache.gobblin.compaction.mapreduce.orc;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.gobblin.compaction.mapreduce.RecordKeyMapperBase;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.orc.OrcConf;
import org.apache.orc.TypeDescription;
import org.apache.orc.impl.ConvertTreeReaderFactory;
import org.apache.orc.impl.SchemaEvolution;
import org.apache.orc.mapred.OrcKey;
import org.apache.orc.mapred.OrcList;
import org.apache.orc.mapred.OrcMap;
import org.apache.orc.mapred.OrcStruct;
import org.apache.orc.mapred.OrcUnion;
import org.apache.orc.mapred.OrcValue;
import org.apache.orc.mapreduce.OrcMapreduceRecordReader;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;


/**
 * To keep consistent with {@link OrcMapreduceRecordReader}'s decision on implementing
 * {@link RecordReader} with {@link NullWritable} as the key and generic type of value, the ORC Mapper will
 * read in the record as the input value.
 */
@Slf4j
public class OrcValueMapper extends RecordKeyMapperBase<NullWritable, OrcStruct, Object, OrcValue> {

  private OrcValue outValue;
  private TypeDescription mapperSchema;

  // This is added mostly for debuggability.
  private static int writeCount = 0;

  @Override
  protected void setup(Context context)
      throws IOException, InterruptedException {
    super.setup(context);
    this.outValue = new OrcValue();
    this.mapperSchema =
        TypeDescription.fromString(context.getConfiguration().get(OrcConf.MAPRED_INPUT_SCHEMA.getAttribute()));
  }

  @Override
  protected void map(NullWritable key, OrcStruct orcStruct, Context context)
      throws IOException, InterruptedException {
    OrcStruct upConvertedStruct = upConvertOrcStruct(orcStruct, mapperSchema);
    try {
      if (context.getNumReduceTasks() == 0) {
        this.outValue.value = upConvertedStruct;
        context.write(NullWritable.get(), this.outValue);
      } else {
        this.outValue.value = upConvertedStruct;
        context.write(getDedupKey(upConvertedStruct), this.outValue);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failure in write record no." + writeCount, e);
    }
    writeCount += 1;

    context.getCounter(EVENT_COUNTER.RECORD_COUNT).increment(1);
  }

  /**
   * Up convert the {@link OrcStruct} towards {@link #mapperSchema} recursively.
   * Limitation:
   * 1. Do not support up-convert key type in map.
   * 2. Conversion only happens if that comply with org.apache.gobblin.compaction.mapreduce.orc.OrcValueMapper#isEvolutionValid return true.
   */
  @VisibleForTesting
  OrcStruct upConvertOrcStruct(OrcStruct orcStruct, TypeDescription mapperSchema) {
    // For ORC schema, if schema object differs that means schema itself is different while for Avro,
    // there are chances that documentation or attributes' difference lead to the schema object difference.
    if (!orcStruct.getSchema().equals(mapperSchema)) {
      log.info("There's schema mismatch identified from reader's schema and writer's schema");
      OrcStruct newStruct = new OrcStruct(mapperSchema);

      int indexInNewSchema = 0;
      List<String> oldSchemaFieldNames = orcStruct.getSchema().getFieldNames();
      List<TypeDescription> oldSchemaTypes = orcStruct.getSchema().getChildren();
      List<TypeDescription> newSchemaTypes = mapperSchema.getChildren();

      for (String field : mapperSchema.getFieldNames()) {
        if (oldSchemaFieldNames.contains(field)) {
          int fieldIndex = oldSchemaFieldNames.indexOf(field);

          TypeDescription fileType = oldSchemaTypes.get(fieldIndex);
          TypeDescription readerType = newSchemaTypes.get(indexInNewSchema);

          if (isEvolutionValid(fileType, readerType)) {
            WritableComparable oldField = orcStruct.getFieldValue(field);
            oldField = structConversionHelper(oldField, mapperSchema.getChildren().get(fieldIndex));
            newStruct.setFieldValue(field, oldField);
          } else {
            throw new SchemaEvolution.IllegalEvolutionException(String
                .format("ORC does not support type conversion from file" + " type %s to reader type %s ",
                    fileType.toString(), readerType.toString()));
          }
        } else {
          newStruct.setFieldValue(field, null);
        }

        indexInNewSchema++;
      }

      return newStruct;
    } else {
      return orcStruct;
    }
  }

  /**
   * Suppress the warning of type checking: All casts are clearly valid as they are all (sub)elements Orc types.
   * Check failure will trigger Cast exception and blow up the process.
   */
  @SuppressWarnings("unchecked")
  private WritableComparable structConversionHelper(WritableComparable w, TypeDescription mapperSchema) {
    if (w instanceof OrcStruct) {
      return upConvertOrcStruct((OrcStruct) w, mapperSchema);
    } else if (w instanceof OrcList) {
      OrcList castedList = (OrcList) w;
      TypeDescription elementType = mapperSchema.getChildren().get(0);
      for (int i = 0; i < castedList.size(); i++) {
        castedList.set(i, structConversionHelper((WritableComparable) castedList.get(i), elementType));
      }
    } else if (w instanceof OrcMap) {
      OrcMap castedMap = (OrcMap) w;
      for (Object entry : castedMap.entrySet()) {
        Map.Entry<WritableComparable, WritableComparable> castedEntry =
            (Map.Entry<WritableComparable, WritableComparable>) entry;
        castedMap.put(castedEntry.getKey(),
            structConversionHelper(castedEntry.getValue(), mapperSchema.getChildren().get(1)));
      }
      return castedMap;
    } else if (w instanceof OrcUnion) {
      OrcUnion castedUnion = (OrcUnion) w;
      byte tag = castedUnion.getTag();
      castedUnion.set(tag,
          structConversionHelper((WritableComparable) castedUnion.getObject(), mapperSchema.getChildren().get(tag)));
    }

    // Directly return if primitive object.
    return w;
  }

  /**
   * Determine if two types are following valid evolution.
   * Implementation stolen and manipulated from {@link SchemaEvolution} as that was package-private.
   */
  static boolean isEvolutionValid(TypeDescription fileType, TypeDescription readerType) {
    boolean isOk = true;
    if (fileType.getCategory() == readerType.getCategory()) {
      switch (readerType.getCategory()) {
        case BOOLEAN:
        case BYTE:
        case SHORT:
        case INT:
        case LONG:
        case DOUBLE:
        case FLOAT:
        case STRING:
        case TIMESTAMP:
        case BINARY:
        case DATE:
          // these are always a match
          break;
        case CHAR:
        case VARCHAR:
          break;
        case DECIMAL:
          break;
        case UNION:
        case MAP:
        case LIST: {
          // these must be an exact match
          List<TypeDescription> fileChildren = fileType.getChildren();
          List<TypeDescription> readerChildren = readerType.getChildren();
          if (fileChildren.size() == readerChildren.size()) {
            for (int i = 0; i < fileChildren.size(); ++i) {
              isOk &= isEvolutionValid(fileChildren.get(i), readerChildren.get(i));
            }
            return isOk;
          } else {
            return false;
          }
        }
        case STRUCT: {
          List<TypeDescription> readerChildren = readerType.getChildren();
          List<TypeDescription> fileChildren = fileType.getChildren();

          List<String> readerFieldNames = readerType.getFieldNames();
          List<String> fileFieldNames = fileType.getFieldNames();

          final Map<String, TypeDescription> fileTypesIdx = new HashMap();
          for (int i = 0; i < fileFieldNames.size(); i++) {
            final String fileFieldName = fileFieldNames.get(i);
            fileTypesIdx.put(fileFieldName, fileChildren.get(i));
          }

          for (int i = 0; i < readerFieldNames.size(); i++) {
            final String readerFieldName = readerFieldNames.get(i);
            TypeDescription readerField = readerChildren.get(i);
            TypeDescription fileField = fileTypesIdx.get(readerFieldName);
            if (fileField == null) {
              continue;
            }

            isOk &= isEvolutionValid(fileField, readerField);
          }
          return isOk;
        }
        default:
          throw new IllegalArgumentException("Unknown type " + readerType);
      }
      return isOk;
    } else {
      /*
       * Check for the few cases where will not convert....
       */
      return ConvertTreeReaderFactory.canConvert(fileType, readerType);
    }
  }

  /**
   * By default, dedup key contains the whole ORC record, except MAP since {@link org.apache.orc.mapred.OrcMap} is
   * an implementation of {@link java.util.TreeMap} which doesn't accept difference of records within the map in comparison.
   */
  protected OrcKey getDedupKey(OrcStruct originalRecord) {
    return convertOrcStructToOrcKey(originalRecord);
  }

  /**
   * The output key of mapper needs to be comparable. In the scenarios that we need the orc record itself
   * to be the output key, this conversion will be necessary.
   */
  protected OrcKey convertOrcStructToOrcKey(OrcStruct struct) {
    OrcKey orcKey = new OrcKey();
    orcKey.key = struct;
    return orcKey;
  }
}
