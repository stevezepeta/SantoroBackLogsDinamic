package backlogs.dinamico.controller.ai;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.service.ai.AiCatalogService;
import backlogs.dinamico.service.ai.HourlySummaryService;
import backlogs.dinamico.service.ai.dto.SystemCatalogItemDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@Validated
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/ai/catalogs")
@RequiredArgsConstructor
@Tag(name = "AI - Catalogs", description = "Catálogos dinámicos para la IA.")
public class AiCatalogController {

    private final HourlySummaryService hourlySummaryService;
    private final AiCatalogService aiCatalogService;

    @GetMapping("/systems")
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<List<SystemCatalogItemDto>> listSystems(
            Authentication auth,
            HttpServletRequest req,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);

        // Calcular sistemas permitidos según RBAC
        List<String> allowedSystems = resolveAllowedSystems(auth);

        List<SystemCatalogItemDto> data = aiCatalogService.listSystems(tenantId, q, limit, allowedSystems);
        return ApiResponse.ok("Systems IA", "ai_catalog_systems", data);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Devuelve null si el usuario no tiene restricciones (ve todos),
     * o la lista de sistemas permitidos si está acotado.
     */
    private List<String> resolveAllowedSystems(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) return null;

        boolean isAdminOrOwner = user.getRoles() != null &&
                (user.getRoles().contains("ORG_ADMIN") || user.getRoles().contains("ORG_OWNER"));

        boolean isUnrestricted = isAdminOrOwner ||
                (user.isOrgWide() && (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()));

        if (isUnrestricted) return null; // sin restricción

        List<String> allowed = user.getAllowedSystems();
        if (allowed == null || allowed.isEmpty()) return List.of(); // sin acceso
        return allowed.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}