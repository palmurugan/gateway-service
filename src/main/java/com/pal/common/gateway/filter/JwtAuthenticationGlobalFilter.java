package com.pal.common.gateway.filter;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Log4j2
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

  private static final List<String> PUBLIC_PATHS = List.of(
      "/api/public",
      "/actuator/health",
      "/actuator/info"
  );
  private final ReactiveJwtDecoder jwtDecoder;

  public JwtAuthenticationGlobalFilter(ReactiveJwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    log.info("Global filter for enhancing the request with user information");

    String path = exchange.getRequest().getURI().getPath();

    // Skip authentication for public paths
    if (isPublicPath(path)) {
      return chain.filter(exchange);
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }

    String token = authHeader.substring(7);
    log.info("JWT Token: {}", token);
    return jwtDecoder.decode(token)
        .flatMap(jwt -> {
          // Add user information to downstream services
          ServerWebExchange modifiedExchange = exchange.mutate()
              .request(builder -> builder
                  .header("X-User-Id", jwt.getSubject())
                  .header("X-User-Email", jwt.getClaimAsString("email"))
                  .header("X-User-Name", jwt.getClaimAsString("preferred_username"))
                  .header("X-User-Roles", String.join(",", extractRoles(jwt)))
              )
              .build();

          return chain.filter(modifiedExchange);
        })
        .onErrorResume(throwable -> {
          exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
          return exchange.getResponse().setComplete();
        });
  }

  private boolean isPublicPath(String path) {
    return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
  }

  private List<String> extractRoles(Jwt jwt) {
    // Extract roles from realm_access
    var realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess != null && realmAccess.containsKey("roles")) {
      return (List<String>) realmAccess.get("roles");
    }
    return List.of();
  }

  @Override
  public int getOrder() {
    return -1;
  }
}
