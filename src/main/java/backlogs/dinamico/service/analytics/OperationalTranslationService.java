package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.analytics.OperationalEventCard;
import backlogs.dinamico.api.dto.analytics.OperationalStatus;
import backlogs.dinamico.config.OperationalMessageCatalog;
import backlogs.dinamico.model.log.LogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Servicio de "traducción operativa" (Backend Humano).
 * Convierte un LogEvent técnico en una tarjeta operativa comprensible para supervisores.
 */
@Service
@RequiredArgsConstructor
public class OperationalTranslationService {

    private final OperationalMessageCatalog catalog;

    /**
     * Traduce un LogEvent a una tarjeta operativa amigable.
     *
     * @param event evento técnico de log
     * @return tarjeta operativa lista para mostrar al supervisor
     */
    public OperationalEventCard translate(LogEvent event) {
        if (event == null) {
            return null;
        }

        String eventCode = resolveEventCode(event);
        String system = event.getSystem();
        String status = event.getStatus();
        OperationalStatus operationalStatus = mapStatus(status, event.getOutcome(), event.getIsError());

        OperationalMessageCatalog.OperationalMessage message =
                catalog.find(system, eventCode, status);

        return OperationalEventCard.builder()
                .id(event.getId() != null ? event.getId().toHexString() : null)
                .timestamp(event.getEventTime())
                .status(operationalStatus)
                .title(message != null && StringUtils.hasText(message.getTitle())
                        ? message.getTitle()
                        : eventCode)
                .description(message != null && StringUtils.hasText(message.getDescription())
                        ? message.getDescription()
                        : event.getMessage())
                .suggestedAction(message != null ? message.getAction() : null)
                .icon(message != null && StringUtils.hasText(message.getIcon())
                        ? message.getIcon()
                        : "info")
                .system(system)
                .caseId(event.getCaseId())
                .actorName(extractActorName(event))
                .locationName(extractLocationName(event))
                .latitude(extractLatitude(event))
                .longitude(extractLongitude(event))
                .stepLabel(eventCode)
                .hasErrorExplanation(needsExplanation(event))
                .errorExplanation(null) // lazy loading via POST /api/analytics/explain-error/{logId}
                .build();
    }

    /**
     * Resuelve el código de evento más específico disponible.
     * Preferencia: eventCode > eventTypeRaw > eventType.
     */
    private String resolveEventCode(LogEvent event) {
        if (StringUtils.hasText(event.getEventCode())) {
            return event.getEventCode().trim().toUpperCase();
        }
        if (StringUtils.hasText(event.getEventTypeRaw())) {
            return event.getEventTypeRaw().trim().toUpperCase();
        }
        if (StringUtils.hasText(event.getEventType())) {
            return event.getEventType().trim().toUpperCase();
        }
        return "EVENT";
    }

    /**
     * Mapea el estado técnico del evento a un estado operativo unificado.
     */
    private OperationalStatus mapStatus(String status, String outcome, Boolean isError) {
        String s = status != null ? status.trim().toUpperCase() : "";
        String o = outcome != null ? outcome.trim().toUpperCase() : "";

        if (s.equals("SUCCESS") || o.equals("COMPLETED") || o.equals("SUCCESS")) {
            return OperationalStatus.SUCCESS;
        }

        if (s.equals("REJECTED") || s.equals("FAILED") || s.equals("ERROR")
                || o.equals("ERROR") || o.equals("FAILED")
                || Boolean.TRUE.equals(isError)) {
            return OperationalStatus.ERROR;
        }

        if (s.equals("PENDING") || s.equals("IN_PROGRESS") || o.equals("IN_PROGRESS")) {
            return OperationalStatus.IN_PROGRESS;
        }

        if (s.equals("TIMEOUT") || s.equals("PARTIAL") || o.equals("PARTIAL")) {
            return OperationalStatus.WARNING;
        }

        return OperationalStatus.INFO;
    }

    /**
     * Determina si un evento requiere explicación de error.
     */
    private boolean needsExplanation(LogEvent event) {
        if (Boolean.TRUE.equals(event.getIsError())) {
            return true;
        }

        String status = event.getStatus() != null ? event.getStatus().trim().toUpperCase() : "";
        String outcome = event.getOutcome() != null ? event.getOutcome().trim().toUpperCase() : "";

        if (status.equals("REJECTED") || status.equals("FAILED") || status.equals("ERROR")
                || outcome.equals("ERROR") || outcome.equals("FAILED")) {
            return true;
        }

        if (event.getHttp() != null && event.getHttp().getStatusCode() != null
                && event.getHttp().getStatusCode() >= 400) {
            return true;
        }

        return false;
    }

    private String extractActorName(LogEvent event) {
        if (event.getActor() == null) {
            return null;
        }
        if (StringUtils.hasText(event.getActor().getFullName())) {
            return event.getActor().getFullName();
        }
        if (StringUtils.hasText(event.getActor().getUsername())) {
            return event.getActor().getUsername();
        }
        return event.getActor().getId();
    }

    private String extractLocationName(LogEvent event) {
        if (event.getLocation() == null) {
            return null;
        }
        if (StringUtils.hasText(event.getLocation().getName())) {
            return event.getLocation().getName();
        }
        return event.getLocation().getId();
    }

    /**
     * Extrae la latitud desde el campo geo embebido (GeoJSON Point: [lng, lat]).
     */
    private Double extractLatitude(LogEvent event) {
        List<Double> coords = extractCoordinates(event);
        if (coords == null || coords.size() < 2) {
            return null;
        }
        return coords.get(1);
    }

    /**
     * Extrae la longitud desde el campo geo embebido (GeoJSON Point: [lng, lat]).
     */
    private Double extractLongitude(LogEvent event) {
        List<Double> coords = extractCoordinates(event);
        if (coords == null || coords.size() < 2) {
            return null;
        }
        return coords.get(0);
    }

    private List<Double> extractCoordinates(LogEvent event) {
        if (event.getGeo() == null) {
            return null;
        }
        List<Double> coords = event.getGeo().getCoordinates();
        if (coords == null || coords.isEmpty()) {
            return null;
        }
        return coords;
    }
}
