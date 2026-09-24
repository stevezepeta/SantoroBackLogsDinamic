package backlogs.dinamico.infra.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Caché simple en memoria con TTL fijo.
 *
 * <p>Wrapper alrededor de Caffeine pensado para consultas de agregación pesadas
 * cuyos resultados no cambian de forma relevante en ventanas cortas de tiempo.
 * La clave es un {@link String} compuesto por el llamador; el valor puede ser
 * cualquier objeto inmutable.</p>
 */
@Slf4j
public class TimedCache<V> {

    private final String name;
    private final Cache<String, V> cache;

    public TimedCache(String name, Duration ttl) {
        this(name, ttl, 100);
    }

    public TimedCache(String name, Duration ttl, long maxSize) {
        this.name = name;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .recordStats()
                .build();
        log.info("[TimedCache] '{}' initialized with ttl={}s, maxSize={}", name, ttl.getSeconds(), maxSize);
    }

    /**
     * Retorna el valor asociado a la clave si existe y no ha expirado.
     * De lo contrario, ejecuta el supplier, almacena el resultado y lo retorna.
     */
    public V get(String key, Supplier<V> loader) {
        V value = cache.getIfPresent(key);
        if (value != null) {
            log.debug("[TimedCache] '{}' hit for key={}", name, key);
            return value;
        }

        log.debug("[TimedCache] '{}' miss for key={}", name, key);
        value = loader.get();
        if (value != null) {
            cache.put(key, value);
        }
        return value;
    }

    /**
     * Invalida una entrada específica del caché.
     */
    public void invalidate(String key) {
        cache.invalidate(key);
        log.debug("[TimedCache] '{}' invalidated key={}", name, key);
    }

    /**
     * Invalida todas las entradas del caché.
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("[TimedCache] '{}' invalidated all entries", name);
    }
}
