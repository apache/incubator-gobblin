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

package gobblin.ingestion.google.webmaster;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.webmasters.WebmastersScopes;
import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;

import lombok.extern.slf4j.Slf4j;

import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;
import gobblin.configuration.WorkUnitState;
import gobblin.source.extractor.extract.LongWatermark;
import gobblin.source.extractor.extract.google.GoogleCommon;
import gobblin.source.extractor.extract.google.GoogleCommonKeys;
import gobblin.source.extractor.partition.Partition;

import static gobblin.configuration.ConfigurationKeys.SOURCE_CONN_PRIVATE_KEY;
import static gobblin.configuration.ConfigurationKeys.SOURCE_CONN_USERNAME;
import static gobblin.configuration.ConfigurationKeys.SOURCE_CONN_USE_PROXY_PORT;
import static gobblin.configuration.ConfigurationKeys.SOURCE_CONN_USE_PROXY_URL;


@Slf4j
public class GoogleWebMasterSourceDaily extends GoogleWebMasterSource {

  private final DateTimeFormatter watermarkFormatter = DateTimeFormat.forPattern("yyyyMMddHHmmss");

  @Override
  GoogleWebmasterExtractor createExtractor(WorkUnitState state, Map<String, Integer> columnPositionMap,
      List<GoogleWebmasterFilter.Dimension> requestedDimensions,
      List<GoogleWebmasterDataFetcher.Metric> requestedMetrics, JsonArray schemaJson)
      throws IOException {

    Partition partition = Partition.deserialize(state.getWorkunit());
    long lowWatermark = partition.getLowWatermark();
    long expectedHighWatermark = partition.getHighWatermark();

    /*
      This change is needed because
      1. The partition behavior changed due to commit 7d730fcb0263b8ca820af0366818160d638d1336 [7d730fc]
       by zxcware <zxcware@gmail.com> on April 3, 2017 at 11:47:41 AM PDT
      2. Google Search Console API only cares about Dates, and are both side inclusive.
      Therefore, do the following processing.
     */
    lowWatermark /= 1000000;
    expectedHighWatermark /= 1000000;
    DateTime lowWaterMarkDate = watermarkFormatter.parseDateTime(Long.toString(lowWatermark));
    DateTime highWaterMarkDate = watermarkFormatter.parseDateTime(Long.toString(expectedHighWatermark));
    if (!partition.isHighWatermarkInclusive()) {
      highWaterMarkDate.minusDays(1);
    }
    long updatedLowWatermark = lowWaterMarkDate.getMillis();
    long updatedExpectedHighWatermark = highWaterMarkDate.getMillis();

    GoogleWebmasterClientImpl gscClient =
        new GoogleWebmasterClientImpl(getCredential(state), state.getProp(ConfigurationKeys.SOURCE_ENTITY));
    return new GoogleWebmasterExtractor(gscClient, state, updatedLowWatermark, updatedExpectedHighWatermark,
        columnPositionMap, requestedDimensions, requestedMetrics, schemaJson);
  }

  private static Credential getCredential(State wuState) {
    String scope = wuState.getProp(GoogleCommonKeys.API_SCOPES, WebmastersScopes.WEBMASTERS_READONLY);
    Preconditions.checkArgument(Objects.equals(WebmastersScopes.WEBMASTERS_READONLY, scope) || Objects
            .equals(WebmastersScopes.WEBMASTERS, scope),
        "The scope for WebMaster must either be WEBMASTERS_READONLY or WEBMASTERS");

    String credentialFile = wuState.getProp(SOURCE_CONN_PRIVATE_KEY);
    List<String> scopes = Collections.singletonList(scope);

//    return GoogleCredential.fromStream(new FileInputStream(credentialFile))
//        .createScoped(Collections.singletonList(scope));

    return new GoogleCommon.CredentialBuilder(credentialFile, scopes)
        .fileSystemUri(wuState.getProp(GoogleCommonKeys.PRIVATE_KEY_FILESYSTEM_URI))
        .proxyUrl(wuState.getProp(SOURCE_CONN_USE_PROXY_URL)).port(wuState.getProp(SOURCE_CONN_USE_PROXY_PORT))
        .serviceAccountId(wuState.getProp(SOURCE_CONN_USERNAME)).build();
  }
}
