package backlogs.dinamico.api.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Tarjeta operativa pre-digerida para presentar un evento de log a un supervisor.
 */
@Data
@Builder
public class OperationalEventCard {

    private String id;
    private Instant timestamp;
    private OperationalStatus status;
    private String title;
    private String description;
    private String suggestedAction;
    private String icon;

    private String system;
    private String caseId;
    private String actorName;
    private String locationName;

    /** Coordenadas geográficas extraídas de LogEvent.geo (GeoJSON Point [lng, lat]). */
    private Double latitude;
    private Double longitude;

    private String stepLabel;

    /**
     * Indica si este evento admite una explicación de IA.
     * La explicación real nunca se carga síncronamente; se obtiene vía
     * POST /api/analytics/explain-error/{logId} bajo demanda.
     */
    private boolean hasErrorExplanation;

    /**
     * Siempre null en las respuestas de lista/timeline.
     * Se rellena únicamente en el endpoint dedicado de explicación.
     */
    private String errorExplanation;
}
