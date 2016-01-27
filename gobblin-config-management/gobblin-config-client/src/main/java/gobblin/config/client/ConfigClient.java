/*
 * Copyright (C) 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.config.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;

import gobblin.config.client.api.ConfigStoreFactoryDoesNotExistsException;
import gobblin.config.client.api.VersionStabilityPolicy;
import gobblin.config.common.impl.ConfigStoreBackedTopology;
import gobblin.config.common.impl.ConfigStoreBackedValueInspector;
import gobblin.config.common.impl.ConfigStoreTopologyInspector;
import gobblin.config.common.impl.ConfigStoreValueInspector;
import gobblin.config.common.impl.InMemoryTopology;
import gobblin.config.common.impl.InMemoryValueInspector;
import gobblin.config.store.api.ConfigKeyPath;
import gobblin.config.store.api.ConfigStore;
import gobblin.config.store.api.ConfigStoreCreationException;
import gobblin.config.store.api.ConfigStoreFactory;
import gobblin.config.store.api.ConfigStoreWithStableVersioning;
import gobblin.config.store.api.VersionDoesNotExistException;

/**
 * This class is used by Client to access the Configuration Management core library.
 * 
 * 
 * @author mitu
 *
 */
public class ConfigClient {
  private final VersionStabilityPolicy policy;

  /** Normally key is the ConfigStore.getStoreURI(), value is the ConfigStoreAccessor
   *  
   * However, there may be two entries for a specific config store, for example
   * if user pass in URI like "etl-hdfs:///datasets/a1/a2" and the etl-hdfs config store factory using
   * default authority/default config store root normalized the URI to
   * "etl-hdfs://eat1-nertznn01.grid.linkedin.com:9000/user/mitu/HdfsBasedConfigTest/datasets/a1/a2"
   * 
   * Then there will be two entries in the Map which point to the same value
   * key1: "etl-hdfs:/"
   * key2: "etl-hdfs://eat1-nertznn01.grid.linkedin.com:9000/user/mitu/HdfsBasedConfigTest/"
   * 
   */
  private final TreeMap<URI, ConfigStoreAccessor> configStoreAccessorMap = new TreeMap<>();
  
  private final ConfigStoreFactoryRegister configStoreFactoryRegister;

  private ConfigClient(VersionStabilityPolicy policy) {
    this.policy = policy;

    this.configStoreFactoryRegister = new ConfigStoreFactoryRegister();
  }
  
  @VisibleForTesting
  ConfigClient(VersionStabilityPolicy policy, ConfigStoreFactoryRegister register) {
    this.policy = policy;

    this.configStoreFactoryRegister = register;
  }

  /**
   * Create the {@link ConfigClient} based on the {@link VersionStabilityPolicy}. 
   * @param policy - {@link VersionStabilityPolicy} to specify the stability policy which control the caching layer creation 
   * @return       - {@link ConfigClient} for client to use to access the {@link ConfigStore}
   */
  public static ConfigClient createConfigClient(VersionStabilityPolicy policy) {
    return new ConfigClient(policy);
  }

  /**
   * Get the resolved {@link Config} based on the input URI.
   * 
   * @param configKeyUri - The URI for the configuration key. There are two types of URI:
   * 
   * 1. URI missing authority and configuration store root , for example "etl-hdfs:///datasets/a1/a2". It will get
   *    the configuration based on the default {@link ConfigStore} in etl-hdfs {@link ConfigStoreFactory}
   * 2. Complete URI:  for example "etl-hdfs://eat1-nertznn01.grid.linkedin.com:9000/user/mitu/HdfsBasedConfigTest/"
   * 
   * @return  the resolved {@link Config} based on the input URI.
   * 
   * @throws ConfigStoreFactoryDoesNotExistsException: if missing scheme name or the scheme name is invalid
   * @throws ConfigStoreCreationException: Specified {@link ConfigStoreFactory} can not create required {@link ConfigStore}
   * @throws VersionDoesNotExistException: Required version does not exist anymore ( may get deleted by retention job )
   */
  public Config getConfig(URI configKeyUri) throws ConfigStoreFactoryDoesNotExistsException,
      ConfigStoreCreationException, VersionDoesNotExistException {
    ConfigStoreAccessor accessor = this.getConfigStoreAccessor(configKeyUri);
    ConfigKeyPath configKeypath = ConfigClientUtils.buildConfigKeyPath(configKeyUri, accessor.configStore);
    return accessor.valueInspector.getResolvedConfig(configKeypath);
  }
  
  /**
   * batch process for getConfig function
   * @param configKeyUris
   * @return
   * @throws ConfigStoreFactoryDoesNotExistsException
   * @throws ConfigStoreCreationException
   * @throws VersionDoesNotExistException
   */
  public Map<URI, Config> getConfigs(Collection<URI> configKeyUris)throws ConfigStoreFactoryDoesNotExistsException,
  ConfigStoreCreationException, VersionDoesNotExistException {
    if(configKeyUris == null || configKeyUris.size()==0 )
      return Collections.emptyMap();
    
    Map<URI, Config> result = new HashMap<>();
    Map<ConfigStoreAccessor, Collection<ConfigKeyPath>> partitionedAccessor = new HashMap<>();
    
    // partitioned the ConfigKeyPaths which belongs to the same store to one accessor 
    for(URI u: configKeyUris){
      ConfigStoreAccessor accessor = this.getConfigStoreAccessor(u);
      ConfigKeyPath configKeypath = ConfigClientUtils.buildConfigKeyPath(u, accessor.configStore);
      if(!partitionedAccessor.containsKey(accessor)){
        partitionedAccessor.put(accessor, new ArrayList<ConfigKeyPath>());
      }
      
      partitionedAccessor.get(accessor).add(configKeypath);
    }
    
    for(Map.Entry<ConfigStoreAccessor, Collection<ConfigKeyPath>> entry: partitionedAccessor.entrySet()){
      Map<ConfigKeyPath, Config> batchResult= entry.getKey().valueInspector.getResolvedConfigs(entry.getValue());
      // translate the ConfigKeyPath to URI
      for(Map.Entry<ConfigKeyPath, Config> resultEntry: batchResult.entrySet()){
        URI absURI = ConfigClientUtils.getAbsoluteURI(resultEntry.getKey(), entry.getKey().configStore);
        result.put(absURI, resultEntry.getValue());
      }
    }
    
    return result;
  }

  /**
   * Convenient method to get resolved {@link Config} based on String input.
   */
  public Config getConfig(String configKeyStr) throws ConfigStoreFactoryDoesNotExistsException,
      ConfigStoreCreationException, VersionDoesNotExistException, URISyntaxException {
    return this.getConfig(new URI(configKeyStr));
  }
  
  /**
   * batch process for getConfig(String)
   * @param configKeyStrs
   * @return
   * @throws ConfigStoreFactoryDoesNotExistsException
   * @throws ConfigStoreCreationException
   * @throws VersionDoesNotExistException
   * @throws URISyntaxException
   */
  public Map<URI, Config> getConfigsFromStrings(Collection<String> configKeyStrs) throws ConfigStoreFactoryDoesNotExistsException,
  ConfigStoreCreationException, VersionDoesNotExistException, URISyntaxException {
    if(configKeyStrs == null || configKeyStrs.size()==0 )
      return Collections.emptyMap();
    
    Collection<URI> configKeyUris = new ArrayList<>();
    for(String s: configKeyStrs){
      configKeyUris.add(new URI(s));
    }
    return getConfigs(configKeyUris);
  }

  /**
   * Get the import links of the input URI.
   * 
   * @param configKeyUri - The URI for the configuration key. 
   * @param recursive    - Specify whether to get direct import links or recursively import links
   * @return  the import links of the input URI.
   * 
   * @throws ConfigStoreFactoryDoesNotExistsException: if missing scheme name or the scheme name is invalid
   * @throws ConfigStoreCreationException: Specified {@link ConfigStoreFactory} can not create required {@link ConfigStore}
   * @throws VersionDoesNotExistException: Required version does not exist anymore ( may get deleted by retention job )
   */
  public Collection<URI> getImports(URI configKeyUri, boolean recursive)
      throws ConfigStoreFactoryDoesNotExistsException, ConfigStoreCreationException, VersionDoesNotExistException {
    ConfigStoreAccessor accessor = this.getConfigStoreAccessor(configKeyUri);
    ConfigKeyPath configKeypath = ConfigClientUtils.buildConfigKeyPath(configKeyUri, accessor.configStore);
    Collection<ConfigKeyPath> result;
    
    if(!recursive){
      result = accessor.topologyInspector.getOwnImports(configKeypath);
    }
    else{
      result = accessor.topologyInspector.getImportsRecursively(configKeypath);
    }
    
    return ConfigClientUtils.getAbsoluteURI(result, accessor.configStore);
  }

  /**
   * Get the URIs which imports the input URI
   * 
   * @param configKeyUri - The URI for the configuration key. 
   * @param recursive    - Specify whether to get direct or recursively imported by links
   * @return  the URIs which imports the input URI
   * 
   * @throws ConfigStoreFactoryDoesNotExistsException: if missing scheme name or the scheme name is invalid
   * @throws ConfigStoreCreationException: Specified {@link ConfigStoreFactory} can not create required {@link ConfigStore}
   * @throws VersionDoesNotExistException: Required version does not exist anymore ( may get deleted by retention job )
   */
  public Collection<URI> getImportedBy(URI configKeyUri, boolean recursive)
      throws ConfigStoreFactoryDoesNotExistsException, ConfigStoreCreationException, VersionDoesNotExistException {
    ConfigStoreAccessor accessor = this.getConfigStoreAccessor(configKeyUri);
    ConfigKeyPath configKeypath = ConfigClientUtils.buildConfigKeyPath(configKeyUri, accessor.configStore);
    Collection<ConfigKeyPath> result;
    
    if(!recursive){
      result = accessor.topologyInspector.getImportedBy(configKeypath);
    }
    else{
      result = accessor.topologyInspector.getImportedByRecursively(configKeypath);
    }
    
    return ConfigClientUtils.getAbsoluteURI(result, accessor.configStore);
  }
  
  private URI getMatchedFloorKeyFromCache(URI configKeyURI){
    URI floorKey = this.configStoreAccessorMap.floorKey(configKeyURI);
    if(floorKey==null) {
      return null;
    }
    
    // both scheme name and authority name should match
    if(floorKey.getScheme().equals(configKeyURI.getScheme())){
      // no authority/store root directory
      if(floorKey.getAuthority()==null && configKeyURI.getAuthority()==null){
        return floorKey;
      }
      
      if(floorKey.getAuthority()==null || configKeyURI.getAuthority()==null){
        return null;
      }
      
      // both are absolute URI
      if(floorKey.getAuthority().equals(configKeyURI.getAuthority())){
        if(configKeyURI.getPath().startsWith(floorKey.getPath())) {
          return floorKey;
        }
      }
    }
    
    return null;
  }
  
  private ConfigStoreAccessor createNewConfigStoreAccessor(URI configKeyURI) throws ConfigStoreFactoryDoesNotExistsException,
  ConfigStoreCreationException, VersionDoesNotExistException{
    
    ConfigStoreAccessor result;
    ConfigStoreFactory<ConfigStore> csFactory = this.getConfigStoreFactory(configKeyURI);
    ConfigStore cs = csFactory.createConfigStore(configKeyURI);

    if (!(cs instanceof ConfigStoreWithStableVersioning)) {
      if (this.policy == VersionStabilityPolicy.CROSS_JVM_STABILITY) {
        throw new RuntimeException(String.format(
            "with policy set to %s, connect not connect to unstable config store %s",
            VersionStabilityPolicy.CROSS_JVM_STABILITY, cs.getStoreURI()));
      }
    }
    
    String currentVersion = cs.getCurrentVersion();
    // topology related
    ConfigStoreBackedTopology csTopology = new ConfigStoreBackedTopology(cs, currentVersion);
    InMemoryTopology inMemoryTopology = new InMemoryTopology(csTopology);
    
    // value related
    ConfigStoreBackedValueInspector rawValueInspector = new ConfigStoreBackedValueInspector(cs, currentVersion, inMemoryTopology);
    InMemoryValueInspector inMemoryValueInspector;
    
    // ConfigStoreWithStableVersioning always create Soft reference cache
    if ( cs instanceof ConfigStoreWithStableVersioning){
      inMemoryValueInspector = new InMemoryValueInspector(rawValueInspector, false);
      result = new ConfigStoreAccessor(cs, inMemoryValueInspector, inMemoryTopology);
    }
    // Non ConfigStoreWithStableVersioning but require STRONG_LOCAL_STABILITY, use Strong reference cache
    else if (this.policy == VersionStabilityPolicy.STRONG_LOCAL_STABILITY) {
      inMemoryValueInspector = new InMemoryValueInspector(rawValueInspector, true);
      result = new ConfigStoreAccessor(cs, inMemoryValueInspector, inMemoryTopology);
    }
    // Require No cache
    else {
      result = new ConfigStoreAccessor(cs, rawValueInspector, inMemoryTopology);
    }
    
    return result;
  }
  
  private ConfigStoreAccessor getConfigStoreAccessor(URI configKeyURI) throws ConfigStoreFactoryDoesNotExistsException,
      ConfigStoreCreationException, VersionDoesNotExistException {
    
    URI matchedFloorKey = getMatchedFloorKeyFromCache(configKeyURI);
    ConfigStoreAccessor result;
    if(matchedFloorKey!=null){
      result = this.configStoreAccessorMap.get(matchedFloorKey);
      return result;
    }
    
    result = createNewConfigStoreAccessor(configKeyURI);
    ConfigStore cs = result.configStore;
    
    // put to cache
    this.configStoreAccessorMap.put(cs.getStoreURI(), result);
    
    // put default root URI in cache as well for the URI which missing authority 
    if(configKeyURI.getAuthority() == null){
      this.configStoreAccessorMap.put(ConfigClientUtils.getDefaultRootURI(configKeyURI, cs), result);
    }

    return result;
  }
  
  // use serviceLoader to load configStoreFactories
  @SuppressWarnings("unchecked")
  private ConfigStoreFactory<ConfigStore> getConfigStoreFactory(URI configKeyUri)
      throws ConfigStoreFactoryDoesNotExistsException {
    @SuppressWarnings("rawtypes")
    ConfigStoreFactory csf = configStoreFactoryRegister.getConfigStoreFactory(configKeyUri.getScheme());
    if (csf == null) {
      throw new ConfigStoreFactoryDoesNotExistsException(configKeyUri.getScheme(), "scheme name does not exists");
    }

    return (ConfigStoreFactory<ConfigStore>) csf;
  }

  static class ConfigStoreAccessor {
    final ConfigStore configStore;
    final ConfigStoreValueInspector valueInspector;
    final ConfigStoreTopologyInspector topologyInspector;

    ConfigStoreAccessor(ConfigStore cs, ConfigStoreValueInspector valueInspector, ConfigStoreTopologyInspector topologyInspector) {
      this.configStore = cs;
      this.valueInspector = valueInspector;
      this.topologyInspector = topologyInspector;
    }
  }
}
