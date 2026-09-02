package com.navio.apigateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The gateway's trust boundary.
 *
 * <p>Replaces the previous {@code JwtAuthFilter}, which was disabled and whose
 * body was a {@code TODO} — it checked only that the header began with
 * {@code "Bearer "}, so any string of characters passed. Nothing in the system
 * verified a Keycloak signature.
 *
 * <p>Every request is now validated for signature, issuer, expiry, and audience
 * before {@link IdentityPropagationFilter} derives the downstream identity
 * headers from it.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /** Publicly reachable: health probes and the gateway's own liveness route. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/actuator/metrics/**"
    };

    private final String issuerUri;
    private final String jwkSetUri;
    private final List<String> audiences;
    private final Duration clockSkew;

    public GatewaySecurityConfig(
            @Value("${navio.security.keycloak.issuer-uri}") String issuerUri,
            @Value("${navio.security.keycloak.jwk-set-uri:}") String jwkSetUri,
            @Value("${navio.security.keycloak.audiences}") List<String> audiences,
            @Value("${navio.security.keycloak.clock-skew:30s}") Duration clockSkew) {
        this.issuerUri = issuerUri;
        this.jwkSetUri = jwkSetUri;
        this.audiences = List.copyOf(audiences);
        this.clockSkew = clockSkew;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         ReactiveJwtDecoder jwtDecoder) {
        return http
                // Bearer-token API: no cookies, so no ambient credential for a
                // cross-site request to ride, and nothing for CSRF to protect.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Deny by default: a newly routed service is protected
                        // even if nobody remembers to add a rule for it.
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(reactiveAuthoritiesConverter())))
                .build();
    }

    /**
     * Reactive decoder with the full validator chain.
     *
     * <p>Audience validation matters as much here as in the resource services:
     * without it, a token minted for any other client in the realm would pass the
     * gateway and be converted into trusted identity headers.
     *
     * <p>{@code jwk-set-uri} overrides where signing keys come from. Discovery on
     * the public issuer is a network call made while this bean is created, and in
     * the deployed stack that issuer resolves through NGINX, which cannot start
     * until this gateway is healthy. Pointing the key source at Keycloak directly
     * breaks that cycle. {@code iss} is still validated against the public issuer
     * below, so the set of accepted tokens is unchanged.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder decoder = jwkSetUri != null && !jwkSetUri.isBlank()
                ? NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : (NimbusReactiveJwtDecoder) ReactiveJwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> timestamps = new JwtTimestampValidator(clockSkew);
        OAuth2TokenValidator<Jwt> issuer = new JwtIssuerValidator(issuerUri);
        OAuth2TokenValidator<Jwt> audience = audienceValidator();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, issuer, audience));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator() {
        Set<String> accepted = Set.copyOf(audiences);
        return token -> {
            List<String> tokenAudiences = token.getAudience();
            if (tokenAudiences != null && tokenAudiences.stream().anyMatch(accepted::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN,
                    "The required audience is missing from the access token",
                    null));
        };
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveAuthoritiesConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter(audiences));
        converter.setPrincipalClaimName("sub");
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    /**
     * Maps Keycloak realm and client roles onto Spring authorities.
     *
     * <p>Filtered against a fixed allowlist so Keycloak's built-in roles
     * ({@code offline_access}, {@code uma_authorization}) never become Navio
     * authorities, and so a role added to the realm for some unrelated purpose
     * cannot silently grant access here.
     */
    static class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        private static final Set<String> NAVIO_ROLES = Set.of("USER", "MODERATOR", "ADMIN");
        private static final String REALM_ACCESS = "realm_access";
        private static final String RESOURCE_ACCESS = "resource_access";
        private static final String ROLES = "roles";

        private final List<String> trustedClientIds;

        KeycloakRealmRoleConverter(List<String> trustedClientIds) {
            this.trustedClientIds = List.copyOf(trustedClientIds);
        }

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            addRoles(authorities, jwt.getClaimAsMap(REALM_ACCESS));

            Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS);
            if (resourceAccess != null) {
                for (String clientId : trustedClientIds) {
                    if (resourceAccess.get(clientId) instanceof Map<?, ?> clientClaims) {
                        addRoles(authorities, clientClaims);
                    }
                }
            }
            return authorities;
        }

        private void addRoles(Set<GrantedAuthority> authorities, Map<?, ?> accessClaim) {
            if (accessClaim == null || !(accessClaim.get(ROLES) instanceof Collection<?> rawRoles)) {
                return;
            }
            for (Object rawRole : rawRoles) {
                if (rawRole instanceof String roleName) {
                    String normalized = roleName.trim().toUpperCase(Locale.ROOT);
                    if (NAVIO_ROLES.contains(normalized)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + normalized));
                    }
                }
            }
        }
    }
}
