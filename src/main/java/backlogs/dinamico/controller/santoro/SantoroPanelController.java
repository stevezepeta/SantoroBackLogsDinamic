package backlogs.dinamico.controller.santoro;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.CreateOrgRequest;
import backlogs.dinamico.api.dto.santoro.CreateOrgResponse;
import backlogs.dinamico.api.dto.santoro.OrgSummaryDto;
import backlogs.dinamico.api.dto.santoro.SantoroPanelStatsDto;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.service.santoro.SantoroPanelService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static backlogs.dinamico.service.santoro.SantoroPanelService.*;

/**
 * Panel de administración exclusivo para Grupo Santoro.
 *
 * Restricción de acceso: solo usuarios con email @grupo-santoro.com.mx
 * pueden consumir estos endpoints. La validación se hace en 2 capas:
 *   1. @PreAuthorize valida la autoridad ROLE_ORG_ADMIN o PERM_SETTINGS_MANAGE
 *   2. SantoroPanelService.assertSantoroDomain() valida el dominio del email
 *
 * Base path: /api/santoro/panel
 */
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Santoro Panel", description = "Centro de control administrativo - solo @grupo-santoro.com.mx")
@RestController
@RequestMapping("/api/santoro/panel")
@RequiredArgsConstructor
public class SantoroPanelController {

    private final SantoroPanelService svc;

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * Números de portada del dashboard: orgs, usuarios, API Keys.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<SantoroPanelStatsDto> stats(Authentication auth) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("Stats del panel", "stats", svc.buildStats());
    }

    // ── Organizations ─────────────────────────────────────────────────────────

    /**
     * Listado paginado de todas las organizaciones con sus conteos.
     */
    @GetMapping("/organizations")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<Page<OrgSummaryDto>> listOrgs(
            Authentication auth,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0)  int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("Organizaciones", "organizations",
                svc.listOrganizations(search, status, page, size));
    }

    /**
     * Detalle de una organización con sus conteos.
     */
    @GetMapping("/organizations/{id}")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<OrgSummaryDto> getOrg(
            Authentication auth,
            @PathVariable ObjectId id
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("Organización", "organization", svc.getOrganization(id));
    }

    /**
     * Crea una nueva organización junto con su primer usuario superAdmin.
     * La password temporal se devuelve solo en esta respuesta.
     */
    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<CreateOrgResponse> createOrg(
            Authentication auth,
            @RequestBody @Valid CreateOrgRequest req
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("Organización creada", "organization", svc.createOrganization(req));
    }

    /**
     * Activa o desactiva una organización.
     * Al desactivar se revocan automáticamente todas sus API Keys activas.
     *
     * Body: { "status": "active" | "disabled" }
     */
    @PutMapping("/organizations/{id}/status")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<OrgSummaryDto> setOrgStatus(
            Authentication auth,
            @PathVariable ObjectId id,
            @RequestBody StatusRequest body
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("Estado actualizado", "organization",
                svc.setOrganizationStatus(id, body.getStatus()));
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    /**
     * Listado global de todos los usuarios del sistema (todos los tenants).
     */
    @GetMapping("/users")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<Page<SantoroPanelService.UserView>> listAllUsers(
            Authentication auth,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0)   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("Usuarios", "users",
                svc.listAllUsers(search, status, page, size));
    }

    /**
     * Usuarios de una organización específica.
     */
    @GetMapping("/organizations/{orgId}/users")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<Page<SantoroPanelService.UserView>> listUsersByOrg(
            Authentication auth,
            @PathVariable ObjectId orgId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0)   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("Usuarios de la organización", "users",
                svc.listUsersByOrg(orgId, search, status, page, size));
    }

    // ── API Keys ──────────────────────────────────────────────────────────────

    /**
     * Listado global de todas las API Keys del sistema.
     */
    @GetMapping("/api-keys")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<Page<ApiKeyView>> listAllApiKeys(
            Authentication auth,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0)   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("API Keys", "apiKeys",
                svc.listAllApiKeys(search, status, page, size));
    }

    /**
     * API Keys de una organización específica.
     */
    @GetMapping("/organizations/{orgId}/api-keys")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<Page<ApiKeyView>> listApiKeysByOrg(
            Authentication auth,
            @PathVariable ObjectId orgId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0)   int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("API Keys de la organización", "apiKeys",
                svc.listApiKeysByOrg(orgId, search, status, page, size));
    }

    /**
     * Activa o revoca una API Key de una organización.
     * Úselo para cortar acceso cuando una org no paga la cuota.
     *
     * Body: { "status": "active" | "revoked" }
     */
    @PutMapping("/organizations/{orgId}/api-keys/{keyId}/status")
    @PreAuthorize("hasAnyAuthority('ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<ApiKeyView> setApiKeyStatus(
            Authentication auth,
            @PathVariable ObjectId orgId,
            @PathVariable ObjectId keyId,
            @RequestBody StatusRequest body
    ) {
        svc.assertSantoroDomain(email(auth));
        return ApiResponse.ok("Estado de API Key actualizado", "apiKey",
                svc.setApiKeyStatus(orgId, keyId, body.getStatus()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String email(Authentication auth) {
        if (auth == null) return "";
        Object p = auth.getPrincipal();
        if (p instanceof AuthUser au) return au.getEmail();
        return String.valueOf(p);
    }

    /** Body genérico para cambios de estado. */
    @lombok.Data
    public static class StatusRequest {
        private String status;
    }

}
