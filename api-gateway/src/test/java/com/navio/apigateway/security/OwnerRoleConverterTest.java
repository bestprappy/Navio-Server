package com.navio.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerRoleConverterTest {

    @Test
    void mapsOwnerButStillIgnoresUntrustedRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject("owner-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of("OWNER", "realm-admin")))
                .build();

        var converter = new GatewaySecurityConfig.KeycloakRealmRoleConverter(List.of("navio-api"));
        assertThat(converter.convert(jwt)).extracting("authority").containsExactly("ROLE_OWNER");
    }
}
