/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.data.management.copy.replication;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.google.common.base.Charsets;

import gobblin.commit.CommitStep;

/**
 * A {@link CommitStep} to write watermark metadata to Hdfs
 * @author mitu
 *
 */
public class WatermarkMetadataGenerationCommitStep implements CommitStep {
  private final String fsUriString;
  private final Path targetDirPath;
  private final long latestTimestamp;
  
  private boolean completed = false;
  
  public WatermarkMetadataGenerationCommitStep(String fsString, Path targetDirPath, long timestamp) {
    this.fsUriString = fsString;
    this.targetDirPath = targetDirPath;
    this.latestTimestamp = timestamp;
  }

  @Override
  public boolean isCompleted() throws IOException {
    return this.completed;
  }
  
  @Override 
  public String toString(){
    return "create file " + new Path(this.targetDirPath, ReplicaHadoopFsEndPoint.WATERMARK_FILE)
        + " in fs " + this.fsUriString + " with content " + this.latestTimestamp;
  }

  @Override
  public void execute() throws IOException {
    URI fsURI;
    try {
      fsURI = new URI(this.fsUriString);
    } catch (URISyntaxException e) {
      throw new IOException("can not build URI " + this.fsUriString, e);
    }
    FileSystem fs = FileSystem.get(fsURI, new Configuration());
    
    Path filenamePath = new Path(this.targetDirPath, ReplicaHadoopFsEndPoint.WATERMARK_FILE);
    if (fs.exists(filenamePath)) {
      fs.delete(filenamePath, false);
    }

    FSDataOutputStream fout = fs.create(filenamePath);
    fout.write((ReplicaHadoopFsEndPoint.LATEST_TIMESTAMP+":"+this.latestTimestamp).getBytes(Charsets.UTF_8));
    fout.close();
    this.completed = true;
  }

}
