package com.linkedin.uif.runtime.mapreduce;

import java.io.FileReader;
import java.util.Properties;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.linkedin.uif.configuration.ConfigurationKeys;
import com.linkedin.uif.metastore.FsStateStore;
import com.linkedin.uif.runtime.JobLauncherTestBase;
import com.linkedin.uif.runtime.JobState;

/**
 * Unit test for {@link MRJobLauncher}.
 */
@Test(groups = {"com.linkedin.uif.runtime.mapreduce", "ignore"})
public class MRJobLauncherTest extends JobLauncherTestBase {

    @BeforeClass
    public void startUp() throws Exception {
        this.properties = new Properties();
        this.properties.load(new FileReader("test/resource/uif.mr-test.properties"));
        this.properties.setProperty(ConfigurationKeys.METRICS_ENABLED_KEY, "true");
        this.jobStateStore = new FsStateStore(
                this.properties.getProperty(ConfigurationKeys.STATE_STORE_FS_URI_KEY),
                this.properties.getProperty(ConfigurationKeys.STATE_STORE_ROOT_DIR_KEY),
                JobState.class);
    }

    @Test
    public void testLaunchJob() throws Exception {
        Properties jobProps = new Properties();
        jobProps.load(new FileReader("test/resource/mr-job-conf/GobblinMRTest.pull"));
        jobProps.putAll(this.properties);
        jobProps.setProperty(SOURCE_FILE_LIST_KEY,
                "test/resource/source/test.avro.0," +
                "test/resource/source/test.avro.1," +
                "test/resource/source/test.avro.2," +
                "test/resource/source/test.avro.3");

        runTest(jobProps);
    }

    @Test
    public void testLaunchJobWithConcurrencyLimit() throws Exception {
        Properties jobProps = new Properties();
        jobProps.load(new FileReader("test/resource/mr-job-conf/GobblinMRTest.pull"));
        jobProps.putAll(this.properties);
        jobProps.setProperty(SOURCE_FILE_LIST_KEY,
                "test/resource/source/test.avro.0," +
                "test/resource/source/test.avro.1," +
                "test/resource/source/test.avro.2," +
                "test/resource/source/test.avro.3");

        jobProps.setProperty(ConfigurationKeys.MR_JOB_MAX_MAPPERS_KEY, "2");
        runTest(jobProps);

        jobProps.setProperty(ConfigurationKeys.MR_JOB_MAX_MAPPERS_KEY, "3");
        runTest(jobProps);

        jobProps.setProperty(ConfigurationKeys.MR_JOB_MAX_MAPPERS_KEY, "5");
        runTest(jobProps);
    }

    @Test
    public void testLaunchJobWithPullLimit() throws Exception {
        Properties jobProps = new Properties();
        jobProps.load(new FileReader("test/resource/mr-job-conf/GobblinMRTest.pull"));
        jobProps.putAll(this.properties);
        jobProps.setProperty(ConfigurationKeys.EXTRACT_PULL_LIMIT, "10");
        jobProps.setProperty(SOURCE_FILE_LIST_KEY,
                "test/resource/source/test.avro.0," +
                "test/resource/source/test.avro.1," +
                "test/resource/source/test.avro.2," +
                "test/resource/source/test.avro.3");

        runTestWithPullLimit(jobProps);
    }
}
