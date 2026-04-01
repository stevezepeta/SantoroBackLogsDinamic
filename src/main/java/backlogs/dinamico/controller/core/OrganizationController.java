package backlogs.dinamico.controller.core;

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
  public Page<Organization> list(@RequestParam(required = false) String q,
                                 Pageable pageable) {

    return service.list(q, pageable);
  }

  @GetMapping("/{id}")
  public Organization get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<Organization> create(@RequestBody Organization body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @PutMapping("/{id}")
  public Organization update(@PathVariable ObjectId id,
                             @RequestBody Organization patch) {
    return service.update(id, patch);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }

}
