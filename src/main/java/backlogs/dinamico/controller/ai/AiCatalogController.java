package backlogs.dinamico.controller.ai;

import backlogs.dinamico.api.ApiResponse;
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
        List<SystemCatalogItemDto> data = aiCatalogService.listSystems(tenantId, q, limit);
        return ApiResponse.ok("Systems IA", "ai_catalog_systems", data);
    }
}