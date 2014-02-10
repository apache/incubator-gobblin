package com.linkedin.uif.writer;

import java.util.Properties;

import com.linkedin.uif.configuration.ConfigurationKeys;
import com.linkedin.uif.converter.SchemaConverter;
import org.apache.hadoop.conf.Configuration;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.linkedin.uif.writer.schema.SchemaType;

/**
 * Unit tests for {@link DataWriterBuilder}.
 */
@Test(groups = {"com.linkedin.uif.writer"})
public class DataWriterBuilderTest {

    @Test
    public void testBuild() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(ConfigurationKeys.BUFFER_SIZE_KEY,
                ConfigurationKeys.DEFAULT_BUFFER_SIZE);
        properties.setProperty(ConfigurationKeys.FILE_SYSTEM_URI_KEY, TestConstants.TEST_FS_URI);
        properties.setProperty(ConfigurationKeys.STAGING_DIR_KEY, TestConstants.TEST_STAGING_DIR);
        properties.setProperty(ConfigurationKeys.OUTPUT_DIR_KEY, TestConstants.TEST_OUTPUT_DIR);
        properties.setProperty(ConfigurationKeys.FILE_NAME_KEY, TestConstants.TEST_FILE_NAME);

        SchemaConverter<String> schemaConverter = new TestSchemaConverter();

        DataWriter writer = DataWriterBuilder.<String, String>newBuilder()
                .writeTo(Destination.of(Destination.DestinationType.HDFS, properties))
                .writerId("writer-1")
                .useDataConverter(new TestDataConverter(
                        schemaConverter.convert(TestConstants.AVRO_SCHEMA)))
                .useSchemaConverter(new TestSchemaConverter())
                .dataSchema(TestConstants.AVRO_SCHEMA, SchemaType.AVRO)
                .build();

        Assert.assertTrue(writer instanceof HdfsDataWriter);

        writer.close();
    }
}
