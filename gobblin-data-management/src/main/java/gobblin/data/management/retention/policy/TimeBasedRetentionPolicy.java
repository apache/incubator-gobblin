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

package gobblin.data.management.retention.policy;

import java.util.Collection;
import java.util.List;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.ISOPeriodFormat;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;

import gobblin.data.management.retention.DatasetCleaner;
import gobblin.data.management.version.DatasetVersion;
import gobblin.data.management.version.TimestampedDatasetVersion;


/**
 * Retain dataset versions newer than now - {@link #retention}.
 */
@Slf4j
public class TimeBasedRetentionPolicy implements RetentionPolicy<TimestampedDatasetVersion> {

  public static final String RETENTION_MINUTES_KEY = DatasetCleaner.CONFIGURATION_KEY_PREFIX + "minutes.retained";
  public static final String RETENTION_MINUTES_DEFAULT = Long.toString(24 * 60); // one day

  // ISO8601 Standard PyYmMwWdDThHmMsS
  public static final String RETENTION_TIMEBASED_DURATION_KEY =
      DatasetCleaner.CONFIGURATION_KEY_PREFIX + "timebased.duration";
  private final Duration retention;

  public TimeBasedRetentionPolicy(Properties props) {
    this.retention =
        Duration.standardMinutes(Long.parseLong(props.getProperty(RETENTION_MINUTES_KEY, RETENTION_MINUTES_DEFAULT)));
  }

  /**
   * Creates a new {@link TimeBasedRetentionPolicy} using {@link #RETENTION_TIMEBASED_DURATION_KEY} in the
   * <code>config</code>
   * <ul> Some Example values for {@link #RETENTION_TIMEBASED_DURATION_KEY} are
   * <li> P20D = 20 Days
   * <li> P20H = 20 Hours
   * <li> P2Y = 2 Years
   * <li> P2Y3M = 2 Years and 3 Months
   * <li> PT23M = 23 Minutes (Note this is different from P23M which is 23 Months)
   * </ul>
   *
   * @param config that holds retention duration in ISO8061 format at key {@link #RETENTION_TIMEBASED_DURATION_KEY}.
   */
  public TimeBasedRetentionPolicy(Config config) {
    this(config.getString(RETENTION_TIMEBASED_DURATION_KEY));
  }

  public TimeBasedRetentionPolicy(String duration) {
    this.retention = parseDuration(duration);
    log.info(String.format("%s will delete dataset versions older than %s.", TimeBasedRetentionPolicy.class.getName(),
        duration));
  }

  @Override
  public Class<? extends DatasetVersion> versionClass() {
    return TimestampedDatasetVersion.class;
  }

  @Override
  public Collection<TimestampedDatasetVersion> listDeletableVersions(List<TimestampedDatasetVersion> allVersions) {
    return Lists.newArrayList(Collections2.filter(allVersions, new Predicate<DatasetVersion>() {
      @Override
      public boolean apply(DatasetVersion version) {
        return ((TimestampedDatasetVersion) version).getDateTime().plus(TimeBasedRetentionPolicy.this.retention)
            .isBeforeNow();
      }
    }));
  }

  /**
   * Since months and years can have arbitrary days, joda time does not allow conversion of a period string containing
   * months or years to a duration. Hence we calculate the duration using 1970 01:01:00:00:00 UTC as a reference time.
   *
   * <p>
   * <code>
   * (1970 01:01:00:00:00 + P2Y) - 1970 01:01:00:00:00 = Duration for 2 years
   * </code>
   * </p>
   *
   * @param periodString
   * @return duration for this period.
   */
  private static Duration parseDuration(String periodString) {
    DateTime zeroEpoc = new DateTime(0);
    return new Duration(zeroEpoc, zeroEpoc.plus(ISOPeriodFormat.standard().parsePeriod(periodString)));
  }
}
