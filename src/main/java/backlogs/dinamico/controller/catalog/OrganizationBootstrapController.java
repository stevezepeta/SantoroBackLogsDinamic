package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.OrgAdminBootstrapReq;

import backlogs.dinamico.service.core.OrganizationAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/catalogs/organizations")
@RequiredArgsConstructor
@CrossOrigin
public class OrganizationBootstrapController {

    private final OrganizationAdminService service;

    // Recomendado: proteger con hasRole('PLATFORM_ADMIN')
    @PostMapping("/{orgId}/bootstrap-admin")
    public ResponseEntity<ApiResponse<Map<String,Object>>> bootstrapAdmin(
            @PathVariable ObjectId orgId,
            @RequestBody OrgAdminBootstrapReq body,
            HttpServletRequest req
    ) {
        var payload = service.bootstrapOrganizationAdmin(orgId, body);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Admin asegurado para la organización", req.getRequestURI(), payload));
    }

}
