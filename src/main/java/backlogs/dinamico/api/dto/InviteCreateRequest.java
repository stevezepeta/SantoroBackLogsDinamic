package backlogs.dinamico.api.dto;

import backlogs.dinamico.model.core.RoleCode;
import jakarta.validation.constraints.*;
import lombok.Getter;
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

    // Puede venir Null si no es SYSTEM_MANAGER
    private List<String> systems;

    @NotNull(message = "ttHours es obligatorio")
    @Min(value = 1, message = "ttlHours debe ser >=1")
    @Max(value = 720, message = "ttlHours debe ser <= 720")      // 30 dias
    private Integer ttlHours;

    @AssertTrue(message = "systems es obligatorio (mínimo 1) cuando el rol es SYSTEM_MANAGER")
    public boolean isSystemsValidForRole() {
        if (roles == null || roles.isEmpty()) return true; // roles ya valida
        RoleCode role = roles.get(0);

        // Para cualquier rol que NO sea SYSTEM_MANAGER, systems puede ir vacío o incluso venir
        if (role != RoleCode.SYSTEM_MANAGER) return true;

        // Para SYSTEM_MANAGER: mínimo un valor con texto (ignora "" o "   ")
        return systems != null && systems.stream().anyMatch(StringUtils::hasText);
    }

}
