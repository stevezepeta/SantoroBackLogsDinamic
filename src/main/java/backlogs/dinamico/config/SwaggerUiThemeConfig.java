package backlogs.dinamico.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Configuration
public class SwaggerUiThemeConfig {

    private static final String THEME_CSS_URL = "/docs-assets/swagger-theme.css?v=1";

    @Bean
    public SwaggerIndexTransformer swaggerIndexTransformer(
            SwaggerUiConfigProperties swaggerUiConfig,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider
    ) {
        return new ThemeInjector(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
    }

    static class ThemeInjector extends SwaggerIndexPageTransformer {

        ThemeInjector(SwaggerUiConfigProperties swaggerUiConfig,
                      SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                      SwaggerWelcomeCommon swaggerWelcomeCommon,
                      ObjectMapperProvider objectMapperProvider) {
            super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
        }

        @Override
        public @NonNull Resource transform(@NonNull HttpServletRequest request,
                                           @NonNull Resource resource,
                                           @NonNull ResourceTransformerChain transformer) throws IOException {

            // Deja que springdoc haga su transformación normal primero
            Resource base = super.transform(request, resource, transformer);

            if (!"index.html".equalsIgnoreCase(base.getFilename())) {
                return base;
            }

            String html;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(base.getInputStream(), StandardCharsets.UTF_8))) {
                html = reader.lines().collect(Collectors.joining("\n"));
            }

            // Inyecta el link ANTES de </head>
            String injected = html.replace(
                    "</head>",
                    "<link rel=\"stylesheet\" type=\"text/css\" href=\"" + THEME_CSS_URL + "\" />\n</head>"
            );

            return new TransformedResource(base, injected.getBytes(StandardCharsets.UTF_8));
        }
    }

}
