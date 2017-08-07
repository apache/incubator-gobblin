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

package gobblin.service;

import gobblin.annotation.Alpha;
import gobblin.runtime.spec_executorInstance.InMemorySpecExecutor;
import gobblin.service.modules.flow.IdentityFlowToJobSpecCompiler;
import gobblin.service.modules.topology.ConfigBasedTopologySpecFactory;

@Alpha
public class ServiceConfigKeys {

  private static final String GOBBLIN_SERVICE_PREFIX = "gobblin.service.";

  // Gobblin Service Manager Keys
  public static final String GOBBLIN_SERVICE_TOPOLOGY_CATALOG_ENABLED_KEY = GOBBLIN_SERVICE_PREFIX + "topologyCatalog.enabled";
  public static final String GOBBLIN_SERVICE_FLOW_CATALOG_ENABLED_KEY = GOBBLIN_SERVICE_PREFIX + "flowCatalog.enabled";
  public static final String GOBBLIN_SERVICE_SCHEDULER_ENABLED_KEY = GOBBLIN_SERVICE_PREFIX + "scheduler.enabled";
  public static final String GOBBLIN_SERVICE_ORCHESTRATOR_ENABLED_KEY = GOBBLIN_SERVICE_PREFIX + "orchestrator.enabled";
  public static final String GOBBLIN_SERVICE_RESTLI_SERVER_ENABLED_KEY = GOBBLIN_SERVICE_PREFIX + "restliServer.enabled";
  public static final String GOBBLIN_SERVICE_TOPOLOGY_SPEC_FACTORY_ENABLED_KEY = GOBBLIN_SERVICE_PREFIX + "topologySpecFactory.enabled";

  // Helix / ServiceScheduler Keys
  public static final String HELIX_CLUSTER_NAME_KEY = GOBBLIN_SERVICE_PREFIX + "helix.cluster.name";
  public static final String ZK_CONNECTION_STRING_KEY = GOBBLIN_SERVICE_PREFIX + "zk.connection.string";
  public static final String HELIX_INSTANCE_NAME_OPTION_NAME = "helix_instance_name";
  public static final String HELIX_INSTANCE_NAME_KEY = GOBBLIN_SERVICE_PREFIX + "helixInstanceName";
  public static final String GOBBLIN_SERVICE_FLOWSPEC = GOBBLIN_SERVICE_PREFIX + "flowSpec";

  // Helix message sub types for FlowSpec
  public static final String HELIX_FLOWSPEC_ADD = "FLOWSPEC_ADD";
  public static final String HELIX_FLOWSPEC_REMOVE = "FLOWSPEC_REMOVE";
  public static final String HELIX_FLOWSPEC_UPDATE = "FLOWSPEC_UPDATE";

  // Flow Compiler Keys
  public static final String GOBBLIN_SERVICE_FLOWCOMPILER_CLASS_KEY = GOBBLIN_SERVICE_PREFIX + "flowCompiler.class";
  public static final String DEFAULT_GOBBLIN_SERVICE_FLOWCOMPILER_CLASS = IdentityFlowToJobSpecCompiler.class.getCanonicalName();

  // Flow specific Keys
  public static final String FLOW_SOURCE_IDENTIFIER_KEY = "gobblin.flow.sourceIdentifier";
  public static final String FLOW_DESTINATION_IDENTIFIER_KEY = "gobblin.flow.destinationIdentifier";

  // Command line options
  public static final String SERVICE_NAME_OPTION_NAME = "service_name";

  // Topology Factory Keys (for overall factory)
  public static final String TOPOLOGY_FACTORY_PREFIX = "topologySpecFactory.";
  public static final String DEFAULT_TOPOLOGY_SPEC_FACTORY = ConfigBasedTopologySpecFactory.class.getCanonicalName();
  public static final String TOPOLOGYSPEC_FACTORY_KEY = TOPOLOGY_FACTORY_PREFIX + "class";
  public static final String TOPOLOGY_FACTORY_TOPOLOGY_NAMES_KEY = TOPOLOGY_FACTORY_PREFIX + "topologyNames";

  // Topology Factory Keys (for individual topologies)
  public static final String TOPOLOGYSPEC_DESCRIPTION_KEY = "description";
  public static final String TOPOLOGYSPEC_VERSION_KEY = "version";
  public static final String TOPOLOGYSPEC_URI_KEY = "uri";
  public static final String DEFAULT_SPEC_EXECUTOR = InMemorySpecExecutor.class.getCanonicalName();
  public static final String SPEC_EXECUTOR_KEY = "specExecutorInstance.class";
  public static final String EDGE_SECURITY_KEY = "edge.secured";
  public static final String INITIAL_LOAD = "initialLoad";

  // Template Catalog Keys
  public static final String TEMPLATE_CATALOGS_FULLY_QUALIFIED_PATH_KEY = GOBBLIN_SERVICE_PREFIX + "templateCatalogs.fullyQualifiedPath";

  // Keys related to user-specified policy on route slection.

  // Undesired connection to form an executable JobSpec.
  // Formatted as a String list, each entry contains a string in the format of "Source1:Sink1",
  // which indicates that data movement from source1 to sink1 should be avoided.
  public static final String POLICY_BASED_BLOCKED_CONNECTION = GOBBLIN_SERVICE_PREFIX + "blocked.connection";
  // Complete path of how the data movement is executed from source to sink.
  // Formatted as a String, each hop separated by comma, from source to sink in order.
  public static final String POLICY_BASED_DATA_MOVEMENT_PATH = GOBBLIN_SERVICE_PREFIX + "full.data.path";
  // Priority criteria for multiple SpecExecutor has the same capabilities.
  public static final String POLICY_BASED_SPEC_EXECUTOR_SELECTION = GOBBLIN_SERVICE_PREFIX + "specExecutor.selection.policy";
  // Topology edge weight enabled or not.
  public static final String TOPOLOGY_EDGE_WEIGHT_ENABLED = GOBBLIN_SERVICE_PREFIX + "edgeWeight.enabled";


}
