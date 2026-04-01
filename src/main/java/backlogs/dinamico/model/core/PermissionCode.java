package backlogs.dinamico.model.core;

public enum PermissionCode {

    LOG_READ,                // consultar logs
    LOG_EXPORT,              // exportar PDF/Excel/CSV
    LOG_REPORTS,             // generar reportes (si lo separas de export)
    ALERTS_MANAGE,           // crear/editar alertas
    SAVED_VIEWS_MANAGE,      // vistas guardadas/dashboards

    USERS_MANAGE,            // invitar/desactivar/reset
    ROLES_ASSIGN,            // asignar roles/scopes

    SETTINGS_MANAGE,         // configurar integraciones/retención/esquemas

    VIEW_PII,                // ver datos sensibles sin masking
    VIEW_RAW_PAYLOAD,        // ver request/response completo
    CROSS_SYSTEM_CORRELATE   // correlación cross-system (si lo activas)

}
