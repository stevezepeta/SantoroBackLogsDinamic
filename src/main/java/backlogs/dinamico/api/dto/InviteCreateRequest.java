package backlogs.dinamico.api.dto;

import backlogs.dinamico.model.core.RoleCode;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.List;

@Getter
@Setter
public class InviteCreateRequest {

    @NotBlank(message = "email es obligatorio")
    @Email(message = "email invalido")
    private String email;

    @NotEmpty(message = "rol es obligatorio")
    @Size(min = 1, max = 1, message = "Solo se permite un rol por invitacion")
    private List<RoleCode> roles;

    // Puede venir null si no es SYSTEM_MANAGER
    private List<String> systems;

    @NotNull(message = "ttlHours es obligatorio")
    @Min(value = 1,   message = "ttlHours debe ser >=1")
    @Max(value = 720, message = "ttlHours debe ser <= 720")
    private Integer ttlHours;

    /**
     * Filtros de visibilidad de logs para el usuario invitado.
     * Opcional — si se omite o todos sus campos están vacíos, el usuario ve todo.
     * Uso típico: limitar qué logs ve un cliente VIEWER de la organización.
     *
     * Ejemplo:
     * {
     *   "allowedOutcomes":   ["SUCCESS", "APPROVED"],
     *   "allowedStatuses":   ["OK"],
     *   "allowedSeverities": ["INFO"],
     *   "allowedEventTypes": []
     * }
     */
    private LogFilterRequest logFilters;

    @AssertTrue(message = "systems es obligatorio (mínimo 1) cuando el rol es SYSTEM_MANAGER")
    public boolean isSystemsValidForRole() {
        if (roles == null || roles.isEmpty()) return true;
        RoleCode role = roles.get(0);
        if (role != RoleCode.SYSTEM_MANAGER) return true;
        return systems != null && systems.stream().anyMatch(StringUtils::hasText);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LogFilterRequest {

        /** Valores de `outcome` permitidos. Vacío = sin restricción. Ej: ["SUCCESS","APPROVED"] */
        private List<String> allowedOutcomes;

        /** Valores de `status` permitidos. Vacío = sin restricción. Ej: ["OK"] */
        private List<String> allowedStatuses;

        /** Valores de `severity` permitidos. Vacío = sin restricción. Ej: ["INFO","DEBUG"] */
        private List<String> allowedSeverities;

        /** Tipos de evento permitidos. Vacío = sin restricción. Ej: ["AUTH_LOGIN"] */
        private List<String> allowedEventTypes;
    }
}