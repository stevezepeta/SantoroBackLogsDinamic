package backlogs.dinamico.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * Entidad para configuración dinámica de embudos de conversión (Funnels).
 * 
 * Esta colección define los flujos de proceso de cada sistema sin necesidad
 * de modificar código Java. Los pasos del funnel se leen desde MongoDB y 
 * se aplican a las queries de análisis de forma dinámica.
 * 
 * Ejemplo de documento:
 * {
 *   "systemName": "TRUSTVALUE",
 *   "funnelName": "Flujo de Jornada Laboral",
 *   "steps": [
 *     { "order": 1, "eventType": "INICIO_SESION", "label": "Inicio de Sesión" },
 *     { "order": 2, "eventType": "SELECCION_SUCURSAL", "label": "Selección de Sucursal" },
 *     { "order": 3, "eventType": "ENVIAR_EVIDENCIAS", "label": "Envío de Evidencias" },
 *     { "order": 4, "eventType": "FINALIZAR_ASISTENCIA", "label": "Cierre de Jornada" }
 *   ]
 * }
 */
@Document("funnel_templates")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FunnelTemplate {

    @Id
    private ObjectId id;

    /**
     * Nombre único del sistema (ej: TRUSTVALUE, CITA_GUYANA, TICKETS).
     * Se usa como clave de búsqueda en el endpoint /api/analytics/funnel/{systemName}
     */
    @Indexed(unique = true)
    @Field("systemName")
    private String systemName;

    /**
     * Nombre descriptivo del flujo (ej: "Flujo de Jornada Laboral")
     */
    @Field("funnelName")
    private String funnelName;

    /**
     * Lista ordenada de pasos del embudo.
     * Cada paso define un eventType y su etiqueta legible.
     */
    @Field("steps")
    private List<FunnelStepDefinition> steps;

    /**
     * Fecha de creación del template
     */
    @Field("createdAt")
    private Instant createdAt;

    /**
     * Fecha de última modificación
     */
    @Field("updatedAt")
    private Instant updatedAt;

    /**
     * Usuario que creó/actualizó el template (opcional)
     */
    @Field("createdBy")
    private String createdBy;

    /**
     * Activo o inactivo (permite deshabilitar sin eliminar)
     */
    @Builder.Default
    @Field("active")
    private Boolean active = true;

    /**
     * Descripción adicional del flujo (opcional)
     */
    @Field("description")
    private String description;

    /**
     * Definición de un paso individual en el funnel.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FunnelStepDefinition {
        
        /**
         * Orden del paso (1 = primer paso, 2 = segundo, etc.)
         */
        @Field("order")
        private Integer order;

        /**
         * Tipo de evento que representa este paso (debe existir en log_events)
         */
        @Field("eventType")
        private String eventType;

        /**
         * Etiqueta legible para mostrar en el frontend
         */
        @Field("label")
        private String label;

        /**
         * Descripción adicional del paso (opcional)
         */
        @Field("description")
        private String description;
    }
}

