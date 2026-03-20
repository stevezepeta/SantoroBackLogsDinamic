package backlogs.dinamico.infra.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica eventos de dashboard por WebSocket.
 *
 * Topic: /topic/dashboard/{tenantId}/{system}
 *
 * El frontend se suscribe a este topic cuando el usuario
 * selecciona un system en el selector del dashboard.
 * Al recibir el evento, re-llama los endpoints de stats/series.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardNotifier {

    private final SimpMessagingTemplate messaging;

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
     * Topic format: /topic/dashboard/{tenantId}/{systemUpperCase}
     * Ejemplo: /topic/dashboard/696a76bddc3d6cd1487cdd35/HID_BIOMETRIC_CAMERA
     */
    public static String buildTopic(ObjectId tenantId, String system) {
        return "/topic/dashboard/" + tenantId.toHexString() + "/"
                + system.trim().toUpperCase().replace(" ", "_");
    }
}