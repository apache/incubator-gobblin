/* (c) 2014 LinkedIn Corp. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package com.linkedin.uif.source.extractor.partition;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import com.linkedin.uif.configuration.ConfigurationKeys;
import com.linkedin.uif.configuration.SourceState;
import com.linkedin.uif.source.extractor.extract.ExtractType;
import com.linkedin.uif.source.extractor.utils.Utils;
import com.linkedin.uif.source.extractor.watermark.WatermarkPredicate;
import com.linkedin.uif.source.extractor.watermark.WatermarkType;


/**
 * An implementation of default partitioner for all types of sources
 */
public class Partitioner {
  private static final String WATERMARKTIMEFORMAT = "yyyyMMddHHmmss";
  private static final SimpleDateFormat DATETIMEFORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private static final Logger LOG = LoggerFactory.getLogger(Partitioner.class);
  private SourceState state;

  public Partitioner(SourceState state) {
    super();
    this.state = state;
  }

  /**
   * Get partitions with low and high water marks
   *
   * @param previous water mark from metadata
   * @return map of partition intervals
   */
  public HashMap<Long, Long> getPartitions(long previousWatermark) {
    HashMap<Long, Long> defaultPartition = new HashMap<Long, Long>();
    if (!isWatermarkExists()) {
      defaultPartition.put(ConfigurationKeys.DEFAULT_WATERMARK_VALUE, ConfigurationKeys.DEFAULT_WATERMARK_VALUE);
      LOG.info("Watermark column or type not found - Default partition with low watermark and high watermark as "
          + ConfigurationKeys.DEFAULT_WATERMARK_VALUE);
      return defaultPartition;
    }

    ExtractType extractType =
        ExtractType.valueOf(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_EXTRACT_TYPE).toUpperCase());
    WatermarkType watermarkType = WatermarkType.valueOf(
        this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_WATERMARK_TYPE, ConfigurationKeys.DEFAULT_WATERMARK_TYPE)
            .toUpperCase());
    int interval = this.getUpdatedInterval(
        Utils.getAsInt(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_PARTITION_INTERVAL)), extractType,
        watermarkType);
    int sourceMaxAllowedPartitions =
        Utils.getAsInt(this.state.getProp(ConfigurationKeys.SOURCE_MAX_NUMBER_OF_PARTITIONS));
    int maxPartitions = (sourceMaxAllowedPartitions != 0 ? sourceMaxAllowedPartitions
        : ConfigurationKeys.DEFAULT_MAX_NUMBER_OF_PARTITIONS);

    WatermarkPredicate watermark = new WatermarkPredicate(null, watermarkType);
    int deltaForNextWatermark = watermark.getDeltaNumForNextWatermark();

    LOG.info("is watermark override: " + this.isWatermarkOverride());
    LOG.info("is full extract: " + this.isFullDump());
    long lowWatermark = this.getLowWatermark(extractType, watermarkType, previousWatermark, deltaForNextWatermark);
    long highWatermark = this.getHighWatermark(extractType, watermarkType);

    if (lowWatermark == ConfigurationKeys.DEFAULT_WATERMARK_VALUE
        || highWatermark == ConfigurationKeys.DEFAULT_WATERMARK_VALUE) {
      LOG.info(
          "Low watermark or high water mark is not found. Hence cannot generate partitions - Default partition with low watermark:  "
              + lowWatermark + " and high watermark: " + highWatermark);
      defaultPartition.put(lowWatermark, highWatermark);
      return defaultPartition;
    }
    LOG.info("Generate partitions with low watermark: " + lowWatermark + "; high watermark: " + highWatermark
        + "; partition interval in hours: " + interval + "; Maximum number of allowed partitions: " + maxPartitions);
    return watermark.getPartitions(lowWatermark, highWatermark, interval, maxPartitions);
  }

  /**
   * Calculate interval in hours with the given interval
   *
   * @param input interval
   * @param Extract type
   * @param Watermark type
   * @return interval in range
   */
  private int getUpdatedInterval(int inputInterval, ExtractType extractType, WatermarkType watermarkType) {
    LOG.debug("Getting updated interval");
    if ((extractType == ExtractType.SNAPSHOT && watermarkType == WatermarkType.DATE)) {
      return inputInterval * 24;
    } else if (extractType == ExtractType.APPEND_DAILY) {
      return (inputInterval < 1 ? 1 : inputInterval) * 24;
    } else {
      return inputInterval;
    }
  }

  /**
   * Get low water mark
   *
   * @param Extract type
   * @param Watermark type
   * @param Previous water mark
   * @param delta number for next water mark
   * @return low water mark
   */
  private long getLowWatermark(ExtractType extractType, WatermarkType watermarkType, long previousWatermark,
      int deltaForNextWatermark) {
    long lowWatermark = ConfigurationKeys.DEFAULT_WATERMARK_VALUE;
    if (this.isFullDump() || this.isWatermarkOverride()) {
      lowWatermark = Utils.getLongWithCurrentDate(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_START_VALUE));
      LOG.info("Overriding low water mark with the given start value: " + lowWatermark);
    } else {
      if (this.isSnapshot(extractType)) {
        lowWatermark = this.getSnapshotLowWatermark(watermarkType, previousWatermark, deltaForNextWatermark);
      } else {
        lowWatermark = this.getAppendLowWatermark(watermarkType, previousWatermark, deltaForNextWatermark);
      }
    }
    return (lowWatermark == 0 ? ConfigurationKeys.DEFAULT_WATERMARK_VALUE : lowWatermark);
  }

  /**
   * Get low water mark
   *
   * @param Watermark type
   * @param Previous water mark
   * @param delta number for next water mark
   * @return snapshot low water mark
   */
  private long getSnapshotLowWatermark(WatermarkType watermarkType, long previousWatermark, int deltaForNextWatermark) {
    LOG.debug("Getting snapshot low water mark");
    if (this.isPreviousWatermarkExists(previousWatermark)) {
      if (this.isSimpleWatermark(watermarkType)) {
        return previousWatermark + deltaForNextWatermark - Utils
            .getAsInt(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_LOW_WATERMARK_BACKUP_SECS));
      } else {
        return Long.parseLong(Utils.dateToString(Utils
            .addSecondsToDate(Utils.toDate(previousWatermark, WATERMARKTIMEFORMAT), (deltaForNextWatermark - Utils
                .getAsInt(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_LOW_WATERMARK_BACKUP_SECS)))),
            WATERMARKTIMEFORMAT));
      }
    } else {
      // if previous watermark is not found, override with the start value(irrespective of source.is.watermark.override flag)
      LOG.info("Overriding low water mark with the given start value: " + Utils
          .getLongWithCurrentDate(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_START_VALUE)));
      return Utils.getLongWithCurrentDate(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_START_VALUE));
    }
  }

  /**
   * Get low water mark
   *
   * @param Watermark type
   * @param Previous water mark
   * @param delta number for next water mark
   * @return append low water mark
   */
  private long getAppendLowWatermark(WatermarkType watermarkType, long previousWatermark, int deltaForNextWatermark) {
    LOG.debug("Getting append low water mark");
    if (this.isPreviousWatermarkExists(previousWatermark)) {
      if (this.isSimpleWatermark(watermarkType)) {
        return previousWatermark + deltaForNextWatermark;
      } else {
        return Long.parseLong(Utils.dateToString(
            Utils.addSecondsToDate(Utils.toDate(previousWatermark, WATERMARKTIMEFORMAT), deltaForNextWatermark),
            WATERMARKTIMEFORMAT));
      }
    } else {
      LOG.info("Overriding low water mark with start value: " + ConfigurationKeys.SOURCE_QUERYBASED_START_VALUE);
      return Utils.getLongWithCurrentDate(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_START_VALUE));
    }
  }

  /**
   * Get high water mark
   *
   * @param Extract type
   * @param Watermark type
   * @return high water mark
   */
  private long getHighWatermark(ExtractType extractType, WatermarkType watermarkType) {
    LOG.debug("Getting high watermark");
    long highWatermark = ConfigurationKeys.DEFAULT_WATERMARK_VALUE;
    if (this.isWatermarkOverride()) {
      highWatermark = Utils.getAsLong(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_END_VALUE));
      LOG.info("Overriding high water mark with the given end value:" + highWatermark);
    } else {
      if (this.isSnapshot(extractType)) {
        highWatermark = this.getSnapshotHighWatermark(watermarkType);
      } else {
        highWatermark = this.getAppendHighWatermark(extractType);
      }
    }
    return (highWatermark == 0 ? ConfigurationKeys.DEFAULT_WATERMARK_VALUE : highWatermark);
  }

  /**
   * Get snapshot high water mark
   *
   * @param Watermark type
   * @return snapshot high water mark
   */
  private long getSnapshotHighWatermark(WatermarkType watermarkType) {
    LOG.debug("Getting snapshot high water mark");
    if (this.isSimpleWatermark(watermarkType)) {
      return ConfigurationKeys.DEFAULT_WATERMARK_VALUE;
    }
    return Long.parseLong(Utils.dateToString(this.getCurrentTime(), WATERMARKTIMEFORMAT));
  }

  /**
   * Get append high water mark
   *
   * @param Extract type
   * @return append high water mark
   */
  private long getAppendHighWatermark(ExtractType extractType) {
    LOG.debug("Getting append high water mark");
    if (this.isFullDump()) {
      LOG.info("Overriding high water mark with end value:" + ConfigurationKeys.SOURCE_QUERYBASED_END_VALUE);
      return Utils.getAsLong(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_END_VALUE));
    } else {
      return this.getAppendWatermarkCutoff(extractType);
    }
  }

  /**
   * Get cutoff for high water mark
   *
   * @param Extract type
   * @return cutoff
   */
  private long getAppendWatermarkCutoff(ExtractType extractType) {
    LOG.debug("Getting append water mark cutoff");
    long highWatermark = ConfigurationKeys.DEFAULT_WATERMARK_VALUE;
    AppendMaxLimitType limitType = this.getAppendLimitType(extractType,
        this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_APPEND_MAX_WATERMARK_LIMIT));
    if (limitType == null) {
      LOG.debug("Limit type is not found");
      return highWatermark;
    }
    int limitDelta =
        this.getAppendLimitDelta(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_APPEND_MAX_WATERMARK_LIMIT));
    // if it is CURRENTDATE or CURRENTHOUR then high water mark is current time
    if (limitDelta == 0) {
      highWatermark = Long.parseLong(Utils.dateToString(this.getCurrentTime(), WATERMARKTIMEFORMAT));
    }
    // if CURRENTDATE or CURRENTHOUR has offset then high water mark is end of day of the given offset
    else {
      int seconds = 3599; // x:59:59
      Date previousTime = null;

      switch (limitType) {
        case CURRENTDATE:
          previousTime = Utils.toDate(Utils.addHoursToDate(this.getCurrentTime(), limitDelta * 24 * -1), "yyyyMMdd");
          seconds = 86399; // 23:59:59
          break;
        case CURRENTHOUR:
          previousTime = Utils.toDate(Utils.addHoursToDate(this.getCurrentTime(), limitDelta * -1), "yyyyMMddHH");
          seconds = 3599; // x:59:59
          break;
      }

      Date previousEndOfDate = Utils.addSecondsToDate(previousTime, seconds);
      highWatermark = Long.parseLong(Utils.dateToString(previousEndOfDate, WATERMARKTIMEFORMAT));
    }
    return highWatermark;
  }

  /**
   * Get append max limit type from the input
   *
   * @param Extract type
   * @param maxLimit
   * @return Max limit type
   */
  private AppendMaxLimitType getAppendLimitType(ExtractType extractType, String maxLimit) {
    LOG.debug("Getting append limit type");
    AppendMaxLimitType limitType;
    switch (extractType) {
      case APPEND_DAILY:
        limitType = AppendMaxLimitType.CURRENTDATE;
        break;
      case APPEND_HOURLY:
        limitType = AppendMaxLimitType.CURRENTHOUR;
        break;
      default:
        limitType = null;
        break;
    }

    if (!Strings.isNullOrEmpty(maxLimit)) {
      LOG.debug("Getting append limit type from the config");
      String[] limitParams = maxLimit.split("-");
      if (limitParams.length >= 1) {
        limitType = AppendMaxLimitType.valueOf(limitParams[0]);
      }
    }
    return limitType;
  }

  /**
   * Get append max limit delta num
   *
   * @param maxLimit
   * @return Max limit delta number
   */
  private int getAppendLimitDelta(String maxLimit) {
    LOG.debug("Getting append limit delta");
    int limitDelta = 0;
    if (!Strings.isNullOrEmpty(maxLimit)) {
      String[] limitParams = maxLimit.split("-");
      if (limitParams.length >= 2) {
        limitDelta = Integer.parseInt(limitParams[1]);
      }
    }
    return limitDelta;
  }

  /**
   * Get current time for the given time zone. If time zone is not provided then consider America/Los_Angeles as default time zone.
   *
   * @return current time
   */
  private Date getCurrentTime() {
    String timezone = this.state.getProp(ConfigurationKeys.SOURCE_TIMEZONE);
    if (Strings.isNullOrEmpty(timezone)) {
      timezone = "America/Los_Angeles";
    }

    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    fmt.setTimeZone(TimeZone.getTimeZone(timezone));

    Date currentTime = null;
    try {
      currentTime = DATETIMEFORMAT.parse(fmt.format(new Date()));
    } catch (ParseException e) {
      LOG.error(e.getMessage(), e);
    }
    return currentTime;
  }

  /**
   * true if previous water mark equals default water mark
   *
   * @param previous water mark
   * @return true if previous water mark exists
   */
  private boolean isPreviousWatermarkExists(long previousWatermark) {
    if (!(previousWatermark == ConfigurationKeys.DEFAULT_WATERMARK_VALUE)) {
      return true;
    }
    return false;
  }

  /**
   * true if water mark columns and water mark type provided
   *
   * @return true if water mark exists
   */
  private boolean isWatermarkExists() {
    if (!Strings.isNullOrEmpty(this.state.getProp(ConfigurationKeys.EXTRACT_DELTA_FIELDS_KEY)) && !Strings
        .isNullOrEmpty(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_WATERMARK_TYPE))) {
      return true;
    }
    return false;
  }

  private boolean isSnapshot(ExtractType extractType) {
    if (extractType == ExtractType.SNAPSHOT) {
      return true;
    }
    return false;
  }

  private boolean isSimpleWatermark(WatermarkType watermarkType) {
    if (watermarkType == WatermarkType.SIMPLE) {
      return true;
    }
    return false;
  }

  /**
   * @return full dump or not
   */
  public boolean isFullDump() {
    return Boolean.valueOf(this.state.getProp(ConfigurationKeys.EXTRACT_IS_FULL_KEY));
  }

  /**
   * @return full dump or not
   */
  public boolean isWatermarkOverride() {
    return Boolean.valueOf(this.state.getProp(ConfigurationKeys.SOURCE_QUERYBASED_IS_WATERMARK_OVERRIDE));
  }
}
