package backlogs.dinamico.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica la configuración global de CORS: el frontend desplegado en
 * http://187.188.66.56:8032 debe estar permitido con credenciales, y los
 * orígenes no autorizados deben quedar fuera (sin comodines).
 */
class SecurityConfigCorsTest {

    private CorsConfigurationSource buildSource(String origins) {
        SecurityConfig config = new SecurityConfig(null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", origins.split(","));
        return config.corsConfigurationSource();
    }

    private CorsConfiguration cfgFor(String origins, String requestOrigin) {
        return buildSource(origins).getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest() {{
            setServerName(requestOrigin);
            addHeader("Origin", requestOrigin);
        }});
    }

    private static final String PROD_ORIGINS =
            "https://dashboard.grupo-santoro.com.mx,http://187.188.66.56:8032,http://localhost:9000";

    @Test
    void localQuasarOriginIsAllowed() {
        // Dev server Quasar/Vite (proxy /api y /ws → backend remoto)
        CorsConfiguration cfg = cfgFor(PROD_ORIGINS, "http://localhost:9000");

        assertNotNull(cfg, "el origen del frontend local debe tener configuración CORS");
        assertEquals("http://localhost:9000", cfg.checkOrigin("http://localhost:9000"));
        assertTrue(cfg.getAllowCredentials());
    }

    @Test
    void loginPreflightFromLocalOriginIsNotBlockedByCors() {
        CorsConfiguration cfg = cfgFor(PROD_ORIGINS, "http://localhost:9000");

        // Preflight de POST /api/auth/login: método OPTIONS + cabeceras del login
        assertTrue(cfg.getAllowedMethods().contains("POST"));
        assertTrue(cfg.getAllowedMethods().contains("OPTIONS"));
        assertTrue(cfg.getAllowedHeaders().contains("Authorization"));
        assertTrue(cfg.getAllowedHeaders().contains("Content-Type"));
    }

    @Test
    void deployedDashboardOriginIsAllowedWithCredentials() {
        CorsConfiguration cfg = cfgFor(
                "https://dashboard.grupo-santoro.com.mx,http://187.188.66.56:8032,http://localhost:9000",
                "http://187.188.66.56:8032");

        assertNotNull(cfg, "el origen del frontend desplegado debe tener configuración CORS");
        assertEquals("http://187.188.66.56:8032", cfg.checkOrigin("http://187.188.66.56:8032"));
        assertTrue(cfg.getAllowCredentials());
        assertTrue(cfg.getAllowedMethods().contains("GET"));
        assertTrue(cfg.getAllowedMethods().contains("OPTIONS"));
        assertTrue(cfg.getAllowedHeaders().contains("Authorization"));
        assertTrue(cfg.getAllowedHeaders().contains("Content-Type"));
    }

    @Test
    void productionDashboardOriginRemainsAllowed() {
        CorsConfiguration cfg = cfgFor(
                "https://dashboard.grupo-santoro.com.mx,http://187.188.66.56:8032",
                "https://dashboard.grupo-santoro.com.mx");

        assertNotNull(cfg);
        assertEquals("https://dashboard.grupo-santoro.com.mx",
                cfg.checkOrigin("https://dashboard.grupo-santoro.com.mx"));
    }

    @Test
    void untrustedOriginIsRejected() {
        CorsConfiguration cfg = cfgFor(
                "https://dashboard.grupo-santoro.com.mx,http://187.188.66.56:8032",
                "http://evil.example.com");

        // checkOrigin devuelve null cuando el origen no está permitido
        // (así lo consume DefaultCorsProcessor → 403 "Invalid CORS request")
        assertNull(cfg.checkOrigin("http://evil.example.com"));
        assertNull(cfg.checkOrigin("http://187.188.66.56:9999"));
        assertNull(cfg.checkOrigin("http://sub.187.188.66.56:8032"));
    }

    @Test
    void defaultOriginsDoNotUseWildcard() {
        CorsConfiguration cfg = cfgFor(
                "https://dashboard.grupo-santoro.com.mx,http://187.188.66.56:8032,http://localhost:4200",
                "http://187.188.66.56:8032");

        for (String pattern : cfg.getAllowedOriginPatterns()) {
            assertNotEquals("*", pattern, "no se permite el comodín global");
        }
    }
}
