package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.catalog.Environment;
import backlogs.dinamico.service.catalog.EnvironmentService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogs/environments")
@RequiredArgsConstructor
@CrossOrigin
public class EnvironmentController {
  private final EnvironmentService service;

  @GetMapping
  public Page<Environment> list(@RequestParam ObjectId tenantId,
                                @RequestParam ObjectId systemId,
                                @RequestParam(required = false) String status,
                                Pageable pageable) {
    return service.list(tenantId, systemId, status, pageable);
  }

  @GetMapping("/{id}")
  public Environment get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<Environment> create(@RequestBody Environment body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
