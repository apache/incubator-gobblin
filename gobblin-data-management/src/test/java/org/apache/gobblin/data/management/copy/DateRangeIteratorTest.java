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

package org.apache.gobblin.data.management.copy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


public class DateRangeIteratorTest {

  @Test
  public void testIterator() {
    LocalDateTime endDate = LocalDateTime.of(2017, 1, 1, 0, 0, 0);
    LocalDateTime startDate = endDate.minusHours(2);
    String datePattern = "HH/yyyy/MM/dd";
    DateTimeFormatter format = DateTimeFormatter.ofPattern(datePattern);
    TimeAwareRecursiveCopyableDataset.DateRangeIterator dateRangeIterator =
        new TimeAwareRecursiveCopyableDataset.DateRangeIterator(startDate, endDate, datePattern);
    LocalDateTime dateTime = dateRangeIterator.next();
    Assert.assertEquals(dateTime.format(format), "22/2016/12/31");
    dateTime = dateRangeIterator.next();
    Assert.assertEquals(dateTime.format(format), "23/2016/12/31");
    dateTime = dateRangeIterator.next();
    Assert.assertEquals(dateTime.format(format), "00/2017/01/01");
    Assert.assertEquals(dateRangeIterator.hasNext(), false);

    datePattern = "yyyy/MM/dd";
    format = DateTimeFormatter.ofPattern(datePattern);
    startDate = endDate.minusDays(1);
    dateRangeIterator = new TimeAwareRecursiveCopyableDataset.DateRangeIterator(startDate, endDate, datePattern);
    dateTime = dateRangeIterator.next();
    Assert.assertEquals(dateTime.format(format), "2016/12/31");
    dateTime = dateRangeIterator.next();
    Assert.assertEquals(dateTime.format(format), "2017/01/01");
    Assert.assertEquals(dateRangeIterator.hasNext(), false);
  }
}