package com.pal.common.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfiguration {

  private final ServicesConfig servicesConfig;

  public GatewayConfiguration(ServicesConfig servicesConfig) {
    this.servicesConfig = servicesConfig;
  }

  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder routeLocatorBuilder) {
    var routes = routeLocatorBuilder.routes();

    servicesConfig.getServiceMap().forEach((serviceName, definition) -> {
      routes.route(serviceName, r -> {
        var spec = r.path("/" + serviceName + "/**")
            .filters(f -> {
              // Apply filters from YAML
              if (definition.getFilters() != null) {
                definition.getFilters().forEach(filter -> {
                  if (filter.startsWith("StripPrefix=")) {
                    int parts = Integer.parseInt(filter.split("=")[1]);
                    f.stripPrefix(parts);
                  }
                  // extend here with RewritePath, AddRequestParameter, etc.
                });
              }

              // Apply headers from YAML
              if (definition.getHeaders() != null) {
                definition.getHeaders().forEach(f::addRequestHeader);
              }

              return f;
            })
            .uri(definition.getUrl());

        return spec;
      });
    });
    return routes.build();
  }
}
