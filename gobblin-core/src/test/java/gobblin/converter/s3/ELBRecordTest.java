/* (c) 2015 NerdWallet All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package gobblin.converter.s3;

import gobblin.converter.DataConversionException;
import gobblin.converter.string.StringToCSVConverter;
import java.io.IOException;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;


/**
 * Tests for {@link ELBRecord}
 *
 * @author ahollenbach@nerdwallet.com
 */
@Test(groups = {"gobblin.converter.s3"})
public class ELBRecordTest {
  /**
   * Generates an ELB record from a CSV string. Uses {@link StringToCSVConverter#splitString(String, char)} and
   * passes the result to make a new {@link ELBRecord}
   *
   * @param recordString A CSV string containing ELB log data
   * @return An {@link ELBRecord} that best represents the string passed in
   * @throws DataConversionException
   */
  public static ELBRecord generateELBRecord(String recordString)
      throws DataConversionException {
    try {
      return new ELBRecord(StringToCSVConverter.splitString(recordString, '\u0020'));
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * A simple use case test.
   */
  @Test
  public void testParseRequestStringSimple()
      throws DataConversionException {
    String requestString = "\"POST http://example.url.com:80/example/path HTTP/1.1\"";
    String elbRecordString = "2015-05-13T23:39:43.945958Z my-loadbalancer 192.168.131.39:2817 10.0.0.1:80 0.000073 0"
        + ".001048 0.000057 200 200 0 29 " + requestString + " \"curl/7.38.0\" - -";
    ELBRecord elbRecord = generateELBRecord(elbRecordString);

    Assert.assertEquals(elbRecord.getRequestMethod(), "POST");
    Assert.assertEquals(elbRecord.getRequestProtocol(), "http");
    Assert.assertEquals(elbRecord.getRequestHostHeader(), "example.url.com");
    Assert.assertEquals(elbRecord.getRequestPort(), 80);
    Assert.assertEquals(elbRecord.getRequestPath(), "example/path");
    Assert.assertEquals(elbRecord.getRequestHttpVersion(), "HTTP/1.1");
  }

  /**
   * Tests if a path ending in a file ending (.html for example) works
   */
  @Test
  public void testParseRequestStringFile()
      throws DataConversionException {
    String requestString = "\"POST http://example.url.com:80/example/path.html HTTP/1.1\"";
    String elbRecordString = "2015-05-13T23:39:43.945958Z my-loadbalancer 192.168.131.39:2817 10.0.0.1:80 0.000073 0"
        + ".001048 0.000057 200 200 0 29 " + requestString + " \"curl/7.38.0\" - -";
    ELBRecord elbRecord = generateELBRecord(elbRecordString);

    Assert.assertEquals(elbRecord.getRequestMethod(), "POST");
    Assert.assertEquals(elbRecord.getRequestProtocol(), "http");
    Assert.assertEquals(elbRecord.getRequestHostHeader(), "example.url.com");
    Assert.assertEquals(elbRecord.getRequestPort(), 80);
    Assert.assertEquals(elbRecord.getRequestPath(), "example/path.html");
    Assert.assertEquals(elbRecord.getRequestHttpVersion(), "HTTP/1.1");
  }

  /**
   * Tests if the TCP input for the request string ("- - - ") fails
   */
  @Test
  public void testParseRequestStringTcpIn()
      throws DataConversionException {
    String requestString = "\"- - - \"";
    String elbRecordString = "2015-05-13T23:39:43.945958Z my-loadbalancer 192.168.131.39:2817 10.0.0.1:80 0.000073 0"
        + ".001048 0.000057 200 200 0 29 " + requestString + " \"curl/7.38.0\" - -";
    ELBRecord elbRecord = generateELBRecord(elbRecordString);

    Assert.assertNull(elbRecord.getRequestMethod());
    Assert.assertNull(elbRecord.getRequestProtocol());
    Assert.assertNull(elbRecord.getRequestHostHeader());
    Assert.assertEquals(elbRecord.getRequestPort(), 0);
    Assert.assertNull(elbRecord.getRequestPath());
    Assert.assertNull(elbRecord.getRequestHttpVersion());
  }
}
