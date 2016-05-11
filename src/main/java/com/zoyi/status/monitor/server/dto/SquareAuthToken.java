package com.zoyi.status.monitor.server.dto;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;


/**
 * Created by lloyd on 2016-05-11
 */
@RequiredArgsConstructor(staticName = "of")
@ToString
public class SquareAuthToken {
  @NonNull @Getter private String authToken;
  @NonNull @Getter private long ts;


  public String getTsString() {
    return Long.toString(getTs());
  }
}
