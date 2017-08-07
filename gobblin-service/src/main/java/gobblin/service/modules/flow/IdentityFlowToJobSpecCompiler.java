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

package gobblin.service.modules.flow;

import gobblin.runtime.api.ServiceNode;
import gobblin.runtime.api.SpecExecutor;
import gobblin.runtime.api.SpecProducer;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;

import gobblin.annotation.Alpha;
import gobblin.instrumented.Instrumented;
import gobblin.runtime.api.FlowSpec;
import gobblin.runtime.api.JobSpec;
import gobblin.runtime.api.Spec;
import gobblin.runtime.api.TopologySpec;
import gobblin.service.ServiceConfigKeys;


/***
 * Take in a logical {@link Spec} ie flow and compile corresponding materialized job {@link Spec}
 * and its mapping to {@link SpecExecutor}.
 */
@Alpha
public class IdentityFlowToJobSpecCompiler extends BaseFlowToJobSpecCompiler {

  public IdentityFlowToJobSpecCompiler(Config config) {
    super(config, true);
  }

  public IdentityFlowToJobSpecCompiler(Config config, boolean instrumentationEnabled) {
    super(config, Optional.<Logger>absent(), instrumentationEnabled);
  }

  public IdentityFlowToJobSpecCompiler(Config config, Optional<Logger> log) {
    super(config, log, true);
  }

  public IdentityFlowToJobSpecCompiler(Config config, Optional<Logger> log, boolean instrumentationEnabled) {
    super(config, log, instrumentationEnabled);
  }

  @Override
  public Map<Spec, SpecProducer> compileFlow(Spec spec) {
    Preconditions.checkNotNull(spec);
    Preconditions.checkArgument(spec instanceof FlowSpec, "IdentityFlowToJobSpecCompiler only converts FlowSpec to JobSpec");

    long startTime = System.nanoTime();
    Map<Spec, SpecProducer> specExecutorInstanceMap = Maps.newLinkedHashMap();

    FlowSpec flowSpec = (FlowSpec) spec;
    String source = flowSpec.getConfig().getString(ServiceConfigKeys.FLOW_SOURCE_IDENTIFIER_KEY);
    String destination = flowSpec.getConfig().getString(ServiceConfigKeys.FLOW_DESTINATION_IDENTIFIER_KEY);
    log.info(String.format("Compiling flow for source: %s and destination: %s", source, destination));

    JobSpec jobSpec = jobSpecGenerator(flowSpec);

    for (TopologySpec topologySpec : topologySpecMap.values()) {
      try {
        Map<ServiceNode, ServiceNode> capabilities = (Map<ServiceNode, ServiceNode>) topologySpec.getSpecExecutorInstance().getCapabilities().get();
        for (Map.Entry<ServiceNode, ServiceNode> capability : capabilities.entrySet()) {
          log.info(String.format("Evaluating current JobSpec: %s against TopologySpec: %s with "
              + "capability of source: %s and destination: %s ", jobSpec.getUri(),
              topologySpec.getUri(), capability.getKey(), capability.getValue()));
          if (source.equals(capability.getKey().getNodeName()) && destination.equals(capability.getValue().getNodeName())) {
            specExecutorInstanceMap.put(jobSpec, topologySpec.getSpecExecutorInstance());
            log.info(String.format("Current JobSpec: %s is executable on TopologySpec: %s. Added TopologySpec as candidate.",
                jobSpec.getUri(), topologySpec.getUri()));

            log.info("Since we found a candidate executor, we will not try to compute more. "
                + "(Intended limitation for IdentityFlowToJobSpecCompiler)");
            return specExecutorInstanceMap;
          }
        }
      } catch (InterruptedException | ExecutionException e) {
        Instrumented.markMeter(this.flowCompilationFailedMeter);
        throw new RuntimeException("Cannot determine topology capabilities", e);
      }
    }
    Instrumented.markMeter(this.flowCompilationSuccessFulMeter);
    Instrumented.updateTimer(this.flowCompilationTimer, System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

    return specExecutorInstanceMap;
  }
}
