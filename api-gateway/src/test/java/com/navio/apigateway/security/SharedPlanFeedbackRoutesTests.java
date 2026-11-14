package com.navio.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercise configured route predicates, not just the independent security allowlist. */
class SharedPlanFeedbackRoutesTests {
    @Test void bundledAndProductionConfigsForwardEveryFeedbackEndpoint() throws Exception {
        var bundled=Path.of("../configuration-server/src/main/resources/config/api-gateway.yml");
        assertFeedbackPaths(bundled);
        var production=Path.of("../../.deploy/config/api-gateway.yml");
        if(Files.exists(production)){
            assertFeedbackPaths(production);
            assertThat(patterns(production)).isEqualTo(patterns(bundled));
        }
    }
    private List<String> patterns(Path config) throws Exception {
        String line=Files.readAllLines(config).stream().map(String::strip)
            .filter(s->s.startsWith("- Path=/v1/shared-plans,")).findFirst().orElseThrow();
        return Arrays.asList(line.substring("- Path=".length()).split(","));
    }
    private void assertFeedbackPaths(Path config) throws Exception {
        var patterns=patterns(config).stream().map(PathPatternParser.defaultInstance::parse).toList();
        for(String suffix:List.of("","/view","/rating","/comments","/comments/123","/comments/123/reports","/reports")){
            String path="/v1/shared-plans/test-token/feedback"+suffix;
            assertThat(patterns.stream().anyMatch(p->p.matches(PathContainer.parsePath(path))))
                .as("%s must forward %s",config,path).isTrue();
        }
        assertThat(patterns.stream().anyMatch(p->p.matches(PathContainer.parsePath("/v1/shared-plans/test-token/unrelated/private")))).isFalse();
    }
}
