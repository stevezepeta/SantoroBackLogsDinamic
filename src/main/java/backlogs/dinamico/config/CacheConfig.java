package backlogs.dinamico.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuración de caché en memoria con Caffeine.
 *
 * <p>TTL corto (10 segundos) para evitar datos obsoletos en el dashboard,
 * con invalidación puntual desde {@code DashboardNotifier} cuando llegan
 * nuevos logs por WebSocket.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SYSTEM_STATS_CACHE = "systemStats";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SYSTEM_STATS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(10))
                .maximumSize(1000)
                .recordStats());
        return manager;
    }
}
