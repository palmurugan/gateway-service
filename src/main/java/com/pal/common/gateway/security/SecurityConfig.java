package com.pal.common.gateway.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity
@Log4j2
public class SecurityConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
  private String jwkSetUri;

  /**
   * Builds a SecurityWebFilterChain that:
   *   - Allows unauthenticated access to actuator endpoints
   *   - Requires authentication for all other endpoints
   *   - Uses JWT authentication
   *   - Disables CSRF protection
   *
   * @param http the ServerHttpSecurity object to configure
   * @return the configured SecurityWebFilterChain
   */
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    http
        .authorizeExchange(exchange -> exchange
            .pathMatchers("/actuator/**").permitAll()
            .anyExchange().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        )
        .csrf(ServerHttpSecurity.CsrfSpec::disable);

    return http.build();
  }

  @Bean
  public ReactiveJwtDecoder jwtDecoder() {
    return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
  }

  @Bean
  public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
    log.info("JWT Authentication Converter Invoked!");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

    // Extract roles from Keycloak token
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
      if (realmAccess != null && realmAccess.containsKey("roles")) {
        Collection<String> roles = (Collection<String>) realmAccess.get("roles");
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            .collect(Collectors.toList());
      }

      // Also check resource_access for client-specific roles
      Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
      if (resourceAccess != null) {
        return resourceAccess.values().stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .filter(clientAccess -> clientAccess.containsKey("roles"))
            .flatMap(clientAccess -> ((Collection<String>) clientAccess.get("roles")).stream())
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            .collect(Collectors.toList());
      }

      return List.of();
    });

    return new ReactiveJwtAuthenticationConverterAdapter(converter);
  }

}
