package backlogs.dinamico.controller.logs;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.service.logs.LogCommandService;
import backlogs.dinamico.service.logs.LogQueryService;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogQueryController {

  private final LogQueryService service;

  @GetMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> list(
          @RequestParam(required = false) String q,
          @RequestParam(required = false) String level,
          @RequestParam(required = false) String processType,
          @RequestParam(required = false) String device,
          @RequestParam(required = false) String scanDevice,
          @RequestParam(required = false) String scanType,
          @RequestParam(required = false) String officeId,
          @RequestParam(required = false) String personId,
          @RequestParam(required = false) String baseCode,
          @RequestParam(required = false) String errorCode,
          @RequestParam(required = false) String sessionToken,
          @RequestParam(required = false) String system,
          @RequestParam(required = false) String environment,
          @RequestParam(required = false) Instant from,
          @RequestParam(required = false) Instant to,
          @RequestParam(defaultValue = "1") int page,
          @RequestParam(defaultValue = "20") int size,
          @RequestParam(defaultValue = "timestamp") String sort,
          @RequestParam(defaultValue = "desc") String order
  ) {

    ObjectId tenantId = TenantContext.getTenantId();

    var filters = new java.util.HashMap<String, String>();
    put(filters, "q", q);
    put(filters, "level", level);
    put(filters, "processType", processType);
    put(filters, "device", device);
    put(filters, "scanDevice", scanDevice);
    put(filters, "scanType", scanType);
    put(filters, "officeId", officeId);
    put(filters, "personId", personId);
    put(filters, "baseCode", baseCode);
    put(filters, "errorCode", errorCode);
    put(filters, "sessionToken", sessionToken);
    put(filters, "system", system);
    put(filters, "environment", environment);

    var result = service.list(tenantId, filters, from, to, page, size, sort, order);
    return ResponseEntity.ok(ApiResponse.ok("Logs", null, result));

  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable ObjectId id) {
    ObjectId tenantId = TenantContext.getTenantId();
    var doc = service.getOne(tenantId, id);

    return ResponseEntity.ok(ApiResponse.ok("Detalle del log", null, doc));
  }

  @GetMapping("/stats/summary")
  public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
          @RequestParam(required = false) Instant from,
          @RequestParam(required = false) Instant to
  ) {
    ObjectId tenantId = TenantContext.getTenantId();
    var data = service.summary(tenantId, from, to);

    return ResponseEntity.ok(ApiResponse.ok("Resumen", null, data));
  }

  // TIMELINE
  @GetMapping("/stats/timeline")
  public ResponseEntity<ApiResponse<Map<String, Object>>> timeline(
          @RequestParam(required = false) Instant from,
          @RequestParam(required = false) Instant to,
          @RequestParam(defaultValue = "hours") String bucket,
          @RequestParam(required = false) String level
  ) {

    ObjectId tenantId = TenantContext.getTenantId();
    var data = service.timeline(tenantId, from, to, bucket, level);
    return ResponseEntity.ok(ApiResponse.ok("Serie de tiempo", null, data));

  }

  private static void put(java.util.Map<String,String> m, String k, String v) {
    if (v != null && !v.isBlank()) m.put(k, v);
  }

}
