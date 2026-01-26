package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.catalog.ApiKeyStatusResponse;
import backlogs.dinamico.api.dto.catalog.ApiKeyView;
import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.service.catalog.ApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    public ResponseEntity<?> create(@Valid @RequestBody CreateReq req,
                                    @RequestParam(name = "export", required = false) String export) {

        ObjectId sys = (StringUtils.hasText(req.systemId()) && ObjectId.isValid(req.systemId()))
                ? new ObjectId(req.systemId()) : null;

        ObjectId env = (StringUtils.hasText(req.environmentId()) && ObjectId.isValid(req.environmentId()))
                ? new ObjectId(req.environmentId()) : null;

        var created = service.create(req.name(), sys, env, req.scopes(), req.ttlDays(), req.rotateDays());
        ApiKey k = created.apiKey();

        // Exportar
        if (StringUtils.hasText(export)) {
            String fmt = export.trim().toLowerCase();
            if (!fmt.equals("txt") && !fmt.equals("json")) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("bad_request", "export must be txt|json", null));
            }

            String filenameBase = safeFileName(k.getName() != null ? k.getName() : "api-key");
            String filename = filenameBase + "-" + Instant.now().toEpochMilli() + "." + fmt;

            String content = fmt.equals("json")
                    ? exportJson(k, created.plainKey())
                    : exportTxt(k, created.plainKey());

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(fmt.equals("json") ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header("X-ApiKey-Id", k.getId() != null ? k.getId().toHexString() : "")
                    .body(bytes);
        }

        // Si no se exporta se devuelve el metaData
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", k.getId() != null ? k.getId().toHexString() : null);
        data.put("name", k.getName());
        data.put("status", k.getStatus());
        data.put("scopes", k.getScopes());
        data.put("systemId", k.getSystemId() != null ? k.getSystemId().toHexString() : null);
        data.put("environmentId", k.getEnvironmentId() != null ? k.getEnvironmentId().toHexString() : null);
        data.put("expiresAt", k.getExpiresAt());     // puede ser null
        data.put("rotatesAt", k.getRotatesAt());     // puede ser null

        // Sugerencia para el cliente: vuelva a llamar con export
        data.put("exportHint", "Call POST with ?export=txt or ?export=json to download the API Key one time");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Api Key creada", "api_key_created", data));
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

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ApiResponse<ApiKeyStatusResponse> status(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "7") int renewWindowDays,
            @RequestParam(defaultValue = "true") boolean includeDisabled
    ) {
        ApiKeyStatusResponse data = service.status(page, size, renewWindowDays, includeDisabled);
        return ApiResponse.ok("API Keys status", "api_keys_status", data);
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

        // NO se regresa apiKey plaintext aquí
        return ApiResponse.ok("API Key renovada", "api_key_renewed", data);
    }

    // ============== HELPER'S ===============
    private static String exportTxt(ApiKey k, String plainKey) {
        return ""
                + "API KEY EXPORT\n"
                + "=============\n"
                + "id: " + (k.getId() != null ? k.getId().toHexString() : "") + "\n"
                + "name: " + nullSafe(k.getName()) + "\n"
                + "status: " + nullSafe(k.getStatus()) + "\n"
                + "scopes: " + (k.getScopes() != null ? k.getScopes() : List.of()) + "\n"
                + "systemId: " + (k.getSystemId() != null ? k.getSystemId().toHexString() : "") + "\n"
                + "environmentId: " + (k.getEnvironmentId() != null ? k.getEnvironmentId().toHexString() : "") + "\n"
                + "rotatesAt: " + nullSafe(k.getRotatesAt()) + "\n"
                + "expiresAt: " + nullSafe(k.getExpiresAt()) + "\n"
                + "\n"
                + "X-Api-Key: " + plainKey + "\n";
    }

    private static String exportJson(ApiKey k, String plainKey) {
        // JSON simple (sin depender de ObjectMapper)
        return "{\n"
                + "  \"id\": \"" + (k.getId() != null ? k.getId().toHexString() : "") + "\",\n"
                + "  \"name\": \"" + escapeJson(nullSafe(k.getName())) + "\",\n"
                + "  \"status\": \"" + escapeJson(nullSafe(k.getStatus())) + "\",\n"
                + "  \"scopes\": " + (k.getScopes() != null ? k.getScopes().toString() : "[]") + ",\n"
                + "  \"systemId\": \"" + (k.getSystemId() != null ? k.getSystemId().toHexString() : "") + "\",\n"
                + "  \"environmentId\": \"" + (k.getEnvironmentId() != null ? k.getEnvironmentId().toHexString() : "") + "\",\n"
                + "  \"rotatesAt\": \"" + (k.getRotatesAt() != null ? k.getRotatesAt().toString() : "") + "\",\n"
                + "  \"expiresAt\": \"" + (k.getExpiresAt() != null ? k.getExpiresAt().toString() : "") + "\",\n"
                + "  \"header\": { \"X-Api-Key\": \"" + escapeJson(plainKey) + "\" }\n"
                + "}\n";
    }

    private static String safeFileName(String s) {
        String x = (s == null ? "api-key" : s.trim());
        x = x.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
        return x.isBlank() ? "api-key" : x;
    }

    private static String nullSafe(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
