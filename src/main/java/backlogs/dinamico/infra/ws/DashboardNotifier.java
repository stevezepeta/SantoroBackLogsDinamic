package backlogs.dinamico.infra.ws;

import backlogs.dinamico.api.dto.dashboard.DashboardStatsDto;
import backlogs.dinamico.config.CacheConfig;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.service.dashboard.DashboardLogService;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Publica eventos de dashboard por WebSocket.
 *
 * Topics:
 *   - /topic/dashboard/{tenantId}/{system}  (notificación ligera de nuevos logs)
 *   - /topic/logs/{system}                  (log completo recién ingresado)
 *   - /topic/system-health                  (salud de 24h del sistema afectado)
 *
 * El frontend se suscribe a estos topics para refrescar el dashboard y el menú
 * de sistemas en tiempo real.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardNotifier {

    private final SimpMessagingTemplate messaging;
    private final DashboardLogService dashboardLogService;
    private final CacheManager cacheManager;

    /**
     * Notifica que llegaron nuevos logs para un system.
     * Llamar desde LogEventService.ingest() después de guardar el log.
     *
     * @param tenantId  Tenant del log
     * @param system    System del log (ej: "HID_BIOMETRIC_CAMERA")
     */
    public void notifyNewLog(ObjectId tenantId, String system) {
        notifyNewLogs(tenantId, system, 1);
    }

    /**
     * Notifica un batch de logs nuevos.
     *
     * @param count Cantidad de logs nuevos en este batch
     */
    public void notifyNewLogs(ObjectId tenantId, String system, int count) {
        if (tenantId == null || system == null) return;

        String topic = buildTopic(tenantId, system);
        DashboardEvent event = DashboardEvent.newLogs(system, count);

        try {
            messaging.convertAndSend(topic, event);
            log.debug("[WS] Dashboard notificado — topic={} count={}", topic, count);
        } catch (Exception e) {
            // No interrumpir el ingest si el WS falla
            log.warn("[WS] Error publicando en topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Emite el log recién guardado y la salud de 24h del sistema afectado.
     * Llamar desde LogEventService.ingest() después de guardar el log.
     *
     * @param logEvent Log recién persistido
     */
    public void broadcastLogIngested(LogEvent logEvent) {
        if (logEvent == null || !StringUtils.hasText(logEvent.getSystem())) return;

        String system = logEvent.getSystem().trim().toUpperCase(Locale.ROOT);

        // Invalidar caché de stats para este sistema antes de publicar
        evictSystemStatsCache(system);

        // 1. Publicar el log completo en /topic/logs/{system}
        try {
            messaging.convertAndSend("/topic/logs/" + system, logEvent);
            log.debug("[WS] Log publicado — topic=/topic/logs/{}", system);
        } catch (Exception e) {
            log.warn("[WS] Error publicando log en /topic/logs/{}: {}", system, e.getMessage());
        }

        // 2. Publicar salud de 24h en /topic/system-health
        try {
            ObjectId tenantId = TenantContext.getTenantId();
            Instant now = Instant.now();
            Instant from = now.minus(24, ChronoUnit.HOURS);
            DashboardStatsDto stats = dashboardLogService.getStats(system, from, now);

            Map<String, Object> healthMap = new LinkedHashMap<>();
            healthMap.put("system", system);
            healthMap.put("tenantId", tenantId != null ? tenantId.toHexString() : null);
            healthMap.put("timestamp", now);
            healthMap.put("totalEvents24h", stats.getTotalEvents());
            healthMap.put("errorCount24h", stats.getErrorCount());
            healthMap.put("errorRate", stats.getErrorRate());
            healthMap.put("healthStatus", stats.getHealthStatus());

            messaging.convertAndSend("/topic/system-health", healthMap);
            log.debug("[WS] Health publicado — topic=/topic/system-health system={}", system);
        } catch (Exception e) {
            log.warn("[WS] Error publicando system-health: {}", e.getMessage());
        }
    }

    private void evictSystemStatsCache(String system) {
        try {
            var cache = cacheManager.getCache(CacheConfig.SYSTEM_STATS_CACHE);
            if (cache instanceof CaffeineCache caffeineCache) {
                ObjectId tenantId = TenantContext.getTenantId();
                String prefix = (tenantId != null ? tenantId.toHexString() : "") + "_" + system + "_";
                caffeineCache.getNativeCache().asMap().keySet()
                        .removeIf(key -> key.toString().startsWith(prefix));
                log.debug("[Cache] systemStats invalidated for system={} prefix={}", system, prefix);
            } else if (cache != null) {
                // Fallback: limpiar todo el caché si no es Caffeine
                cache.clear();
                log.debug("[Cache] systemStats cleared (fallback)");
            }
        } catch (Exception e) {
            log.warn("[Cache] Error invalidating systemStats cache: {}", e.getMessage());
        }
    }

    /**
     * Topic format: /topic/dashboard/{tenantId}/{systemUpperCase}
     * Ejemplo: /topic/dashboard/696a76bddc3d6cd1487cdd35/HID_BIOMETRIC_CAMERA
     */
    public static String buildTopic(ObjectId tenantId, String system) {
        return "/topic/dashboard/" + tenantId.toHexString() + "/"
                + system.trim().toUpperCase().replace(" ", "_");
    }
}