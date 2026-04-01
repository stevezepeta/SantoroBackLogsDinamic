package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.catalog.ErrorCode;
import backlogs.dinamico.service.catalog.ErrorCodeService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogs/error-codes")
@RequiredArgsConstructor
@CrossOrigin
public class ErrorCodeController {
  private final ErrorCodeService service;

  @GetMapping
  public Page<ErrorCode> list(@RequestParam ObjectId tenantId,
                              @RequestParam ObjectId systemId,
                              @RequestParam(required = false) String severity,
                              @RequestParam(required = false) String q,
                              Pageable pageable) {
    return service.list(tenantId, systemId, severity, q, pageable);
  }

  @GetMapping("/{id}")
  public ErrorCode get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<ErrorCode> create(@RequestBody ErrorCode body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
