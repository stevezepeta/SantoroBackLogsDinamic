package backlogs.dinamico.config;

import backlogs.dinamico.infra.security.ApiKeyTenantFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

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

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .cors(Customizer.withDefaults())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> {
        auth.requestMatchers("/actuator/**").permitAll();
        auth.requestMatchers("/api/ingest/**").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/api/catalogs/**").permitAll();
        if (protectCatalogWrites) {
          auth.requestMatchers(HttpMethod.POST,   "/api/catalogs/**").authenticated();
          auth.requestMatchers(HttpMethod.PUT,    "/api/catalogs/**").authenticated();
          auth.requestMatchers(HttpMethod.PATCH,  "/api/catalogs/**").authenticated();
          auth.requestMatchers(HttpMethod.DELETE, "/api/catalogs/**").authenticated();
        } else {
          auth.requestMatchers("/api/catalogs/**").permitAll();
        }
        auth.anyRequest().permitAll();
      });

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
    cfg.setAllowedOrigins(List.of("*"));
    cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization","Content-Type","X-API-Key","X-Requested-With"));
    cfg.setAllowCredentials(false);
    cfg.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}
