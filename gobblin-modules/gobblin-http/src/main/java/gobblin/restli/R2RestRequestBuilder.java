package gobblin.restli;

import java.io.IOException;
import java.net.URI;
import java.util.Queue;

import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.data.DataMap;
import com.linkedin.data.codec.JacksonDataCodec;
import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.r2.message.rest.RestRequestBuilder;
import com.linkedin.restli.common.HttpMethod;
import com.linkedin.restli.common.ResourceMethod;
import com.linkedin.restli.common.RestConstants;

import gobblin.utils.HttpConstants;
import gobblin.utils.HttpUtils;
import gobblin.writer.http.AsyncWriteRequest;
import gobblin.writer.http.AsyncWriteRequestBuilder;
import gobblin.writer.http.BufferedRecord;


/**
 * Build {@link RestRequest} that can talk to restli services
 *
 * <p>
 *   This basic implementation builds a write request from a single record.
 * </p>
 */
public class R2RestRequestBuilder implements AsyncWriteRequestBuilder<GenericRecord, RestRequest> {
  private static final Logger LOG = LoggerFactory.getLogger(R2RestRequestBuilder.class);
  private static final JacksonDataCodec JACKSON_DATA_CODEC = new JacksonDataCodec();

  private final String urlTemplate;
  private final ResourceMethod method;
  private final String protocolVersion;

  public R2RestRequestBuilder(String urlTemplate, String verb, String protocolVersion) {
    this.urlTemplate = urlTemplate;
    method = ResourceMethod.fromString(verb);
    this.protocolVersion = protocolVersion;
  }

  @Override
  public AsyncWriteRequest<GenericRecord, RestRequest> buildWriteRequest(Queue<BufferedRecord<GenericRecord>> buffer) {
    return buildWriteRequest(buffer.poll());
  }

  /**
   * Build a request from a single record
   */
  private AsyncWriteRequest<GenericRecord, RestRequest> buildWriteRequest(BufferedRecord<GenericRecord> record) {
    if (record == null) {
      return null;
    }

    AsyncWriteRequest<GenericRecord, RestRequest> request = new AsyncWriteRequest<>();
    GenericRecord httpOperation = record.getRecord();
    // Set uri
    URI uri = HttpUtils.buildURI(urlTemplate, HttpUtils.toStringMap(httpOperation.get(HttpConstants.KEYS)),
        HttpUtils.toStringMap(httpOperation.get(HttpConstants.QUERY_PARAMS)));
    if (uri == null) {
      return null;
    }

    RestRequestBuilder builder = new RestRequestBuilder(uri).setMethod(method.getHttpMethod().toString());
    // Set headers
    builder.setHeaders(HttpUtils.toStringMap(httpOperation.get(HttpConstants.HEADERS)));
    builder.setHeader(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION, protocolVersion);
    if (method.getHttpMethod() == HttpMethod.POST) {
      builder.setHeader(RestConstants.HEADER_RESTLI_REQUEST_METHOD, method.toString());
    }

    // Add payload
    int bytesWritten = addPayload(builder, httpOperation.get(HttpConstants.BODY).toString());
    if (bytesWritten == -1) {
      return null;
    }

    request.markRecord(record, bytesWritten);
    request.setRawRequest(builder.build());
    return request;
  }

  /**
   * Add payload to request. By default, payload is sent as application/json
   */
  protected int addPayload(RestRequestBuilder builder, String payload) {
    if (payload == null || payload.length() == 0) {
      return 0;
    }

    builder.setHeader(RestConstants.HEADER_CONTENT_TYPE, RestConstants.HEADER_VALUE_APPLICATION_JSON);
    DataMap data = new DataMap(HttpUtils.toMap(payload));
    try {
      byte[] bytes = JACKSON_DATA_CODEC.mapToBytes(data);
      builder.setEntity(bytes);
      return bytes.length;
    } catch (IOException e) {
      LOG.error("Fail to convert payload: " + payload, e);
      return -1;
    }
  }
}
