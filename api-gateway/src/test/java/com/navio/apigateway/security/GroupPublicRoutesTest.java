package com.navio.apigateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.config.EnableWebFlux;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(GroupPublicRoutesTest.Config.class)
@TestPropertySource(properties = {
        "navio.security.keycloak.issuer-uri=https://issuer.example", "navio.security.keycloak.audiences=navio",
        "navio.security.keycloak.jwk-set-uri=https://issuer.example/jwks"
})
class GroupPublicRoutesTest {
    @Autowired ApplicationContext context;
    WebTestClient client;
    @BeforeEach void setup() { client = WebTestClient.bindToApplicationContext(context).build(); }
    @Test void guestsCanOnlyUsePublicGroupReads() {
        for (String path : new String[]{"/v1/groups", "/v1/groups/search?q=ev", "/v1/groups/thailand-ev-charging", "/v1/groups/ev/banner"}) {
            client.get().uri(path).exchange().expectStatus().isOk();
        }
        for (String path : new String[]{"/v1/groups/mine", "/v1/groups/ev/members", "/v1/groups/ev/moderators", "/v1/trips"}) {
            client.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
        for (HttpMethod method : new HttpMethod[]{HttpMethod.POST,HttpMethod.PATCH,HttpMethod.PUT,HttpMethod.DELETE}) {
            client.method(method).uri("/v1/groups/ev").exchange().expectStatus().isUnauthorized();
        }
        client.post().uri("/v1/groups").exchange().expectStatus().isUnauthorized();
        client.post().uri("/v1/groups/ev/banner").exchange().expectStatus().isUnauthorized();
    }
    @Configuration @EnableWebFlux @Import(GatewaySecurityConfig.class)
    static class Config {
        @Bean static org.springframework.core.convert.ConversionService conversionService() {
            return new org.springframework.boot.convert.ApplicationConversionService();
        }
        @Bean @Primary ReactiveJwtDecoder testDecoder() { return mock(ReactiveJwtDecoder.class); }
        @Bean Endpoints endpoints() { return new Endpoints(); }
    }
    @RestController
    static class Endpoints {
        @RequestMapping("/**") String ok() { return "ok"; }
    }
}
