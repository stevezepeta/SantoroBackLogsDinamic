package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.catalog.ApiKeyView;
import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.service.catalog.ApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/catalogs/api-keys", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService service;

    public record CreateReq(
            @NotBlank String name,
            String systemId,
            String environmentId,
            List<String> scopes,
            Long ttlDays,
            Long rotateDays
    ) {}

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateReq req) {

        ObjectId sys = (StringUtils.hasText(req.systemId()) && ObjectId.isValid(req.systemId()))
                ? new ObjectId(req.systemId()) : null;

        ObjectId env = (StringUtils.hasText(req.environmentId()) && ObjectId.isValid(req.environmentId()))
                ? new ObjectId(req.environmentId()) : null;

        var created = service.create(req.name(), sys, env, req.scopes(), req.ttlDays(), req.rotateDays());
        ApiKey k = created.apiKey();

        // LinkedHashMap permite nulls (Map.of NO)
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", k.getId() != null ? k.getId().toHexString() : null);
        data.put("name", k.getName());
        data.put("status", k.getStatus());
        data.put("scopes", k.getScopes());
        data.put("systemId", k.getSystemId() != null ? k.getSystemId().toHexString() : null);
        data.put("environmentId", k.getEnvironmentId() != null ? k.getEnvironmentId().toHexString() : null);
        data.put("expiresAt", k.getExpiresAt());     // puede ser null
        data.put("rotatesAt", k.getRotatesAt());     // puede ser null
        data.put("apiKey", created.plainKey());      // solo una vez

        return ApiResponse.created("API Key creada", "api_key_created", data);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        Page<ApiKeyView> result = service.listMine(status, q, page, size);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result.getContent());
        data.put("page", Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalItems", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "hasNext", result.hasNext()
        ));

        return ApiResponse.ok("API Keys", "api_keys_list", data);
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> rotate(@PathVariable String id) {
        if (!ObjectId.isValid(id)) throw new IllegalArgumentException("invalid_id");

        var created = service.rotate(new ObjectId(id));
        ApiKey k = created.apiKey();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", k.getId() != null ? k.getId().toHexString() : null);
        data.put("name", k.getName());
        data.put("status", k.getStatus());
        data.put("scopes", k.getScopes());
        data.put("apiKey", created.plainKey());

        return ApiResponse.ok("API Key rotada", "api_key_rotated", data);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> disable(@PathVariable String id) {
        if (!ObjectId.isValid(id)) throw new IllegalArgumentException("invalid_id");

        ApiKey k = service.disable(new ObjectId(id));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", k.getId() != null ? k.getId().toHexString() : null);
        data.put("status", k.getStatus());

        return ApiResponse.ok("API Key desactivada", "api_key_disabled", data);
    }

    public record RenewReq(
            Long ttlDays,
            Long rotateDays
    ) {}

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> renew(@PathVariable String id,
                                                  @Valid @RequestBody(required = false) RenewReq req) {

        if (!ObjectId.isValid(id)) throw new IllegalArgumentException("invalid_id");

        Long ttlDays = (req != null) ? req.ttlDays() : null;
        Long rotateDays = (req != null) ? req.rotateDays() : null;

        ApiKey k = service.renew(new ObjectId(id), ttlDays, rotateDays);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", k.getId() != null ? k.getId().toHexString() : null);
        data.put("name", k.getName());
        data.put("status", k.getStatus());
        data.put("scopes", k.getScopes());
        data.put("systemId", k.getSystemId() != null ? k.getSystemId().toHexString() : null);
        data.put("environmentId", k.getEnvironmentId() != null ? k.getEnvironmentId().toHexString() : null);
        data.put("expiresAt", k.getExpiresAt());
        data.put("rotatesAt", k.getRotatesAt());

        // 👇 NOTA: NO se regresa apiKey plaintext aquí
        return ApiResponse.ok("API Key renovada", "api_key_renewed", data);
    }


}
