package com.navio.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the fix for the {@code X-User-Id} trust bug.
 *
 * <p>Downstream services read the acting user from that header. Before this
 * filter existed the header came straight from the client, so
 * {@code curl -H 'X-User-Id: <someone-else>'} was a complete authorization
 * bypass. These tests assert that a client-supplied value can never survive,
 * whether or not the request is authenticated.
 */
class IdentityPropagationFilterTest {

    private static final String SPOOFED_USER_ID = "11111111-1111-4111-8111-111111111111";
    private static final String REAL_SUBJECT = "22222222-2222-4222-8222-222222222222";

    private final IdentityPropagationFilter filter = new IdentityPropagationFilter();

    @Test
    void stripsClientSuppliedUserIdAndReplacesItWithTheTokenSubject() {
        MockServerWebExchange exchange = exchangeWithHeaders();
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuthentication()))
                .block();

        ServerHttpRequest forwarded = chain.forwardedRequest();
        assertThat(forwarded.getHeaders().getFirst(IdentityPropagationFilter.USER_ID_HEADER))
                .isEqualTo(REAL_SUBJECT)
                .isNotEqualTo(SPOOFED_USER_ID);
    }

    @Test
    void stripsSpoofedRolesAndEmailHeaders() {
        MockServerWebExchange exchange = exchangeWithHeaders();
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuthentication()))
                .block();

        ServerHttpRequest forwarded = chain.forwardedRequest();
        // Roles come from the validated token's authorities, not the request.
        assertThat(forwarded.getHeaders().getFirst(IdentityPropagationFilter.USER_ROLES_HEADER))
                .isEqualTo("USER")
                .doesNotContain("ADMIN");
        assertThat(forwarded.getHeaders().getFirst(IdentityPropagationFilter.USER_EMAIL_HEADER))
                .isEqualTo("real@example.com");
    }

    @Test
    void stripsIdentityHeadersWhenThereIsNoAuthentication() {
        // The critical case. On an unauthenticated route the filter must still
        // remove the headers, or a public endpoint becomes a way to smuggle a
        // forged identity through to a service that trusts it.
        MockServerWebExchange exchange = exchangeWithHeaders();
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.forwardedRequest();
        assertThat(forwarded.getHeaders().getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isNull();
        assertThat(forwarded.getHeaders().getFirst(IdentityPropagationFilter.USER_ROLES_HEADER)).isNull();
        assertThat(forwarded.getHeaders().getFirst(IdentityPropagationFilter.USER_EMAIL_HEADER)).isNull();
    }

    @Test
    void stripsIdentityHeadersForNonJwtAuthentication() {
        MockServerWebExchange exchange = exchangeWithHeaders();
        CapturingChain chain = new CapturingChain();

        Authentication nonJwt = new TestingAuthenticationToken("someone", "credentials", "ROLE_ADMIN");

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(nonJwt))
                .block();

        assertThat(chain.forwardedRequest().getHeaders()
                .getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isNull();
    }

    @Test
    void headerCasingCannotBeUsedToBypassTheStrip() {
        // HTTP header names are case-insensitive; a filter comparing them
        // case-sensitively would let "x-user-id" slip through untouched.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/trips")
                        .header("x-user-id", SPOOFED_USER_ID)
                        .header("X-USER-ROLES", "ADMIN")
                        .build());
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.forwardedRequest();
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Roles")).isNull();
    }

    private MockServerWebExchange exchangeWithHeaders() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/v1/trips")
                .header(IdentityPropagationFilter.USER_ID_HEADER, SPOOFED_USER_ID)
                .header(IdentityPropagationFilter.USER_ROLES_HEADER, "ADMIN,MODERATOR")
                .header(IdentityPropagationFilter.USER_EMAIL_HEADER, "attacker@example.com")
                .build());
    }

    private JwtAuthenticationToken jwtAuthentication() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(REAL_SUBJECT)
                .claim("email", "real@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")), REAL_SUBJECT);
    }

    /** Captures the request the filter forwards, so headers can be asserted. */
    private static final class CapturingChain implements GatewayFilterChain {

        private final AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            captured.set(exchange);
            return Mono.empty();
        }

        ServerHttpRequest forwardedRequest() {
            ServerWebExchange exchange = captured.get();
            if (exchange == null) {
                throw new IllegalStateException("The filter did not forward the request");
            }
            return exchange.getRequest();
        }
    }
}
