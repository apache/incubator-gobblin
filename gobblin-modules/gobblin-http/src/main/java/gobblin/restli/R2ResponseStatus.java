package gobblin.restli;

import com.linkedin.data.ByteString;

import lombok.Getter;
import lombok.Setter;

import gobblin.http.ResponseStatus;
import gobblin.http.StatusType;

@Getter @Setter
public class R2ResponseStatus extends ResponseStatus {
  private ByteString content = null;
  private String contentType = null;
  public R2ResponseStatus(StatusType type) {
    super(type);
  }
}
