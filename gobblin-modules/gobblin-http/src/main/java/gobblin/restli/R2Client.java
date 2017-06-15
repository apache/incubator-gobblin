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

package gobblin.restli;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.common.callback.Callbacks;
import com.linkedin.common.util.None;
import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.r2.message.rest.RestResponse;
import com.linkedin.r2.transport.common.Client;
import com.typesafe.config.Config;

import gobblin.broker.iface.NotConfiguredException;
import gobblin.broker.iface.SharedResourcesBroker;
import gobblin.http.ThrottledHttpClient;


public class R2Client extends ThrottledHttpClient<RestRequest, RestResponse> {
  private final Client client;

  public R2Client(Client client, SharedResourcesBroker broker) {
    super (broker, getLimiterKey());
    this.client = client;
  }

  @Override
  public RestResponse sendRequestImpl(RestRequest request)
      throws IOException {
    Future<RestResponse> responseFuture = client.restRequest(request);
    RestResponse response;
    try {
      response = responseFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
    return response;
  }

  @Override
  public void close()
      throws IOException {
    client.shutdown(Callbacks.<None>empty());
  }

  private static String getLimiterKey () {
    return "D2request/" + "serviceName";
  }
}
