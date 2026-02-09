package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.config.RateLimitProperties;
import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.model.core.RateLimitPolicy;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.service.catalog.RateLimitPolicyService;
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

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping(value = "/api/admin/rate-limit", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RateLimitAdminController {

    private final OrganizationRepository orgRepo;
    private final RateLimitPolicyService policyService;
    private final RateLimitProperties rateLimitProps;

    // -------- DTOs --------
    @Data
    public static class BucketReq {
        @Min(1) private long capacity;
        @Min(1) private long refillTokens;
        @Min(1) private long refillSeconds;
    }

    @Data
    public static class RateLimitReq {
        private boolean enabled = true;
        @NotNull private BucketReq apiKey;
        @NotNull private BucketReq tenant;
    }

    @Data
    public static class RateLimitView {
        private String source;              // "ORG_OVERRIDE" | "DEFAULT_YML"
        private RateLimitPolicy stored;     // lo guardado en Organization.rateLimit (puede ser null)
        private RateLimitPolicy effective;  // lo que aplica realmente (resuelto)
    }

    // GET sin tenantId (lo toma del usuario logueado)
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORG_OWNER','ROLE_ORG_OWNER') or hasRole('ORG_OWNER')")
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

    // PUT sin tenantId (lo toma del usuario logueado)
    @PutMapping
    @PreAuthorize("hasAnyAuthority('ORG_OWNER','ROLE_ORG_OWNER') or hasRole('ORG_OWNER')")
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

        // limpia cachés/resolución
        policyService.invalidate(org.getId());

        return ApiResponse.ok("Rate limit actualizado", "rate_limit_updated", p);
    }

    // DELETE reset a default (quita override)
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('ORG_OWNER','ROLE_ORG_OWNER') or hasRole('ORG_OWNER')")
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

        // 1) Intentar método getTenantId() en tu principal (si existe)
        try {
            Method m = principal.getClass().getMethod("getTenantId");
            Object val = m.invoke(principal);
            if (val instanceof ObjectId oid) return oid;
            if (val instanceof String s && ObjectId.isValid(s)) return new ObjectId(s);
        } catch (Exception ignored) {}

        // 2) Intentar si principal es Map (algunas implementaciones)
        if (principal instanceof Map<?, ?> map) {
            Object tid = map.get("tenantId");
            if (tid instanceof String s && ObjectId.isValid(s)) return new ObjectId(s);
        }

        // 3) Fallback: header X-Tenant (si tu UI lo manda)
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
