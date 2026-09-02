package com.navio.apigateway;

import org.springframework.context.annotation.Configuration;

/**
 * Gateway wiring that is not route configuration.
 *
 * <p>Routes are defined in {@code configuration-server/.../config/api-gateway.yml}
 * under {@code spring.cloud.gateway.server.webflux.routes}. They deliberately
 * live in one place: a Java {@code RouteLocator} bean and the YAML route list
 * are both active at once, so defining a route in both produces duplicate
 * entries whose ordering depends on bean initialisation.
 *
 * <p>Routes are also listed explicitly rather than relying on discovery-based
 * auto-routing, which would publish every Eureka-registered service — including
 * internal-only paths such as the mobility service's {@code /internal/**} —
 * as soon as it registered.
 *
 * <p>Authentication for every route is configured in
 * {@code com.navio.apigateway.security.GatewaySecurityConfig}.
 */
@Configuration
public class ApiGatewayConfig {
}
