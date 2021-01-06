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

package org.apache.gobblin.cluster;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import org.apache.hadoop.fs.Path;
import org.apache.helix.HelixManager;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.eventbus.EventBus;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import org.apache.gobblin.configuration.ConfigurationKeys;
import org.apache.gobblin.runtime.api.MutableJobCatalog;
import org.apache.gobblin.runtime.job_catalog.NonObservingFSJobCatalog;
import org.apache.gobblin.scheduler.SchedulerService;

import static org.testng.Assert.*;


public class HelixRetriggeringJobCallableTest {
  public static final String TMP_DIR = "/tmp/" + HelixRetriggeringJobCallable.class.getSimpleName();

  @BeforeClass
  public void setUp() {
    File tmpDir = new File(TMP_DIR);
    if (!tmpDir.exists()) {
      tmpDir.mkdirs();
    }
  }

  @Test
  public void testBuildJobLauncher()
      throws Exception {
    Config config = ConfigFactory.empty().withValue(ConfigurationKeys.JOB_CONFIG_FILE_GENERAL_PATH_KEY,
        ConfigValueFactory.fromAnyRef(TMP_DIR));
    MutableJobCatalog jobCatalog = new NonObservingFSJobCatalog(config);
    SchedulerService schedulerService = new SchedulerService(new Properties());
    //HelixManager helixManager = Mockito.mock(HelixManager.class);
    //EventBus eventBus = Mockito.mock(EventBus.class);
    Path appWorkDir = new Path(TMP_DIR);
    GobblinHelixJobScheduler jobScheduler = new GobblinHelixJobScheduler(ConfigFactory.empty(), null, Optional.empty(), null,
        appWorkDir, Lists.emptyList(), schedulerService, jobCatalog);
    Properties jobProps = new Properties();
    jobProps.setProperty(ConfigurationKeys.JOB_NAME_KEY, "dummyJob");
    GobblinHelixJobLauncher jobLauncher = jobScheduler.buildJobLauncher(jobProps);
    String jobId = jobLauncher.getJobId();
    Assert.assertNotNull(jobId);
    Assert.assertFalse(jobId.contains(GobblinClusterConfigurationKeys.ACTUAL_JOB_NAME_PREFIX));
  }
}