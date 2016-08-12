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
package gobblin.runtime.scheduler;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import gobblin.runtime.api.JobSpec;
import gobblin.runtime.api.JobSpecSchedule;
import gobblin.runtime.api.JobSpecScheduler;
import gobblin.runtime.api.JobSpecSchedulerListener;
import gobblin.runtime.std.DefaultJobSpecScheduleImpl;
import gobblin.util.LoggingUncaughtExceptionHandler;

import lombok.AllArgsConstructor;

/**
 * A simple implementation of a {@link JobSpecScheduler} which schedules the job immediately.
 * Note that job runnable is scheduled immediately but this happens in a separate thread so it is
 * asynchronous.
 */
public class ImmediateJobSpecScheduler implements JobSpecScheduler {
  private final Logger _log;
  private final ThreadFactory _jobRunnablesThreadFactory;
  private final JobSpecSchedulerListeners _callbacksDispatcher;

  public ImmediateJobSpecScheduler(Optional<Logger> log) {
    _log = log.isPresent() ? log.get() : LoggerFactory.getLogger(getClass());
    _jobRunnablesThreadFactory = (new ThreadFactoryBuilder())
        .setDaemon(false)
        .setNameFormat(_log.getName() + "-%d")
        .setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler(Optional.of(_log)))
        .build();
    _callbacksDispatcher = new JobSpecSchedulerListeners(_log);
  }

  /** {@inheritDoc} */
  @Override public JobSpecSchedule scheduleJob(JobSpec jobSpec, Runnable jobRunnable) {
    return scheduleOnce(jobSpec, jobRunnable);
  }

  @Override
  public JobSpecSchedule scheduleOnce(JobSpec jobSpec, Runnable jobRunnable) {
    Thread runThread = _jobRunnablesThreadFactory.newThread(jobRunnable);
    _log.info("Starting JobSpec " + jobSpec + " in thread " + runThread.getName());
    JobSpecSchedule schedule =
        DefaultJobSpecScheduleImpl.createImmediateSchedule(jobSpec, jobRunnable);
    _callbacksDispatcher.onJobScheduled(schedule);
    runThread.start();
    return schedule;
  }

  /** {@inheritDoc} */
  @Override public void unscheduleJob(URI jobSpecURI) {
    // No-op since no future schedules are maintained
  }

  /** {@inheritDoc} */
  @Override public Map<URI, JobSpecSchedule> getSchedules() {
    // No future schedules
    return Collections.emptyMap();
  }

  @Override public void registerJobSpecSchedulerListener(JobSpecSchedulerListener listener) {
    _callbacksDispatcher.registerJobSpecSchedulerListener(listener);
  }

  @Override
  public void unregisterJobSpecSchedulerListener(JobSpecSchedulerListener listener) {
    _callbacksDispatcher.unregisterJobSpecSchedulerListener(listener);
  }

  @Override
  public List<JobSpecSchedulerListener> getJobSpecSchedulerListeners() {
    return _callbacksDispatcher.getJobSpecSchedulerListeners();
  }

  @AllArgsConstructor
  protected class TriggerRunnable implements Runnable {
    private final JobSpecSchedule jobSchedule;

    @Override public void run() {
      _callbacksDispatcher.onJobTriggered(jobSchedule);
      this.jobSchedule.getJobRunnable().run();
    }
  }

}
