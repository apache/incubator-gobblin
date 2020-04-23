package org.apache.gobblin.compaction.mapreduce.orc;

import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.orc.TypeDescription;
import org.apache.orc.mapred.OrcList;
import org.apache.orc.mapred.OrcMap;
import org.apache.orc.mapred.OrcStruct;
import org.apache.orc.mapred.OrcUnion;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


public class OrcUtilsTest {

  @Test
  public void testRandomFillOrcStructWithAnySchema() {
    // 1. Basic case
    TypeDescription schema_1 = TypeDescription.fromString("struct<i:int,j:int,k:int>");
    OrcStruct expectedStruct = (OrcStruct) OrcStruct.createValue(schema_1);
    expectedStruct.setFieldValue("i", new IntWritable(3));
    expectedStruct.setFieldValue("j", new IntWritable(3));
    expectedStruct.setFieldValue("k", new IntWritable(3));

    OrcStruct actualStruct = (OrcStruct) OrcStruct.createValue(schema_1);
    OrcUtils.orcStructFillerWithFixedValue(actualStruct, schema_1, 3, "", false);
    Assert.assertEquals(actualStruct, expectedStruct);

    TypeDescription schema_2 = TypeDescription.fromString("struct<i:boolean,j:int,k:string>");
    expectedStruct = (OrcStruct) OrcStruct.createValue(schema_2);
    expectedStruct.setFieldValue("i", new BooleanWritable(false));
    expectedStruct.setFieldValue("j", new IntWritable(3));
    expectedStruct.setFieldValue("k", new Text(""));
    actualStruct = (OrcStruct) OrcStruct.createValue(schema_2);

    OrcUtils.orcStructFillerWithFixedValue(actualStruct, schema_2, 3, "", false);
    Assert.assertEquals(actualStruct, expectedStruct);

    // 2. Some simple nested cases: struct within struct
    TypeDescription schema_3 = TypeDescription.fromString("struct<i:boolean,j:struct<i:boolean,j:int,k:string>>");
    OrcStruct expectedStruct_nested_1 = (OrcStruct) OrcStruct.createValue(schema_3);
    expectedStruct_nested_1.setFieldValue("i", new BooleanWritable(false));
    expectedStruct_nested_1.setFieldValue("j", expectedStruct);
    actualStruct = (OrcStruct) OrcStruct.createValue(schema_3);

    OrcUtils.orcStructFillerWithFixedValue(actualStruct, schema_3, 3, "", false);
    Assert.assertEquals(actualStruct, expectedStruct_nested_1);

    // 3. array of struct within struct
    TypeDescription schema_4 = TypeDescription.fromString("struct<i:boolean,j:array<struct<i:boolean,j:int,k:string>>>");
    // Note that this will not create any elements in the array.
    expectedStruct_nested_1 = (OrcStruct) OrcStruct.createValue(schema_4);
    expectedStruct_nested_1.setFieldValue("i", new BooleanWritable(false));
    OrcList list = new OrcList(schema_2, 1);
    list.add(expectedStruct);
    expectedStruct_nested_1.setFieldValue("j", list);

    // Constructing actualStruct: make sure the list is non-Empty. There's any meaningful value within placeholder struct.
    actualStruct = (OrcStruct) OrcStruct.createValue(schema_4);
    OrcList placeHolderList = new OrcList(schema_2, 1);
    OrcStruct placeHolderStruct = (OrcStruct) OrcStruct.createValue(schema_2);
    placeHolderList.add(placeHolderStruct);
    actualStruct.setFieldValue("j", placeHolderList);

    OrcUtils.orcStructFillerWithFixedValue(actualStruct, schema_4, 3, "", false);
    Assert.assertEquals(actualStruct, expectedStruct_nested_1);

    // 4. union of struct within struct
    TypeDescription schema_5 = TypeDescription.fromString("struct<i:boolean,j:uniontype<struct<i:boolean,j:int,k:string>>>");
    expectedStruct_nested_1 = (OrcStruct) OrcStruct.createValue(schema_5);
    expectedStruct_nested_1.setFieldValue("i", new BooleanWritable(false));
    OrcUnion union = new OrcUnion(schema_2);
    union.set(0, expectedStruct);
    expectedStruct_nested_1.setFieldValue("j", union);

    // Construct actualStruct: make sure there's a struct-placeholder within the union.
    actualStruct = (OrcStruct) OrcStruct.createValue(schema_5);
    OrcUnion placeHolderUnion = new OrcUnion(schema_2);
    placeHolderUnion.set(0, placeHolderStruct);
    actualStruct.setFieldValue("j", placeHolderUnion);

    OrcUtils.orcStructFillerWithFixedValue(actualStruct, schema_5, 3, "", false);
    Assert.assertEquals(actualStruct, expectedStruct_nested_1);
  }

  @Test
  public void testUpConvertOrcStruct() {
    int intValue = 10;
    String stringValue = "testString";
    boolean boolValue = true;

    // Basic case, all primitives, newly added value will be set to null
    TypeDescription baseStructSchema = TypeDescription.fromString("struct<a:int,b:string>");
    // This would be re-used in the following tests as the actual record using the schema.
    OrcStruct baseStruct = (OrcStruct) OrcStruct.createValue(baseStructSchema);
    // Fill in the baseStruct with specified value.
    OrcUtils.orcStructFillerWithFixedValue(baseStruct, baseStructSchema, intValue, stringValue, boolValue);
    TypeDescription evolved_baseStructSchema = TypeDescription.fromString("struct<a:int,b:string,c:int>");
    OrcStruct evolvedStruct = (OrcStruct) OrcStruct.createValue(evolved_baseStructSchema);
    // This should be equivalent to deserialize(baseStruct).serialize(evolvedStruct, evolvedSchema);
    OrcUtils.upConvertOrcStruct(baseStruct, evolvedStruct, evolved_baseStructSchema);
    // Check if all value in baseStruct is populated and newly created column in evolvedStruct is filled with null.
    Assert.assertEquals(((IntWritable) evolvedStruct.getFieldValue("a")).get(), intValue);
    Assert.assertEquals(((Text) evolvedStruct.getFieldValue("b")).toString(), stringValue);
    Assert.assertNull(evolvedStruct.getFieldValue("c"));

    // Base case: Reverse direction, which is column projection on top-level columns.
    OrcStruct baseStruct_shadow = (OrcStruct) OrcStruct.createValue(baseStructSchema);
    OrcUtils.upConvertOrcStruct(evolvedStruct, baseStruct_shadow, baseStructSchema);
    Assert.assertEquals(baseStruct, baseStruct_shadow);

    // Simple Nested: List/Map/Union within Struct.
    // The element type of list contains a new field.
    // Prepare two ListInStructs with different size ( the list field contains different number of members)
    TypeDescription listInStructSchema = TypeDescription.fromString("struct<a:array<struct<a:int,b:string>>>");
    OrcStruct listInStruct = (OrcStruct) OrcUtils.createValueRecursively(listInStructSchema);
    OrcUtils.orcStructFillerWithFixedValue(listInStruct, listInStructSchema, intValue, stringValue, boolValue);
    TypeDescription evolved_listInStructSchema =
        TypeDescription.fromString("struct<a:array<struct<a:int,b:string,c:int>>>");
    OrcStruct evolved_listInStruct = (OrcStruct) OrcUtils.createValueRecursively(evolved_listInStructSchema);
    // Convert and verify contents.
    OrcUtils.upConvertOrcStruct(listInStruct, evolved_listInStruct, evolved_listInStructSchema);
    Assert.assertEquals(
        ((IntWritable) ((OrcStruct) ((OrcList) evolved_listInStruct.getFieldValue("a")).get(0)).getFieldValue("a"))
            .get(), intValue);
    Assert.assertEquals(
        ((Text) ((OrcStruct) ((OrcList) evolved_listInStruct.getFieldValue("a")).get(0)).getFieldValue("b")).toString(),
        stringValue);
    Assert.assertNull((((OrcStruct) ((OrcList) evolved_listInStruct.getFieldValue("a")).get(0)).getFieldValue("c")));
    // Add cases when original OrcStruct has its list member having different number of elements then the destination OrcStruct.
    // original has list.size() = 2, target has list.size() = 1
    listInStruct = (OrcStruct) OrcUtils.createValueRecursively(listInStructSchema, 2);
    OrcUtils.orcStructFillerWithFixedValue(listInStruct, listInStructSchema, intValue, stringValue, boolValue);
    Assert.assertNotEquals(((OrcList)listInStruct.getFieldValue("a")).size(),
        ((OrcList)evolved_listInStruct.getFieldValue("a")).size());
    OrcUtils.upConvertOrcStruct(listInStruct, evolved_listInStruct, evolved_listInStructSchema);
    Assert.assertEquals(((OrcList) evolved_listInStruct.getFieldValue("a")).size(), 2);
    // Original has lise.size()=0, target has list.size() = 1
    ((OrcList)listInStruct.getFieldValue("a")).clear();
    OrcUtils.upConvertOrcStruct(listInStruct, evolved_listInStruct, evolved_listInStructSchema);
    Assert.assertEquals(((OrcList) evolved_listInStruct.getFieldValue("a")).size(), 0);

    // Map within Struct, contains a type-widening in the map-value type.
    TypeDescription mapInStructSchema = TypeDescription.fromString("struct<a:map<string,int>>");
    OrcStruct mapInStruct = (OrcStruct) OrcStruct.createValue(mapInStructSchema);
    TypeDescription mapSchema = TypeDescription.createMap(TypeDescription.createString(), TypeDescription.createInt());
    OrcMap mapEntry = new OrcMap(mapSchema);
    mapEntry.put(new Text(""), new IntWritable());
    mapInStruct.setFieldValue("a", mapEntry);
    OrcUtils.orcStructFillerWithFixedValue(mapEntry, mapSchema, intValue, stringValue, boolValue);
    // Create the target struct with evolved schema
    TypeDescription evolved_mapInStructSchema = TypeDescription.fromString("struct<a:map<string,bigint>>");
    OrcStruct evolved_mapInStruct = (OrcStruct) OrcStruct.createValue(evolved_mapInStructSchema);
    OrcMap evolvedMapEntry =
        new OrcMap(TypeDescription.createMap(TypeDescription.createString(), TypeDescription.createInt()));
    evolvedMapEntry.put(new Text(""), new LongWritable(2L));
    evolvedMapEntry.put(new Text(""), new LongWritable(3L));
    evolved_mapInStruct.setFieldValue("a", evolvedMapEntry);
    // convert and verify: Type-widening is correct, and size of output file is correct.
    OrcUtils.upConvertOrcStruct(mapInStruct, evolved_mapInStruct, evolved_mapInStructSchema);

    Assert.assertEquals(((OrcMap) evolved_mapInStruct.getFieldValue("a")).get(new Text(stringValue)),
        new LongWritable(intValue));
    Assert.assertEquals(((OrcMap) evolved_mapInStruct.getFieldValue("a")).size(), 1);
    // re-use the same object but the source struct has fewer member in the map entry.
    mapEntry.put(new Text(""), new IntWritable(1));
    // sanity check
    Assert.assertEquals(((OrcMap) mapInStruct.getFieldValue("a")).size(), 2);
    OrcUtils.upConvertOrcStruct(mapInStruct, evolved_mapInStruct, evolved_mapInStructSchema);
    Assert.assertEquals(((OrcMap) evolved_mapInStruct.getFieldValue("a")).size(), 2);
    Assert.assertEquals(((OrcMap) evolved_mapInStruct.getFieldValue("a")).get(new Text(stringValue)),
        new LongWritable(intValue));

    // Union in struct, type widening within the union's member field.
    TypeDescription unionInStructSchema = TypeDescription.fromString("struct<a:uniontype<int,string>>");
    OrcStruct unionInStruct = (OrcStruct) OrcStruct.createValue(unionInStructSchema);
    OrcUnion placeHolderUnion = new OrcUnion(TypeDescription.fromString("uniontype<int,string>"));
    placeHolderUnion.set(0, new IntWritable(1));
    unionInStruct.setFieldValue("a", placeHolderUnion);
    OrcUtils.orcStructFillerWithFixedValue(unionInStruct, unionInStructSchema, intValue, stringValue, boolValue);
    // Create new structWithUnion
    TypeDescription evolved_unionInStructSchema = TypeDescription.fromString("struct<a:uniontype<bigint,string>>");
    OrcStruct evolvedUnionInStruct = (OrcStruct) OrcStruct.createValue(evolved_unionInStructSchema);
    OrcUnion evolvedPlaceHolderUnion = new OrcUnion(TypeDescription.fromString("uniontype<bigint,string>"));
    evolvedPlaceHolderUnion.set(0, new LongWritable(1L));
    evolvedUnionInStruct.setFieldValue("a", evolvedPlaceHolderUnion);
    OrcUtils.upConvertOrcStruct(unionInStruct, evolvedUnionInStruct, evolved_unionInStructSchema);
    // Check in the tag 0(Default from value-filler) within evolvedUnionInStruct, the value is becoming type-widened with correct value.
    Assert.assertEquals(((OrcUnion) evolvedUnionInStruct.getFieldValue("a")).getTag(), 0);
    Assert.assertEquals(((OrcUnion) evolvedUnionInStruct.getFieldValue("a")).getObject(), new LongWritable(intValue));

    // Complex: List<Struct> within struct among others and evolution happens on multiple places, also type-widening in deeply nested level.
    TypeDescription complexOrcSchema =
        TypeDescription.fromString("struct<a:array<struct<a:string,b:int>>,b:struct<a:uniontype<int,string>>>");
    OrcStruct complexOrcStruct = (OrcStruct) OrcUtils.createValueRecursively(complexOrcSchema);
    OrcUtils.orcStructFillerWithFixedValue(complexOrcStruct, complexOrcSchema, intValue, stringValue, boolValue);
    TypeDescription evolvedComplexOrcSchema = TypeDescription
        .fromString("struct<a:array<struct<a:string,b:bigint,c:string>>,b:struct<a:uniontype<bigint,string>,b:int>>");
    OrcStruct evolvedComplexStruct = (OrcStruct) OrcUtils.createValueRecursively(evolvedComplexOrcSchema);
    OrcUtils.orcStructFillerWithFixedValue(evolvedComplexStruct, evolvedComplexOrcSchema, intValue, stringValue, boolValue);
    // Check if new columns are assigned with null value and type widening is working fine.
    OrcUtils.upConvertOrcStruct(complexOrcStruct, evolvedComplexStruct, evolvedComplexOrcSchema);
    Assert
        .assertEquals(((OrcStruct)((OrcList)evolvedComplexStruct.getFieldValue("a")).get(0)).getFieldValue("b"), new LongWritable(intValue));
    Assert.assertNull(((OrcStruct)((OrcList)evolvedComplexStruct.getFieldValue("a")).get(0)).getFieldValue("c"));
    Assert.assertEquals(((OrcUnion) ((OrcStruct)evolvedComplexStruct.getFieldValue("b")).getFieldValue("a")).getObject(), new LongWritable(intValue));
    Assert.assertNull(((OrcStruct)evolvedComplexStruct.getFieldValue("b")).getFieldValue("b"));
  }
}