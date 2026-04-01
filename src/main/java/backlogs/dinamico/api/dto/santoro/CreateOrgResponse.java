package backlogs.dinamico.api.dto.santoro;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateOrgResponse {

    // --- Organizations ----
    private String orgId;
    private String orgName;
    private String orgDomain;
    private String orgCode;
    private String orgSlug;
    private String orgStatus;

    // --- Super Admin ----
    private String adminUserId;
    private String adminName;
    private String adminEmail;

    /**
     * Password temporal en texto plano — solo se devuelve en esta respuesta.
     * El admin de Santoro debe entregarla directamente al usuario.
     * En el primer login se forzará el cambio de password.
     */
    private String temporaryPassword;

    private String createdAt;

}
