package backlogs.dinamico.controller.alerting;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.alerting.AlertIncident;
import backlogs.dinamico.service.ai.HourlySummaryService;
import backlogs.dinamico.service.alerting.AlertIncidentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Validated
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidentes", description = "Gestión y resolución de incidentes de alerta.")
public class IncidentController {

    private final AlertIncidentService incidentService;
    private final HourlySummaryService hourlySummaryService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<Page<AlertIncident>> list(
            Authentication auth,
            HttpServletRequest req,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt"));
        Page<AlertIncident> incidents = incidentService.listByTenant(tenantId, status, pageable);
        return ApiResponse.ok("Incidentes", "incidents", incidents);
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<AlertIncident> resolve(
            Authentication auth,
            HttpServletRequest req,
            @PathVariable String id,
            @RequestBody(required = false) ResolveIncidentRequest request
    ) {
        try {
            ObjectId tenantId = null;
            try {
                tenantId = hourlySummaryService.resolveTenantId(auth, req);
            } catch (Exception ignored) {
                // Si no se puede resolver el tenant, continuaremos buscando por ID único
            }

            ObjectId incidentObjectId = ObjectId.isValid(id) ? new ObjectId(id) : null;

            String rootCause = (request != null && request.getRootCause() != null) ? request.getRootCause() : "Otras Causas";
            String solutionComment = (request != null && request.getSolutionComment() != null) ? request.getSolutionComment() : "Sin comentarios";
            String resolvedBy = (request != null && request.getResolvedBy() != null && !request.getResolvedBy().isBlank())
                    ? request.getResolvedBy()
                    : actorEmail(auth);

            AlertIncident resolved = incidentService.resolveFlexible(
                    tenantId,
                    id,
                    incidentObjectId,
                    rootCause,
                    solutionComment,
                    resolvedBy
            );
            return ApiResponse.ok("Incidente resuelto exitosamente", "incident_resolved", resolved);
        } catch (Exception e) {
            System.err.println("❌ ERROR AL RESOLVER INCIDENTE EN BACKEND: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al resolver el incidente: " + e.getMessage());
        }
    }

    @Data
    public static class ResolveIncidentRequest {
        private String rootCause;
        private String solutionComment;
        private String resolvedBy;
        private String resolvedAt;
    }

    private String actorEmail(Authentication auth) {
        if (auth == null) return "Sistema";
        Object p = auth.getPrincipal();
        if (p instanceof backlogs.dinamico.infra.security.AuthUser au) return au.getEmail();
        if (p instanceof org.springframework.security.core.userdetails.UserDetails ud) return ud.getUsername();
        return String.valueOf(p);
    }
}
