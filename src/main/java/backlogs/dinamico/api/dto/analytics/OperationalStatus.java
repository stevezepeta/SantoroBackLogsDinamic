package backlogs.dinamico.api.dto.analytics;

/**
 * Estados operativos unificados para presentación a supervisores.
 */
public enum OperationalStatus {
    SUCCESS("Éxito"),
    WARNING("Advertencia"),
    ERROR("Error"),
    IN_PROGRESS("En progreso"),
    INFO("Información");

    private final String label;

    OperationalStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
