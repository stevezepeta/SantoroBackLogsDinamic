package backlogs.dinamico.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiGroups {

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("01 - Admin")
                .pathsToMatch("/api/admin/**")
                .build();
    }

    @Bean
    GroupedOpenApi coreApi() {
        return GroupedOpenApi.builder()
                .group("02 - Core")
                .pathsToMatch("/api/core/**")
                .build();
    }

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("03 - Public")
                .pathsToMatch("/api/public/**", "/api/auth/**", "/api/health/**")
                .build();
    }

}
