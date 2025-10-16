package backlogs.dinamico.config;

import backlogs.dinamico.infra.security.ApiKeyTenantFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.annotation.PostConstruct;
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

  @Value("${security.catalogs.protect-writes:false}")
  private boolean protectCatalogWrites;

  private final ObjectProvider<ApiKeyTenantFilter> apiKeyTenantFilterProvider;

  @PostConstruct
  void logFlags() {
    log.info("Security flags -> requireApiKey={}, basicEnabled={}, protectCatalogWrites={}",
        requireApiKey, basicEnabled, protectCatalogWrites);
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .cors(Customizer.withDefaults())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> {
        // públicos
        auth.requestMatchers("/actuator/**").permitAll();
        auth.requestMatchers("/api/ingest/**").permitAll();  // público para ingesta
        auth.requestMatchers(HttpMethod.GET, "/api/catalogs/**").permitAll();

        // si se desea proteger escritura de catálogos
        if (protectCatalogWrites) {
          auth.requestMatchers(HttpMethod.POST,   "/api/catalogs/**").authenticated();
          auth.requestMatchers(HttpMethod.PUT,    "/api/catalogs/**").authenticated();
          auth.requestMatchers(HttpMethod.PATCH,  "/api/catalogs/**").authenticated();
          auth.requestMatchers(HttpMethod.DELETE, "/api/catalogs/**").authenticated();
        } else {
          auth.requestMatchers("/api/catalogs/**").permitAll();
        }

        // resto
        auth.anyRequest().permitAll();
      });

    // El filtro de API-Key sólo se añade cuando está habilitado
    var apiKeyFilter = apiKeyTenantFilterProvider.getIfAvailable();
    if (requireApiKey && apiKeyFilter != null) {
      http.addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class);
    }

    if (basicEnabled) {
      http.httpBasic(Customizer.withDefaults());
    }

    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    var cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of("*")); // si usas credenciales, cambia a allowedOriginPatterns y setAllowCredentials(true)
    cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    // Agregamos X-Tenant-Id además de X-API-Key
    cfg.setAllowedHeaders(List.of("Authorization","Content-Type","X-API-Key","X-Tenant-Id","X-Requested-With"));
    cfg.setExposedHeaders(List.of("X-Request-Id")); // opcional
    cfg.setAllowCredentials(false);
    cfg.setMaxAge(3600L);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}
