package backlogs.dinamico.config;

import backlogs.dinamico.infra.security.ApiKeyTenantFilter;
import backlogs.dinamico.infra.security.JwtAuthFilter;
import backlogs.dinamico.security.JsonAuthEntryPoint;
import backlogs.dinamico.tenant.TenantResolutionFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${multitenant.require-api-key:true}")
    private boolean requireApiKey;

    @Value("${security.basic.enabled:false}")
    private boolean basicEnabled;


    private final ObjectProvider<ApiKeyTenantFilter> apiKeyTenantFilterProvider;
    private final JwtAuthFilter jwtAuthFilter;
    private final TenantResolutionFilter tenantResolutionFilter;

    @Autowired
    JsonAuthEntryPoint jsonAuthEntryPoint;

    @PostConstruct
    void logFlags() {
        log.info("Security flags -> requireApiKey={}, basicEnabled={}",
                requireApiKey, basicEnabled);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {

                    auth.requestMatchers("/error", "/actuator/**").permitAll();
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    // WS
                    auth.requestMatchers("/ws/**").permitAll();

                    // Auth público
                    auth.requestMatchers(HttpMethod.POST,
                            "/api/auth/login",
                            "/api/auth/refresh",
                            "/api/auth/accept-invite",
                            "/api/auth/forgot-password",
                            "/api/auth/reset-password"
                    ).permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/auth/qr-token").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/qr-login").authenticated();

                    // Bootstrap
                    auth.requestMatchers(HttpMethod.POST, "/api/core/bootstrap-admin").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/catalogs/organizations").permitAll();

                    // Ingest por API-KEY (permitAll aquí; ApiKeyTenantFilter lo protege)
                    auth.requestMatchers(HttpMethod.POST, "/api/ingest/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/fingerprint/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/logs/events").permitAll();

                    // Ingest universal por API-KEY (sin JWT)
                    auth.requestMatchers(HttpMethod.POST, "/api/logs", "/api/logs/events").permitAll();

                    // Logs: lectura siempre con JWT
                    auth.requestMatchers("/api/logs/**")
                            .hasAnyRole("ADMIN","TENANT_OWNER","EXEC","OPS","SUPPORT","AUDITOR");

                    // ingest se queda como ya lo tienes (permitAll por API-KEY)
                    auth.requestMatchers(HttpMethod.POST, "/api/logs/events").permitAll();

                    // Catálogos: autenticados (mínimas excepciones ya arriba)
                    auth.requestMatchers("/api/catalogs/**")
                            .hasAnyRole("ADMIN","TENANT_OWNER","EXEC","OPS","SUPPORT","AUDITOR");

                    // ==== Zonas por rol ====
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.GET,    "/api/core/users/*/roles").hasAnyRole("ADMIN", "TENANT_OWNER");
                    auth.requestMatchers(HttpMethod.POST,   "/api/core/users/*/roles/**").hasAnyRole("ADMIN", "TENANT_OWNER");
                    auth.requestMatchers(HttpMethod.DELETE, "/api/core/users/*/roles/**").hasAnyRole("ADMIN", "TENANT_OWNER");
                    auth.requestMatchers(HttpMethod.PUT,    "/api/core/users/*/roles**").hasAnyRole("ADMIN", "TENANT_OWNER");

                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAuthEntryPoint)
                );

        // ====== ORDEN DE FILTROS ======

        // 1) Resolver tenant (JWT -> claim tenantId; fallback X-Tenant)
        http.addFilterBefore(tenantResolutionFilter, UsernamePasswordAuthenticationFilter.class);

        // 2) JWT después del resolver, para que ya exista TenantContext al autenticar
        http.addFilterAfter(jwtAuthFilter, TenantResolutionFilter.class);

        // 3) API Key / validador de tenant (después del resolver)
        var apiKeyFilter = apiKeyTenantFilterProvider.getIfAvailable();
        if (requireApiKey && apiKeyFilter != null) {
            http.addFilterAfter(apiKeyFilter, TenantResolutionFilter.class);
        }

        if (basicEnabled) {
            http.httpBasic(Customizer.withDefaults());
        }

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Permitimos Authorization, X-API-Key y variantes de Tenant
        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-API-Key",
                "X-Api-Key",
                "X-Tenant", "X-Tenant-Id",
                "X-Org-Code", "X-Org-Slug", "X-Org-Domain",
                "X-System-Id", "X-Environment-Id",
                "X-Requested-With"
        ));
        cfg.setExposedHeaders(List.of("X-Request-Id"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
