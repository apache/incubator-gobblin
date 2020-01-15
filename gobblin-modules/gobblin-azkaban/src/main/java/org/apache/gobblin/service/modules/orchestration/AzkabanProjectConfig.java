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
package org.apache.gobblin.service.modules.orchestration;

import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import org.apache.commons.lang.StringUtils;
import org.apache.gobblin.runtime.api.JobSpec;
import org.apache.gobblin.util.ConfigUtils;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@Getter
@ToString
@AllArgsConstructor
@Builder(builderMethodName = "hiddenBuilder")
/***
 * Class to hold Azkaban project specific configs
 */
public class AzkabanProjectConfig {
  private final String azkabanServerUrl;
  private final String azkabanProjectName;
  private final String azkabanProjectDescription;
  private final String azkabanProjectFlowName;
  private final String azkabanGroupAdminUsers;
  private final Optional<String> azkabanUserToProxy;
  private final Optional<List<String>> azkabanZipJarNames;
  private final Optional<String> azkabanZipJarUrlTemplate;
  private final Optional<String> azkabanZipJarVersion;
  private final Optional<List<String>> azkabanZipAdditionalFiles;
  private final Boolean failIfJarNotFound;
  private final JobSpec jobSpec;

  public static final String USER_TO_PROXY = "user.to.proxy";

  public AzkabanProjectConfig(JobSpec jobSpec) {
    // Extract config objects
    this.jobSpec = jobSpec;
    Config defaultConfig = ConfigFactory.load(ServiceAzkabanConfigKeys.DEFAULT_AZKABAN_PROJECT_CONFIG_FILE);
    Config config  = jobSpec.getConfig().withFallback(defaultConfig);

    // Azkaban Infrastructure
    this.azkabanServerUrl = config.getString(ServiceAzkabanConfigKeys.AZKABAN_SERVER_URL_KEY);

    // Azkaban Project Metadata
    this.azkabanProjectName = constructProjectName(jobSpec, config);
    this.azkabanProjectDescription = config.getString(ServiceAzkabanConfigKeys.AZKABAN_PROJECT_DESCRIPTION_KEY);
    this.azkabanProjectFlowName = config.getString(ServiceAzkabanConfigKeys.AZKABAN_PROJECT_FLOW_NAME_KEY);
    this.azkabanGroupAdminUsers = ConfigUtils.getString(config, ServiceAzkabanConfigKeys.AZKABAN_PROJECT_GROUP_ADMINS_KEY, "");
    this.azkabanUserToProxy = Optional.ofNullable(ConfigUtils.getString(config, ServiceAzkabanConfigKeys.AZKABAN_PROJECT_USER_TO_PROXY_KEY, null));

    // Azkaban Project Zip
    this.azkabanZipJarNames = Optional.ofNullable(ConfigUtils.getStringList(config, ServiceAzkabanConfigKeys.AZKABAN_PROJECT_ZIP_JAR_NAMES_KEY));
    this.azkabanZipJarUrlTemplate = Optional.ofNullable(ConfigUtils.getString(config, ServiceAzkabanConfigKeys.AZKABAN_PROJECT_ZIP_JAR_URL_TEMPLATE_KEY, null));
    this.azkabanZipJarVersion = Optional.ofNullable(ConfigUtils.getString(config, ServiceAzkabanConfigKeys.AZKABAN_PROJECT_ZIP_JAR_VERSION_KEY, null));
    if (config.hasPath(ServiceAzkabanConfigKeys.AZKABAN_PROJECT_ZIP_ADDITIONAL_FILE_URLS_KEY) &&
        StringUtils.isNotBlank(config.getString(ServiceAzkabanConfigKeys.AZKABAN_PROJECT_ZIP_ADDITIONAL_FILE_URLS_KEY))) {
      this.azkabanZipAdditionalFiles = Optional.ofNullable(
          ConfigUtils.getStringList(config, ServiceAzkabanConfigKeys.AZKABAN_PROJECT_ZIP_ADDITIONAL_FILE_URLS_KEY));
    } else {
      this.azkabanZipAdditionalFiles = Optional.empty();
    }

    this.failIfJarNotFound = ConfigUtils.getBoolean(config, ServiceAzkabanConfigKeys.AZKABAN_PROJECT_ZIP_FAIL_IF_JARNOTFOUND_KEY, false);
  }

  public static String constructProjectName(JobSpec jobSpec, Config config) {
    String projectNamePrefix = ConfigUtils.getString(config, ServiceAzkabanConfigKeys.AZKABAN_PROJECT_NAME_PREFIX_KEY, "");
    String projectNamePostfix = null == jobSpec.getUri() ? "" :
        jobSpec.getUri().toString().replaceAll("_", "-").replaceAll("[^A-Za-z0-9\\-]", "_");

    return trimProjectName(String.format("%s_%s", projectNamePrefix, projectNamePostfix));
  }

  /***
   * Get Azkaban project zip file name
   * @return Azkaban project zip file name
   */
  public String getAzkabanProjectZipFilename() {
    return String.format("%s.zip", azkabanProjectName);
  }

  /***
   * Get Azkaban project working directory, generated by prefixing a temp name
   * @return Azkaban project working directory
   */
  public String getWorkDir() {
    return String.format("%s/%s/%s/%s", System.getProperty("user.dir"), "serviceAzkaban", azkabanProjectName, System.currentTimeMillis());
  }

  private static String trimProjectName(String projectName) {
    // Azkaban does not support name greater than 64 chars, so limit it to 64 chars
    if (projectName.length() > 64) {
      // We are using string.hashcode() so that for same path the generated project name is same (and hence checking
      // .. for path duplicates is deterministic. Using UUID or currentMillis will produce different shortened path
      // .. for the same path every time)
      int pathHash = projectName.hashCode();
      if (pathHash < 0) {
        pathHash *= -1;
      }
      projectName = String.format("%s_%s", projectName.substring(0, 53), pathHash);
    }

    return projectName;
  }
}
