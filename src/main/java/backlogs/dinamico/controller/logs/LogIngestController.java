package backlogs.dinamico.controller.logs;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.LogIngestBatchReq;
import backlogs.dinamico.api.dto.LogIngestReq;
import backlogs.dinamico.model.log.LogEntry;
import backlogs.dinamico.service.logs.LogCommandService;
import backlogs.dinamico.service.logs.LogIngestService;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogIngestController {

    private final LogIngestService service;
    private final LogCommandService commandService;

//    @PostMapping
//    @PreAuthorize("isAuthenticated()")
//    public ResponseEntity<ApiResponse<Map<String, Object>>> ingestOne(@Valid @RequestBody LogIngestReq body) {
//
//        LogEntry saved = service.ingestOne(body);
//
//        Map<String, Object> data = new java.util.LinkedHashMap<>();
//        data.put("id", saved.getId().toHexString());
//        data.put("timestamp", saved.getTimestamp());
//        data.put("level", saved.getLevel());
//
//        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
//                .body(ApiResponse.created("Log almacenado", "/api/logs", data));
//
//    }

//    @PostMapping("/batch")
//    @PreAuthorize("isAuthenticated()")
//    public ResponseEntity<ApiResponse<Map<String, Object>>> ingestBatch(@Valid @RequestBody LogIngestBatchReq body) {
//
//        List<LogEntry> saved = service.ingestBatch(body);
//
//        Map<String, Object> data = new java.util.LinkedHashMap<>();
//        data.put("count", saved.size());
//        data.put("firstId", saved.isEmpty() ? null : saved.get(0).getId().toHexString());
//        data.put("ingestAt", java.time.Instant.now());
//
//        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
//                .body(ApiResponse.created("Lote almacenado", "/api/logs", data));
//
//    }

    // Este reemplaza al antiguo ingestOne(LogIngestReq)
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> ingestOne(
            @Valid @RequestBody LogCreateRequest req) {

        ObjectId tenantId = TenantContext.getTenantId();

        // Crea el log, valida oficina/persona, etc. y devuelve el formato legacy
        var legacy = commandService.create(tenantId, req);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("log_created", null, legacy));
    }

}
