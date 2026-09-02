package com.navio.apigateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Replaces client-supplied identity headers with values derived from the
 * validated JWT.
 *
 * <h2>The problem this fixes</h2>
 * Downstream services read the acting user from an {@code X-User-Id} request
 * header. Because that header arrives from the client, anyone could previously
 * send
 * <pre>curl -H 'X-User-Id: &lt;any-uuid&gt;' https://.../v1/trips/...</pre>
 * and read or modify another user's data. The header looked like an internal
 * detail but was in fact attacker-controlled input being used as an
 * authorization decision.
 *
 * <h2>The fix, in two parts</h2>
 * <ol>
 *   <li><strong>Strip</strong> every identity header that arrived from outside —
 *       unconditionally, before anything else runs. Stripping happens even on
 *       anonymous routes, so a public endpoint cannot be used to smuggle a
 *       forged header through to a service that trusts it.</li>
 *   <li><strong>Re-inject</strong> the same headers from the verified token, so
 *       the value downstream services see is one the gateway proved.</li>
 * </ol>
 *
 * <p>Because the strip is unconditional and the injection depends on a validated
 * token, a request can only ever carry an identity the gateway established.
 *
 * <p><strong>Deployment requirement:</strong> this holds only while services are
 * unreachable except through the gateway. If a service port is exposed on a
 * network an attacker can reach, they can talk to it directly and set the header
 * themselves. Keep the backend network internal, and give the services their own
 * token validation as defence in depth — the user management service already
 * ignores {@code X-User-Id} entirely and derives identity from the JWT itself.
 */
@Component
public class IdentityPropagationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IdentityPropagationFilter.class);

    /** Carries the internal Navio user id — the Keycloak subject. */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** Carries the caller's global roles, comma-separated. */
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    /** Carries the caller's email. */
    public static final String USER_EMAIL_HEADER = "X-User-Email";

    /**
     * Every header the gateway asserts. All are removed from the inbound request
     * before any of them is set, so none can be spoofed.
     */
    private static final Set<String> GATEWAY_ASSERTED_HEADERS =
            Set.of(USER_ID_HEADER, USER_ROLES_HEADER, USER_EMAIL_HEADER);

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Strip first and always. A request that is not authenticated simply
        // proceeds with no identity headers at all, rather than whatever the
        // client chose to send.
        ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(headers -> GATEWAY_ASSERTED_HEADERS.forEach(headers::remove))
                .build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> withIdentity(strippedRequest, authentication))
                // No authentication: forward the stripped request unchanged.
                .defaultIfEmpty(strippedRequest)
                .flatMap(request -> chain.filter(exchange.mutate().request(request).build()));
    }

    private ServerHttpRequest withIdentity(ServerHttpRequest request, JwtAuthenticationToken authentication) {
        Jwt token = authentication.getToken();
        String subject = token.getSubject();

        if (subject == null || subject.isBlank()) {
            // A validated token with no subject cannot identify anyone. Forward
            // without identity headers rather than inventing one.
            log.warn("Validated token carried no subject claim; forwarding without identity headers");
            return request;
        }

        String roles = extractRoles(authentication);
        String email = token.getClaimAsString("email");

        return request.mutate()
                .headers(headers -> {
                    headers.set(USER_ID_HEADER, subject);
                    if (!roles.isEmpty()) {
                        headers.set(USER_ROLES_HEADER, roles);
                    }
                    if (email != null && !email.isBlank()) {
                        headers.set(USER_EMAIL_HEADER, email);
                    }
                })
                .build();
    }

    private String extractRoles(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        return String.join(",", roles);
    }

    /**
     * Runs after Spring Security has populated the reactive security context but
     * before the routing filter forwards the request.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
