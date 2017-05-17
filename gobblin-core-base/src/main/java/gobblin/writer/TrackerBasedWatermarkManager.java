/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package gobblin.writer;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import gobblin.configuration.WorkUnitState;
import gobblin.source.extractor.CheckpointableWatermark;
import gobblin.source.extractor.RecordEnvelope;
import gobblin.util.ExecutorsUtils;
import gobblin.watermark.InOrderWatermarkTracker;


/**
 * Responsible for managing continuous commit of watermarks.
 * Uses a {@link FineGrainedWatermarkTracker} to keep track of watermarks.
 * Periodically fetches watermarks from the tracker and commits them to WatermarkStorage.
 * TODO: Add metrics monitoring
 */
public class TrackerBasedWatermarkManager implements WatermarkManager {

  private final InOrderWatermarkTracker _watermarkTracker;
  private final WatermarkStorage _watermarkStorage;
  private final long _commitIntervalMillis;
  private final ScheduledExecutorService _watermarkCommitThreadPool;
  private final Logger _logger;
  private final RetrievalStatus _retrievalStatus;
  private final CommitStatus _commitStatus;

  @VisibleForTesting
  final Runnable _watermarkCommitter = new

      Runnable() {
        @Override
        public void run() {
          long startTime = System.nanoTime();
          Map<String, CheckpointableWatermark> watermarksToCommit = null;
          try {
            _retrievalStatus.onAttempt();
            watermarksToCommit = _watermarkTracker.getAllCommitableWatermarks();
            _logger.debug("Retrieved watermark {}", watermarksToCommit);
            _retrievalStatus.onSuccess(watermarksToCommit);
          }
          catch (Exception e) {
            _retrievalStatus.onFailure(e);
            _logger.error("Failed to get watermark", e);
          }
          // Prevent multiple commits concurrently
          synchronized (this) {
            if (watermarksToCommit != null && !watermarksToCommit.isEmpty()) {
              try {
                _commitStatus.onAttempt();
                _logger.info("Will commit watermark {}", watermarksToCommit.toString());
                //TODO: Not checking if this watermark has already been committed successfully.
                _watermarkStorage.commitWatermarks(watermarksToCommit.values());
                _commitStatus.onSuccess(watermarksToCommit);
              } catch (Exception e) {
                _commitStatus.onFailure(e, watermarksToCommit);
                _logger.error("Failed to write watermark", e);
              }
            } else {
              _logger.info("Nothing to commit");
            }
          }
          long duration = (System.nanoTime() - startTime)/1000000;
          _logger.info("Duration of run {} milliseconds", duration);
        }
      };

  public TrackerBasedWatermarkManager(WatermarkStorage storage, FineGrainedWatermarkTracker watermarkTracker,
      long commitIntervalMillis, Optional<Logger> logger) {
    Preconditions.checkArgument(storage != null, "WatermarkStorage cannot be null");
    Preconditions.checkArgument(watermarkTracker != null, "WatermarkTracker cannot be null");
    _watermarkTracker = new InOrderWatermarkTracker();
    _watermarkStorage = storage;
    _commitIntervalMillis = commitIntervalMillis;
    _logger = logger.or(LoggerFactory.getLogger(TrackerBasedWatermarkManager.class));
    _watermarkCommitThreadPool = new ScheduledThreadPoolExecutor(1, ExecutorsUtils.newThreadFactory(logger,
        Optional.of("WatermarkManager-%d")));
    _retrievalStatus = new RetrievalStatus();
    _commitStatus = new CommitStatus();
  }

  public void start() {
    _watermarkCommitThreadPool
        .scheduleWithFixedDelay(_watermarkCommitter, 0, _commitIntervalMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public Stream<RecordEnvelope> peekExtractorStream(Stream<RecordEnvelope> extractorStream) {
    return _watermarkTracker.convertRecordStream(null, extractorStream, new WorkUnitState());
  }

  @Override
  public void close()
      throws IOException {
    _logger.info("Watermark committer closing");
    _watermarkCommitThreadPool.shutdown();
    try {
      long startTime = System.nanoTime();
      _watermarkCommitThreadPool.awaitTermination(1000, TimeUnit.MILLISECONDS);
      long duration = (System.nanoTime() - startTime)/ 1000000;
      _logger.info("Duration of termination wait was {} milliseconds", duration);
    }
    catch (InterruptedException ie) {
      throw new IOException("Interrupted while waiting for committer to shutdown", ie);
    }
    finally {
      // final watermark commit
      _logger.info("Watermark committer: one last commit before shutting down");
      _watermarkCommitter.run();
    }
  }

  @Override
  public CommitStatus getCommitStatus() {
    return _commitStatus;
  }

  @Override
  public RetrievalStatus getRetrievalStatus() {
    return _retrievalStatus;
  }
}
