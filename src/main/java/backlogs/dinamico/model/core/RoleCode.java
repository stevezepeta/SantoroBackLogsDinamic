package backlogs.dinamico.model.core;

public enum RoleCode {

    ORG_OWNER,       // bootstrap-admin: gobierno total dentro de la organización
    ORG_ADMIN,       // admin operativo sin gobierno
    SYSTEM_MANAGER,  // jefe de system (uno o varios systems)
    AUDITOR,         // auditoría read + export
    SUPPORT_TI,      // soporte técnico / plataforma
    SUPPORT, EXEC, VIEWER           // solo lectura

}
