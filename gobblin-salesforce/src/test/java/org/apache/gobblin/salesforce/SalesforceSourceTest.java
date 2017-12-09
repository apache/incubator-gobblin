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
package org.apache.gobblin.salesforce;

import org.testng.Assert;
import org.testng.annotations.Test;


public class SalesforceSourceTest {
  @Test
  void testGenerateSpecifiedPartition() {
    SalesforceSource.Histogram histogram = new SalesforceSource.Histogram();
    for (String group: HISTOGRAM.split(", ")) {
      String[] groupInfo = group.split("::");
      histogram.add(new SalesforceSource.HistogramGroup(groupInfo[0], Integer.parseInt(groupInfo[1])));
    }
    SalesforceSource source = new SalesforceSource();

    long expectedHighWatermark = 20170407152123L;
    long lowWatermark = 20140213000000L;
    int maxPartitions = 5;
    String expectedPartitions = "20140213000000,20170224000000,20170228000000,20170301000000,20170407000000,20170407152123";
    String actualPartitions = source.generateSpecifiedPartitions(histogram, 1, maxPartitions, lowWatermark, expectedHighWatermark);
    Assert.assertEquals(actualPartitions, expectedPartitions);
  }

  static final String HISTOGRAM = "2014-02-13-00:00:00::3, 2014-04-15-00:00:00::1, 2014-05-06-00:00:00::624, 2014-05-07-00:00:00::1497, 2014-05-08-00:00:00::10, 2014-05-18-00:00:00::3, 2014-05-19-00:00:00::2, 2014-05-20-00:00:00::1, 2014-05-21-00:00:00::8, 2014-05-26-00:00:00::2, 2014-05-28-00:00:00::1, 2014-05-31-00:00:00::1, 2014-06-02-00:00:00::1, 2014-06-03-00:00:00::1, 2014-06-04-00:00:00::1, 2014-06-10-00:00:00::2, 2014-06-12-00:00:00::1, 2014-06-23-00:00:00::1, 2014-06-24-00:00:00::1, 2014-06-26-00:00:00::32, 2014-06-27-00:00:00::40, 2014-06-30-00:00:00::2, 2014-07-01-00:00:00::2, 2014-07-02-00:00:00::1, 2014-07-07-00:00:00::1, 2014-07-08-00:00:00::2, 2014-07-09-00:00:00::2, 2014-07-10-00:00:00::3, 2014-07-11-00:00:00::5, 2014-07-14-00:00:00::1, 2014-07-15-00:00:00::2, 2014-07-16-00:00:00::8, 2014-07-17-00:00:00::5, 2014-07-18-00:00:00::2, 2014-07-21-00:00:00::1, 2014-07-22-00:00:00::3, 2014-07-23-00:00:00::3, 2014-07-24-00:00:00::1, 2014-07-25-00:00:00::1, 2014-07-26-00:00:00::103, 2014-07-28-00:00:00::1, 2014-07-29-00:00:00::1, 2014-07-30-00:00:00::3, 2014-08-01-00:00:00::1, 2014-08-06-00:00:00::3, 2014-08-18-00:00:00::3, 2014-08-19-00:00:00::2, 2014-08-21-00:00:00::1, 2014-08-25-00:00:00::1, 2014-08-26-00:00:00::1, 2014-08-27-00:00:00::2, 2014-08-28-00:00:00::2, 2014-08-29-00:00:00::1, 2014-09-03-00:00:00::2, 2014-09-04-00:00:00::1, 2014-09-05-00:00:00::9, 2014-09-09-00:00:00::1, 2014-09-10-00:00:00::1, 2014-09-11-00:00:00::4, 2014-09-12-00:00:00::1, 2014-09-15-00:00:00::1, 2014-09-16-00:00:00::7, 2014-09-18-00:00:00::3, 2014-09-19-00:00:00::2, 2014-09-21-00:00:00::1, 2014-09-24-00:00:00::3, 2014-09-26-00:00:00::1, 2014-09-29-00:00:00::3, 2014-10-03-00:00:00::1, 2014-10-06-00:00:00::1, 2014-10-07-00:00:00::1, 2014-10-09-00:00:00::1, 2014-10-16-00:00:00::1, 2014-10-23-00:00:00::1, 2014-10-24-00:00:00::1, 2014-10-28-00:00:00::1, 2014-11-03-00:00:00::1, 2014-11-05-00:00:00::2, 2014-11-07-00:00:00::1, 2014-11-10-00:00:00::5, 2014-11-12-00:00:00::1, 2014-11-13-00:00:00::4, 2014-11-18-00:00:00::1, 2014-11-24-00:00:00::1, 2014-11-25-00:00:00::1, 2014-11-26-00:00:00::2, 2014-11-27-00:00:00::2, 2014-11-28-00:00:00::1, 2014-12-01-00:00:00::2, 2014-12-02-00:00:00::3, 2014-12-03-00:00:00::5, 2014-12-04-00:00:00::1, 2014-12-08-00:00:00::1, 2014-12-09-00:00:00::3, 2014-12-12-00:00:00::3, 2014-12-14-00:00:00::1, 2014-12-15-00:00:00::4, 2014-12-16-00:00:00::1, 2014-12-17-00:00:00::2, 2014-12-19-00:00:00::2, 2014-12-22-00:00:00::1, 2014-12-23-00:00:00::3, 2014-12-24-00:00:00::1, 2014-12-30-00:00:00::1, 2014-12-31-00:00:00::1, 2015-01-02-00:00:00::1, 2015-01-04-00:00:00::1, 2015-01-05-00:00:00::1, 2015-01-06-00:00:00::1, 2015-01-07-00:00:00::1, 2015-01-08-00:00:00::2, 2015-01-09-00:00:00::2, 2015-01-14-00:00:00::1, 2015-01-15-00:00:00::2, 2015-01-16-00:00:00::15685, 2015-01-19-00:00:00::1, 2015-01-21-00:00:00::1, 2015-01-22-00:00:00::1, 2015-01-27-00:00:00::2, 2015-01-28-00:00:00::4, 2015-01-29-00:00:00::1, 2015-01-30-00:00:00::1, 2015-02-01-00:00:00::1, 2015-02-02-00:00:00::4, 2015-02-05-00:00:00::11, 2015-02-06-00:00:00::3, 2015-02-09-00:00:00::2, 2015-02-10-00:00:00::1, 2015-02-11-00:00:00::2, 2015-02-12-00:00:00::15, 2015-02-13-00:00:00::4, 2015-02-14-00:00:00::3, 2015-02-15-00:00:00::2, 2015-02-16-00:00:00::2, 2015-02-18-00:00:00::8, 2015-02-19-00:00:00::2, 2015-02-20-00:00:00::4, 2015-02-21-00:00:00::1, 2015-02-22-00:00:00::4, 2015-02-23-00:00:00::1, 2015-02-24-00:00:00::4, 2015-02-25-00:00:00::1, 2015-02-26-00:00:00::3, 2015-02-27-00:00:00::3, 2015-03-02-00:00:00::5, 2015-03-03-00:00:00::5, 2015-03-04-00:00:00::21, 2015-03-05-00:00:00::2, 2015-03-06-00:00:00::5, 2015-03-07-00:00:00::5, 2015-03-09-00:00:00::9, 2015-03-10-00:00:00::2050, 2015-03-11-00:00:00::13, 2015-03-12-00:00:00::3035, 2015-03-13-00:00:00::1, 2015-03-16-00:00:00::10, 2015-03-17-00:00:00::2, 2015-03-18-00:00:00::1, 2015-03-19-00:00:00::10, 2015-03-20-00:00:00::4, 2015-03-23-00:00:00::281, 2015-03-24-00:00:00::60, 2015-03-25-00:00:00::30, 2015-03-26-00:00:00::7, 2015-03-27-00:00:00::10, 2015-03-29-00:00:00::1, 2015-03-30-00:00:00::1, 2015-03-31-00:00:00::3, 2015-04-01-00:00:00::5, 2015-04-02-00:00:00::2, 2015-04-03-00:00:00::4, 2015-04-05-00:00:00::3, 2015-04-06-00:00:00::5, 2015-04-07-00:00:00::16, 2015-04-08-00:00:00::6, 2015-04-09-00:00:00::8, 2015-04-10-00:00:00::2, 2015-04-11-00:00:00::1, 2015-04-12-00:00:00::1, 2015-04-13-00:00:00::2, 2015-04-14-00:00:00::1, 2015-04-15-00:00:00::3, 2015-04-16-00:00:00::3, 2015-04-17-00:00:00::1, 2015-04-18-00:00:00::2, 2015-04-20-00:00:00::10, 2015-04-21-00:00:00::2, 2015-04-22-00:00:00::20, 2015-04-23-00:00:00::49, 2015-04-24-00:00:00::1, 2015-04-25-00:00:00::3, 2015-04-27-00:00:00::8, 2015-04-28-00:00:00::115, 2015-04-29-00:00:00::120, 2015-04-30-00:00:00::397, 2015-05-01-00:00:00::4, 2015-05-04-00:00:00::605, 2015-05-05-00:00:00::3, 2015-05-06-00:00:00::7, 2015-05-07-00:00:00::14, 2015-05-08-00:00:00::1, 2015-05-09-00:00:00::2, 2015-05-11-00:00:00::5, 2015-05-12-00:00:00::5, 2015-05-13-00:00:00::2, 2015-05-14-00:00:00::3, 2015-05-15-00:00:00::2, 2015-05-18-00:00:00::2, 2015-05-19-00:00:00::3, 2015-05-21-00:00:00::4, 2015-05-22-00:00:00::4, 2015-05-25-00:00:00::1, 2015-05-26-00:00:00::2, 2015-05-27-00:00:00::2, 2015-05-28-00:00:00::3, 2015-05-29-00:00:00::20, 2015-05-30-00:00:00::1, 2015-06-01-00:00:00::1, 2015-06-02-00:00:00::11, 2015-06-03-00:00:00::2, 2015-06-04-00:00:00::2, 2015-06-08-00:00:00::3, 2015-06-09-00:00:00::9, 2015-06-10-00:00:00::3, 2015-06-11-00:00:00::3, 2015-06-13-00:00:00::2, 2015-06-15-00:00:00::11, 2015-06-16-00:00:00::7, 2015-06-17-00:00:00::2, 2015-06-18-00:00:00::1, 2015-06-19-00:00:00::1, 2015-06-22-00:00:00::4, 2015-06-23-00:00:00::1, 2015-06-24-00:00:00::12, 2015-06-25-00:00:00::13, 2015-06-26-00:00:00::9, 2015-06-28-00:00:00::1, 2015-06-29-00:00:00::655, 2015-06-30-00:00:00::5, 2015-07-01-00:00:00::30, 2015-07-02-00:00:00::7, 2015-07-03-00:00:00::3, 2015-07-04-00:00:00::1, 2015-07-05-00:00:00::1, 2015-07-06-00:00:00::10, 2015-07-07-00:00:00::18, 2015-07-08-00:00:00::2, 2015-07-09-00:00:00::17, 2015-07-10-00:00:00::5, 2015-07-11-00:00:00::1, 2015-07-13-00:00:00::6, 2015-07-14-00:00:00::9, 2015-07-15-00:00:00::3, 2015-07-16-00:00:00::21, 2015-07-17-00:00:00::3, 2015-07-18-00:00:00::1, 2015-07-20-00:00:00::92, 2015-07-22-00:00:00::4, 2015-07-23-00:00:00::5, 2015-07-24-00:00:00::5, 2015-07-25-00:00:00::1, 2015-07-27-00:00:00::7, 2015-07-28-00:00:00::19, 2015-07-30-00:00:00::10, 2015-07-31-00:00:00::6, 2015-08-01-00:00:00::1, 2015-08-03-00:00:00::1, 2015-08-04-00:00:00::4, 2015-08-05-00:00:00::17, 2015-08-06-00:00:00::4, 2015-08-07-00:00:00::3, 2015-08-08-00:00:00::2, 2015-08-10-00:00:00::6, 2015-08-11-00:00:00::2, 2015-08-12-00:00:00::4, 2015-08-13-00:00:00::2, 2015-08-14-00:00:00::1, 2015-08-16-00:00:00::3, 2015-08-17-00:00:00::4, 2015-08-18-00:00:00::2, 2015-08-19-00:00:00::4, 2015-08-20-00:00:00::216, 2015-08-21-00:00:00::6, 2015-08-24-00:00:00::5, 2015-08-25-00:00:00::4, 2015-08-26-00:00:00::384, 2015-08-27-00:00:00::3, 2015-08-28-00:00:00::2, 2015-08-31-00:00:00::5, 2015-09-01-00:00:00::7, 2015-09-02-00:00:00::2, 2015-09-03-00:00:00::9, 2015-09-06-00:00:00::1, 2015-09-07-00:00:00::2, 2015-09-08-00:00:00::25, 2015-09-09-00:00:00::4, 2015-09-10-00:00:00::5, 2015-09-11-00:00:00::3, 2015-09-12-00:00:00::1, 2015-09-13-00:00:00::1, 2015-09-14-00:00:00::4, 2015-09-15-00:00:00::45, 2015-09-16-00:00:00::2, 2015-09-17-00:00:00::2, 2015-09-18-00:00:00::2, 2015-09-21-00:00:00::205, 2015-09-22-00:00:00::88, 2015-09-23-00:00:00::23, 2015-09-24-00:00:00::37, 2015-09-25-00:00:00::7, 2015-09-28-00:00:00::5, 2015-09-29-00:00:00::35, 2015-09-30-00:00:00::24, 2015-10-01-00:00:00::16, 2015-10-02-00:00:00::31, 2015-10-04-00:00:00::1, 2015-10-05-00:00:00::5, 2015-10-06-00:00:00::1, 2015-10-07-00:00:00::10, 2015-10-08-00:00:00::9, 2015-10-09-00:00:00::8, 2015-10-10-00:00:00::219, 2015-10-11-00:00:00::77, 2015-10-12-00:00:00::191, 2015-10-13-00:00:00::9, 2015-10-14-00:00:00::23, 2015-10-15-00:00:00::103, 2015-10-16-00:00:00::52, 2015-10-17-00:00:00::1, 2015-10-18-00:00:00::5, 2015-10-19-00:00:00::4, 2015-10-20-00:00:00::822, 2015-10-21-00:00:00::34, 2015-10-22-00:00:00::41, 2015-10-23-00:00:00::2045, 2015-10-24-00:00:00::1, 2015-10-25-00:00:00::2, 2015-10-26-00:00:00::7, 2015-10-27-00:00:00::19, 2015-10-28-00:00:00::17, 2015-10-29-00:00:00::14, 2015-10-30-00:00:00::12, 2015-11-02-00:00:00::4, 2015-11-03-00:00:00::7, 2015-11-04-00:00:00::11, 2015-11-05-00:00:00::6, 2015-11-06-00:00:00::8, 2015-11-07-00:00:00::2, 2015-11-08-00:00:00::1, 2015-11-09-00:00:00::10, 2015-11-10-00:00:00::10, 2015-11-11-00:00:00::10, 2015-11-12-00:00:00::5, 2015-11-13-00:00:00::6, 2015-11-15-00:00:00::3, 2015-11-16-00:00:00::8, 2015-11-17-00:00:00::4, 2015-11-18-00:00:00::8, 2015-11-19-00:00:00::458, 2015-11-20-00:00:00::390, 2015-11-22-00:00:00::1, 2015-11-23-00:00:00::12, 2015-11-24-00:00:00::13, 2015-11-25-00:00:00::542, 2015-11-26-00:00:00::2, 2015-11-27-00:00:00::1, 2015-11-30-00:00:00::4, 2015-12-01-00:00:00::3, 2015-12-02-00:00:00::4, 2015-12-03-00:00:00::8, 2015-12-04-00:00:00::6, 2015-12-05-00:00:00::1, 2015-12-06-00:00:00::1, 2015-12-07-00:00:00::3, 2015-12-08-00:00:00::18, 2015-12-09-00:00:00::3, 2015-12-10-00:00:00::22, 2015-12-11-00:00:00::4, 2015-12-14-00:00:00::13, 2015-12-15-00:00:00::8, 2015-12-16-00:00:00::8, 2015-12-17-00:00:00::5, 2015-12-18-00:00:00::9, 2015-12-20-00:00:00::2, 2015-12-21-00:00:00::7, 2015-12-22-00:00:00::4, 2015-12-23-00:00:00::15, 2015-12-24-00:00:00::2, 2015-12-28-00:00:00::2, 2015-12-31-00:00:00::1, 2016-01-01-00:00:00::10, 2016-01-02-00:00:00::2, 2016-01-03-00:00:00::1, 2016-01-04-00:00:00::15, 2016-01-05-00:00:00::9, 2016-01-06-00:00:00::19, 2016-01-07-00:00:00::30, 2016-01-08-00:00:00::9711, 2016-01-09-00:00:00::9, 2016-01-10-00:00:00::9, 2016-01-11-00:00:00::20, 2016-01-12-00:00:00::14, 2016-01-13-00:00:00::3084, 2016-01-14-00:00:00::17, 2016-01-15-00:00:00::9, 2016-01-16-00:00:00::1, 2016-01-17-00:00:00::1, 2016-01-18-00:00:00::2, 2016-01-19-00:00:00::9, 2016-01-20-00:00:00::12, 2016-01-21-00:00:00::15, 2016-01-22-00:00:00::9, 2016-01-23-00:00:00::6, 2016-01-24-00:00:00::2, 2016-01-25-00:00:00::3, 2016-01-26-00:00:00::1, 2016-01-27-00:00:00::3, 2016-01-28-00:00:00::7, 2016-01-29-00:00:00::4, 2016-01-30-00:00:00::7, 2016-01-31-00:00:00::3, 2016-02-01-00:00:00::17, 2016-02-02-00:00:00::28, 2016-02-03-00:00:00::20, 2016-02-04-00:00:00::20, 2016-02-05-00:00:00::40, 2016-02-06-00:00:00::3, 2016-02-07-00:00:00::9, 2016-02-08-00:00:00::28, 2016-02-09-00:00:00::33, 2016-02-10-00:00:00::100, 2016-02-11-00:00:00::52, 2016-02-12-00:00:00::103, 2016-02-13-00:00:00::5, 2016-02-14-00:00:00::5, 2016-02-15-00:00:00::7, 2016-02-16-00:00:00::23, 2016-02-17-00:00:00::210, 2016-02-18-00:00:00::29, 2016-02-19-00:00:00::24, 2016-02-20-00:00:00::5, 2016-02-21-00:00:00::29, 2016-02-22-00:00:00::26, 2016-02-23-00:00:00::12, 2016-02-24-00:00:00::19, 2016-02-25-00:00:00::46, 2016-02-26-00:00:00::23, 2016-02-27-00:00:00::7, 2016-02-28-00:00:00::1, 2016-02-29-00:00:00::45, 2016-03-01-00:00:00::48, 2016-03-02-00:00:00::49, 2016-03-03-00:00:00::34, 2016-03-04-00:00:00::61, 2016-03-05-00:00:00::5, 2016-03-06-00:00:00::15, 2016-03-07-00:00:00::22, 2016-03-08-00:00:00::3649, 2016-03-09-00:00:00::49, 2016-03-10-00:00:00::39, 2016-03-11-00:00:00::67, 2016-03-12-00:00:00::3, 2016-03-13-00:00:00::1, 2016-03-14-00:00:00::31, 2016-03-15-00:00:00::31, 2016-03-16-00:00:00::37, 2016-03-17-00:00:00::43, 2016-03-18-00:00:00::47, 2016-03-19-00:00:00::5, 2016-03-20-00:00:00::14, 2016-03-21-00:00:00::47, 2016-03-22-00:00:00::31, 2016-03-23-00:00:00::41, 2016-03-24-00:00:00::38, 2016-03-25-00:00:00::36, 2016-03-26-00:00:00::5, 2016-03-27-00:00:00::3, 2016-03-28-00:00:00::42, 2016-03-29-00:00:00::43, 2016-03-30-00:00:00::68, 2016-03-31-00:00:00::35, 2016-04-01-00:00:00::26, 2016-04-02-00:00:00::1, 2016-04-03-00:00:00::2, 2016-04-04-00:00:00::66, 2016-04-05-00:00:00::35, 2016-04-06-00:00:00::26, 2016-04-07-00:00:00::25, 2016-04-08-00:00:00::31, 2016-04-09-00:00:00::2, 2016-04-10-00:00:00::1, 2016-04-11-00:00:00::27, 2016-04-12-00:00:00::35, 2016-04-13-00:00:00::5, 2016-04-14-00:00:00::185, 2016-04-15-00:00:00::121, 2016-04-16-00:00:00::83, 2016-04-18-00:00:00::9, 2016-04-19-00:00:00::1, 2016-04-20-00:00:00::53, 2016-04-21-00:00:00::1, 2016-04-22-00:00:00::95, 2016-04-23-00:00:00::126, 2016-04-24-00:00:00::3, 2016-04-25-00:00:00::1, 2016-04-26-00:00:00::202, 2016-04-27-00:00:00::58, 2016-04-28-00:00:00::64, 2016-04-29-00:00:00::54, 2016-04-30-00:00:00::139, 2016-05-01-00:00:00::2, 2016-05-02-00:00:00::4, 2016-05-03-00:00:00::46, 2016-05-04-00:00:00::108, 2016-05-05-00:00:00::34, 2016-05-06-00:00:00::24, 2016-05-07-00:00:00::7, 2016-05-08-00:00:00::3, 2016-05-09-00:00:00::44, 2016-05-10-00:00:00::28, 2016-05-11-00:00:00::44, 2016-05-12-00:00:00::58, 2016-05-13-00:00:00::40, 2016-05-14-00:00:00::2, 2016-05-15-00:00:00::1, 2016-05-16-00:00:00::29, 2016-05-17-00:00:00::16, 2016-05-18-00:00:00::31, 2016-05-19-00:00:00::58, 2016-05-20-00:00:00::59, 2016-05-21-00:00:00::14, 2016-05-22-00:00:00::16, 2016-05-23-00:00:00::68, 2016-05-24-00:00:00::19, 2016-05-25-00:00:00::55, 2016-05-26-00:00:00::78, 2016-05-27-00:00:00::64, 2016-05-28-00:00:00::76, 2016-05-29-00:00:00::2, 2016-05-30-00:00:00::15, 2016-05-31-00:00:00::24, 2016-06-01-00:00:00::11, 2016-06-02-00:00:00::31, 2016-06-03-00:00:00::39, 2016-06-04-00:00:00::2, 2016-06-06-00:00:00::783, 2016-06-07-00:00:00::14, 2016-06-08-00:00:00::51, 2016-06-09-00:00:00::25, 2016-06-10-00:00:00::14, 2016-06-12-00:00:00::2, 2016-06-13-00:00:00::57, 2016-06-14-00:00:00::20, 2016-06-15-00:00:00::36, 2016-06-16-00:00:00::15, 2016-06-17-00:00:00::49, 2016-06-18-00:00:00::3, 2016-06-20-00:00:00::38, 2016-06-21-00:00:00::45, 2016-06-22-00:00:00::5215, 2016-06-23-00:00:00::4977, 2016-06-24-00:00:00::50, 2016-06-25-00:00:00::2, 2016-06-26-00:00:00::2, 2016-06-27-00:00:00::27, 2016-06-28-00:00:00::1102, 2016-06-29-00:00:00::59, 2016-06-30-00:00:00::38, 2016-07-01-00:00:00::34, 2016-07-02-00:00:00::1, 2016-07-03-00:00:00::3, 2016-07-04-00:00:00::69, 2016-07-05-00:00:00::26, 2016-07-06-00:00:00::5, 2016-07-07-00:00:00::5, 2016-07-08-00:00:00::26, 2016-07-09-00:00:00::58, 2016-07-10-00:00:00::3, 2016-07-11-00:00:00::13, 2016-07-12-00:00:00::1, 2016-07-13-00:00:00::68, 2016-07-14-00:00:00::73, 2016-07-15-00:00:00::69, 2016-07-16-00:00:00::65, 2016-07-17-00:00:00::4, 2016-07-18-00:00:00::1, 2016-07-19-00:00:00::14, 2016-07-20-00:00:00::60, 2016-07-21-00:00:00::328, 2016-07-22-00:00:00::41, 2016-07-23-00:00:00::105, 2016-07-24-00:00:00::16, 2016-07-25-00:00:00::1, 2016-07-26-00:00:00::37, 2016-07-27-00:00:00::72, 2016-07-28-00:00:00::59, 2016-07-29-00:00:00::53, 2016-07-30-00:00:00::241, 2016-07-31-00:00:00::1, 2016-08-01-00:00:00::10, 2016-08-02-00:00:00::2, 2016-08-03-00:00:00::45, 2016-08-04-00:00:00::44, 2016-08-05-00:00:00::76, 2016-08-06-00:00:00::96, 2016-08-07-00:00:00::8, 2016-08-08-00:00:00::129, 2016-08-09-00:00:00::14, 2016-08-10-00:00:00::50, 2016-08-11-00:00:00::47, 2016-08-12-00:00:00::56, 2016-08-13-00:00:00::95, 2016-08-14-00:00:00::6, 2016-08-15-00:00:00::20, 2016-08-16-00:00:00::11, 2016-08-17-00:00:00::5, 2016-08-18-00:00:00::94, 2016-08-19-00:00:00::51, 2016-08-20-00:00:00::110, 2016-08-21-00:00:00::2, 2016-08-22-00:00:00::30, 2016-08-23-00:00:00::3, 2016-08-24-00:00:00::50, 2016-08-25-00:00:00::55, 2016-08-26-00:00:00::51, 2016-08-27-00:00:00::103, 2016-08-28-00:00:00::5, 2016-08-29-00:00:00::6, 2016-08-31-00:00:00::58, 2016-09-01-00:00:00::72, 2016-09-02-00:00:00::94, 2016-09-03-00:00:00::107, 2016-09-04-00:00:00::9, 2016-09-05-00:00:00::1, 2016-09-06-00:00:00::10, 2016-09-07-00:00:00::58, 2016-09-08-00:00:00::82, 2016-09-09-00:00:00::44, 2016-09-10-00:00:00::162, 2016-09-11-00:00:00::5, 2016-09-12-00:00:00::5, 2016-09-13-00:00:00::1, 2016-09-14-00:00:00::64, 2016-09-15-00:00:00::62, 2016-09-16-00:00:00::55, 2016-09-17-00:00:00::106, 2016-09-18-00:00:00::17, 2016-09-19-00:00:00::6, 2016-09-20-00:00:00::4, 2016-09-21-00:00:00::68, 2016-09-22-00:00:00::69, 2016-09-23-00:00:00::62, 2016-09-24-00:00:00::101, 2016-09-25-00:00:00::11, 2016-09-26-00:00:00::13, 2016-09-27-00:00:00::2, 2016-09-28-00:00:00::94, 2016-09-29-00:00:00::42, 2016-09-30-00:00:00::47, 2016-10-01-00:00:00::109, 2016-10-02-00:00:00::13, 2016-10-03-00:00:00::2, 2016-10-04-00:00:00::43, 2016-10-05-00:00:00::38, 2016-10-06-00:00:00::41, 2016-10-07-00:00:00::31, 2016-10-08-00:00:00::64, 2016-10-09-00:00:00::7, 2016-10-10-00:00:00::13, 2016-10-11-00:00:00::2, 2016-10-12-00:00:00::47, 2016-10-13-00:00:00::49, 2016-10-14-00:00:00::114, 2016-10-15-00:00:00::96, 2016-10-16-00:00:00::9, 2016-10-17-00:00:00::3, 2016-10-18-00:00:00::3, 2016-10-19-00:00:00::60, 2016-10-20-00:00:00::9, 2016-10-21-00:00:00::160, 2016-10-22-00:00:00::53, 2016-10-23-00:00:00::2, 2016-10-24-00:00:00::4, 2016-10-25-00:00:00::4, 2016-10-26-00:00:00::69, 2016-10-27-00:00:00::67, 2016-10-28-00:00:00::4, 2016-10-29-00:00:00::182, 2016-10-31-00:00:00::8, 2016-11-01-00:00:00::72, 2016-11-02-00:00:00::68, 2016-11-03-00:00:00::53, 2016-11-04-00:00:00::54, 2016-11-05-00:00:00::94, 2016-11-06-00:00:00::2, 2016-11-07-00:00:00::24, 2016-11-08-00:00:00::14, 2016-11-09-00:00:00::83, 2016-11-10-00:00:00::71, 2016-11-11-00:00:00::74, 2016-11-12-00:00:00::129, 2016-11-13-00:00:00::3, 2016-11-14-00:00:00::4, 2016-11-16-00:00:00::55, 2016-11-17-00:00:00::54, 2016-11-18-00:00:00::77, 2016-11-19-00:00:00::119, 2016-11-20-00:00:00::4, 2016-11-21-00:00:00::14, 2016-11-22-00:00:00::3, 2016-11-23-00:00:00::52, 2016-11-24-00:00:00::33, 2016-11-25-00:00:00::45, 2016-11-26-00:00:00::66, 2016-11-27-00:00:00::4, 2016-11-28-00:00:00::3, 2016-11-29-00:00:00::1, 2016-11-30-00:00:00::60, 2016-12-01-00:00:00::78, 2016-12-02-00:00:00::84, 2016-12-03-00:00:00::66, 2016-12-04-00:00:00::7, 2016-12-05-00:00:00::38, 2016-12-06-00:00:00::53, 2016-12-07-00:00:00::147, 2016-12-08-00:00:00::472, 2016-12-09-00:00:00::112, 2016-12-10-00:00:00::80, 2016-12-11-00:00:00::8, 2016-12-12-00:00:00::9, 2016-12-14-00:00:00::42, 2016-12-16-00:00:00::76, 2016-12-17-00:00:00::105, 2016-12-18-00:00:00::5, 2016-12-19-00:00:00::8, 2016-12-21-00:00:00::63, 2016-12-22-00:00:00::31, 2016-12-23-00:00:00::56, 2016-12-24-00:00:00::51, 2016-12-25-00:00:00::9, 2016-12-26-00:00:00::1, 2016-12-28-00:00:00::3, 2016-12-30-00:00:00::7, 2016-12-31-00:00:00::4, 2017-01-02-00:00:00::1, 2017-01-04-00:00:00::10, 2017-01-05-00:00:00::57, 2017-01-06-00:00:00::133, 2017-01-07-00:00:00::99, 2017-01-08-00:00:00::5, 2017-01-09-00:00:00::5, 2017-01-10-00:00:00::14, 2017-01-11-00:00:00::61, 2017-01-12-00:00:00::39, 2017-01-13-00:00:00::55, 2017-01-14-00:00:00::107, 2017-01-15-00:00:00::5, 2017-01-16-00:00:00::3, 2017-01-18-00:00:00::39, 2017-01-19-00:00:00::48, 2017-01-20-00:00:00::38, 2017-01-21-00:00:00::73, 2017-01-22-00:00:00::6, 2017-01-23-00:00:00::10, 2017-01-25-00:00:00::1, 2017-01-26-00:00:00::104, 2017-01-27-00:00:00::1, 2017-01-28-00:00:00::117, 2017-01-29-00:00:00::3, 2017-01-30-00:00:00::4, 2017-01-31-00:00:00::78, 2017-02-01-00:00:00::60, 2017-02-02-00:00:00::49, 2017-02-03-00:00:00::46, 2017-02-04-00:00:00::113, 2017-02-05-00:00:00::6, 2017-02-06-00:00:00::56, 2017-02-07-00:00:00::302, 2017-02-08-00:00:00::75, 2017-02-09-00:00:00::54, 2017-02-10-00:00:00::226, 2017-02-11-00:00:00::1039, 2017-02-13-00:00:00::29515, 2017-02-14-00:00:00::602, 2017-02-15-00:00:00::1099, 2017-02-16-00:00:00::97, 2017-02-17-00:00:00::1608, 2017-02-18-00:00:00::1258, 2017-02-19-00:00:00::109, 2017-02-20-00:00:00::116, 2017-02-21-00:00:00::336, 2017-02-22-00:00:00::664, 2017-02-23-00:00:00::154529, 2017-02-24-00:00:00::40521, 2017-02-25-00:00:00::2100, 2017-02-26-00:00:00::131, 2017-02-27-00:00:00::415645, 2017-02-28-00:00:00::276943, 2017-03-01-00:00:00::3306, 2017-03-02-00:00:00::3552, 2017-03-03-00:00:00::4227, 2017-03-04-00:00:00::6963, 2017-03-05-00:00:00::440, 2017-03-06-00:00:00::3365, 2017-03-07-00:00:00::389, 2017-03-08-00:00:00::3870, 2017-03-09-00:00:00::3867, 2017-03-10-00:00:00::4218, 2017-03-11-00:00:00::7977, 2017-03-12-00:00:00::391, 2017-03-13-00:00:00::27355, 2017-03-14-00:00:00::34284, 2017-03-15-00:00:00::14698, 2017-03-16-00:00:00::2926, 2017-03-17-00:00:00::5338, 2017-03-18-00:00:00::7752, 2017-03-19-00:00:00::510, 2017-03-20-00:00:00::7459, 2017-03-21-00:00:00::440, 2017-03-22-00:00:00::6040, 2017-03-23-00:00:00::6313, 2017-03-24-00:00:00::5792, 2017-03-25-00:00:00::9397, 2017-03-26-00:00:00::613, 2017-03-27-00:00:00::1365, 2017-03-28-00:00:00::7063, 2017-03-29-00:00:00::2110, 2017-03-30-00:00:00::8154, 2017-03-31-00:00:00::9777, 2017-04-01-00:00:00::16354, 2017-04-02-00:00:00::1288, 2017-04-03-00:00:00::1426, 2017-04-04-00:00:00::2666, 2017-04-05-00:00:00::13562, 2017-04-06-00:00:00::54698, 2017-04-07-00:00:00::22457";
}
