package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.service.core.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogs/organizations")
@RequiredArgsConstructor
@CrossOrigin
public class OrganizationController {
  private final OrganizationService service;

  @GetMapping
  public Page<Organization> list(@RequestParam(required = false) String status,
                                 @RequestParam(required = false) String q,
                                 Pageable pageable) {
    return service.list(status, q, pageable);
  }

  @GetMapping("/{id}")
  public Organization get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<Organization> create(@RequestBody Organization body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
