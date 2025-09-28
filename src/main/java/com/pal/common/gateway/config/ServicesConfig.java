package com.pal.common.gateway.config;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
@Data
public class ServicesConfig {

  private Map<String, ServiceDefinition> serviceMap;

  @Setter
  @Getter
  public static class ServiceDefinition {

    private String url;
    private List<String> filters;
    private Map<String, String> headers;
  }
}
