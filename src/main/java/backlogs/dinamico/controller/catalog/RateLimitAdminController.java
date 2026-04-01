package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.config.RateLimitProperties;
import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.model.core.RateLimitPolicy;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.service.catalog.RateLimitPolicyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(value = "/api/admin/rate-limit", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(
        name = "Admin - Rate Limit",
        description = """
Administración del rate limit por Tenant.

### Conceptos
- **apiKey**: limita por API key (control por integración)
- **tenant**: limita globalmente por organización (suma todas las keys)

### source
- **DEFAULT_YML**: se usa el default del `application.yml`
- **ORG_OVERRIDE**: existe override guardado en la organización
"""
)
public class RateLimitAdminController {

    private final OrganizationRepository orgRepo;
    private final RateLimitPolicyService policyService;
    private final RateLimitProperties rateLimitProps;

    // -------- DTOs --------
    @Data
    @Schema(name = "BucketReq", description = "Configuración de bucket (token bucket)")
    public static class BucketReq {
        @Min(1)
        @Schema(example = "400", description = "Capacidad máxima del bucket (burst)")
        private long capacity;

        @Min(1)
        @Schema(example = "200", description = "Tokens que se recargan por periodo")
        private long refillTokens;

        @Min(1)
        @Schema(example = "1", description = "Cada cuántos segundos se recarga")
        private long refillSeconds;
    }

    @Data
    @Schema(name = "RateLimitReq", description = "Body para actualizar rate limit")
    public static class RateLimitReq {
        @Schema(example = "true", description = "Habilita/deshabilita el rate limit")
        private boolean enabled = true;

        @NotNull
        private BucketReq apiKey;

        @NotNull
        private BucketReq tenant;
    }

    @Data
    @Schema(name = "RateLimitView", description = "Vista del rate limit actual (stored vs effective)")
    public static class RateLimitView {
        @Schema(example = "DEFAULT_YML")
        private String source;

        @Schema(description = "Override guardado en la organización (puede ser null)")
        private RateLimitPolicy stored;

        @Schema(description = "Configuración final efectiva (stored o fallback del yml)")
        private RateLimitPolicy effective;
    }

    // ----------------- GET -----------------

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORG_OWNER','ROLE_ORG_OWNER') or hasRole('ORG_OWNER')")
    @Operation(
            summary = "Consultar rate limit actual",
            description = "Regresa `stored` (override) y `effective` (lo que aplica realmente)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "OK",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "OK",
                                    value = """
                                            {
                                              "ok": true,
                                              "code": "ok",
                                              "message": "Rate limit actual",
                                              "path": "rate_limit",
                                              "timestamp": "2026-02-09T20:23:07.919396900Z",
                                              "data": {
                                                "source": "DEFAULT_YML",
                                                "stored": null,
                                                "effective": {
                                                  "enabled": true,
                                                  "apiKey": { "capacity": 400, "refillTokens": 200, "refillSeconds": 1 },
                                                  "tenant": { "capacity": 4000, "refillTokens": 2000, "refillSeconds": 1 }
                                                }
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "tenant_id_not_found_in_auth",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "ok": false, "code": "tenant_id_not_found_in_auth", "message": "tenant_id_not_found_in_auth" }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "ok": false, "code": "unauthorized", "message": "unauthorized" }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "tenant_not_found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "ok": false, "code": "tenant_not_found", "message": "tenant_not_found" }
                                    """)
                    )
            )
    })
    public ApiResponse<?> get(Authentication auth, HttpServletRequest req) {

        ObjectId tenantId = resolveTenantId(auth, req);

        Organization org = orgRepo.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "tenant_not_found"));

        RateLimitPolicy fallback = fallbackPolicyFromYml();
        RateLimitPolicy effective = policyService.resolve(org.getId(), fallback);

        RateLimitView view = new RateLimitView();
        view.setStored(org.getRateLimit());
        view.setEffective(effective);
        view.setSource(org.getRateLimit() != null ? "ORG_OVERRIDE" : "DEFAULT_YML");

        return ApiResponse.ok("Rate limit actual", "rate_limit", view);
    }

    // ----------------- PUT -----------------

    @PutMapping
    @PreAuthorize("hasAnyAuthority('ORG_OWNER','ROLE_ORG_OWNER') or hasRole('ORG_OWNER')")
    @Operation(
            summary = "Actualizar rate limit (override)",
            description = "Guarda el override en la organización y reinvalida caches.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RateLimitReq.class),
                            examples = @ExampleObject(
                                    name = "Enterprise baseline",
                                    value = """
                                            {
                                              "enabled": true,
                                              "apiKey": { "capacity": 400, "refillTokens": 200, "refillSeconds": 1 },
                                              "tenant": { "capacity": 4000, "refillTokens": 2000, "refillSeconds": 1 }
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "bad_request / validation_error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "tenant_not_found")
    })
    public ApiResponse<?> upsert(Authentication auth,
                                 HttpServletRequest req,
                                 @Valid @RequestBody RateLimitReq body) {

        ObjectId tenantId = resolveTenantId(auth, req);

        Organization org = orgRepo.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "tenant_not_found"));

        RateLimitPolicy p = new RateLimitPolicy();
        p.setEnabled(body.enabled);

        RateLimitPolicy.BucketPolicy ak = new RateLimitPolicy.BucketPolicy();
        ak.setCapacity(body.apiKey.capacity);
        ak.setRefillTokens(body.apiKey.refillTokens);
        ak.setRefillSeconds(body.apiKey.refillSeconds);

        RateLimitPolicy.BucketPolicy tp = new RateLimitPolicy.BucketPolicy();
        tp.setCapacity(body.tenant.capacity);
        tp.setRefillTokens(body.tenant.refillTokens);
        tp.setRefillSeconds(body.tenant.refillSeconds);

        p.setApiKey(ak);
        p.setTenant(tp);

        org.setRateLimit(p);
        orgRepo.save(org);

        policyService.invalidate(org.getId());

        return ApiResponse.ok("Rate limit actualizado", "rate_limit_updated", p);
    }

    // ----------------- DELETE -----------------

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('ORG_OWNER','ROLE_ORG_OWNER') or hasRole('ORG_OWNER')")
    @Operation(
            summary = "Restaurar rate limit a default",
            description = "Elimina el override guardado (Organization.rateLimit = null) y reinvalida caches."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "tenant_not_found")
    })
    public ApiResponse<?> clear(Authentication auth, HttpServletRequest req) {

        ObjectId tenantId = resolveTenantId(auth, req);

        Organization org = orgRepo.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "tenant_not_found"));

        org.setRateLimit(null);
        orgRepo.save(org);

        policyService.invalidate(org.getId());

        return ApiResponse.ok("Rate limit restaurado a default", "rate_limit_cleared", null);
    }

    // ----------------- Helpers -----------------

    private ObjectId resolveTenantId(Authentication auth, HttpServletRequest req) {
        if (auth == null) throw new ResponseStatusException(UNAUTHORIZED, "unauthorized");

        Object principal = auth.getPrincipal();

        // 1) Intentar getTenantId()
        try {
            Method m = principal.getClass().getMethod("getTenantId");
            Object val = m.invoke(principal);
            if (val instanceof ObjectId oid) return oid;
            if (val instanceof String s && ObjectId.isValid(s)) return new ObjectId(s);
        } catch (Exception ignored) {}

        // 2) Map principal
        if (principal instanceof Map<?, ?> map) {
            Object tid = map.get("tenantId");
            if (tid instanceof String s && ObjectId.isValid(s)) return new ObjectId(s);
        }

        // 3) Header X-Tenant
        String headerTenant = req.getHeader("X-Tenant");
        if (headerTenant != null && ObjectId.isValid(headerTenant)) return new ObjectId(headerTenant);

        throw new ResponseStatusException(BAD_REQUEST, "tenant_id_not_found_in_auth");
    }

    private RateLimitPolicy fallbackPolicyFromYml() {
        RateLimitPolicy p = new RateLimitPolicy();
        p.setEnabled(rateLimitProps.isEnabled());

        RateLimitPolicy.BucketPolicy ak = new RateLimitPolicy.BucketPolicy();
        ak.setCapacity(rateLimitProps.getApiKey().getCapacity());
        ak.setRefillTokens(rateLimitProps.getApiKey().getRefillTokens());
        ak.setRefillSeconds(rateLimitProps.getApiKey().getRefillSeconds());

        RateLimitPolicy.BucketPolicy tp = new RateLimitPolicy.BucketPolicy();
        tp.setCapacity(rateLimitProps.getTenant().getCapacity());
        tp.setRefillTokens(rateLimitProps.getTenant().getRefillTokens());
        tp.setRefillSeconds(rateLimitProps.getTenant().getRefillSeconds());

        p.setApiKey(ak);
        p.setTenant(tp);

        return p;
    }
}
