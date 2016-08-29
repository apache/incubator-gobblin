package gobblin.data.management.copy.replication;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

public class Test {
  public static void displayAllConfig(Config c){
    for(Map.Entry<String, ConfigValue> entry :c.entrySet()){
      System.out.println("key " + entry.getKey() + " value " + entry.getValue());
    }
  }
  
  public static void main(String[]args){
    System.out.println("Test replication config");
    
    File f = new File("/Users/mitu/ConfigTest/dataset.conf");
    Config c = ConfigFactory.parseFile(f).resolve();
    //displayAllConfig(c.getConfig(ReplicationConfiguration.METADATA));
    //Config source = c.getConfig(ReplicationConfiguration.REPLICATION_SOURCE);
    //displayAllConfig(source);
    //displayAllConfig(c.getConfig(ReplicationConfiguration.REPLICATION_REPLICAS));
    
    ReplicationConfiguration rc = ReplicationConfiguration.buildFromConfig(c);
    //ReplicationMetaData md = ReplicationUtils.buildMetaData(c);
    ReplicationMetaData md = rc.getMetaData();
    System.out.println("metadata : " + md);
    
    //ReplicationSource source = ReplicationUtils.buildSource(c);
    ReplicationSource source = rc.getSource();
    System.out.println("source : " + source);
    
    List<ReplicationReplica> replicas = rc.getReplicas(); //ReplicationUtils.buildReplicas(c);
    for(ReplicationReplica r: replicas){
      System.out.println("replica: " + r);
    }
    
    DataFlowTopology topology = rc.getTopology();
    for(DataFlowTopology.CopyRoute route: topology.getRoutes()){
      System.out.println("route: " + route);
    }
  }
}
