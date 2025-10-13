package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.catalog.Office;
import backlogs.dinamico.service.catalog.OfficeService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogs/offices")
@RequiredArgsConstructor
@CrossOrigin
public class OfficeController {
  private final OfficeService service;

  @GetMapping
  public Page<Office> list(@RequestParam ObjectId tenantId,
                           @RequestParam(required = false) String city,
                           @RequestParam(required = false) String status,
                           Pageable pageable) {
    return service.list(tenantId, city, status, pageable);
  }

  @GetMapping("/{id}")
  public Office get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<Office> create(@RequestBody Office body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
