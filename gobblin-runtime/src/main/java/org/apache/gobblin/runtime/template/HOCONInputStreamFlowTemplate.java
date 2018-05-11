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

package org.apache.gobblin.runtime.template;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

import org.apache.gobblin.configuration.ConfigurationKeys;
import org.apache.gobblin.runtime.api.SpecNotFoundException;
import org.apache.gobblin.runtime.spec_catalog.FlowCatalog;

import com.google.common.base.Charsets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;


public class HOCONInputStreamFlowTemplate extends StaticFlowTemplate {
  public static final String VERSION_KEY = "gobblin.flow.template.version";
  public static final String DEFAULT_VERSION = "1";

  public HOCONInputStreamFlowTemplate(InputStream inputStream, URI uri, FlowCatalog catalog) throws SpecNotFoundException{
    this(ConfigFactory.parseReader(new InputStreamReader(inputStream, Charsets.UTF_8)), uri, catalog);
  }

  public HOCONInputStreamFlowTemplate(Config config, URI uri, FlowCatalog catalog) throws SpecNotFoundException{
    super(uri, config.hasPath(VERSION_KEY) ? config.getString(VERSION_KEY) : DEFAULT_VERSION,
        config.hasPath(ConfigurationKeys.FLOW_DESCRIPTION_KEY) ? config.getString(ConfigurationKeys.FLOW_DESCRIPTION_KEY) : "",
        config, catalog);
  }
}
