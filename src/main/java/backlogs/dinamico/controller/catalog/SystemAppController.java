package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.catalog.SystemApp;
import backlogs.dinamico.service.catalog.SystemAppService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogs/systems")
@RequiredArgsConstructor
@CrossOrigin
public class SystemAppController {
  private final SystemAppService service;

  @GetMapping
  public Page<SystemApp> list(@RequestParam ObjectId tenantId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String q,
                              Pageable pageable) {
    return service.list(tenantId, status, q, pageable);
  }

  @GetMapping("/{id}")
  public SystemApp get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<SystemApp> create(@RequestBody SystemApp body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
