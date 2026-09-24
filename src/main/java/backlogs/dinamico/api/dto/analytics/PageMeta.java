package backlogs.dinamico.api.dto.analytics;

/**
 * Metadatos de paginación para respuestas analíticas operativas.
 */
public record PageMeta(
        int page,
        int size,
        boolean hasNext
) {
}
