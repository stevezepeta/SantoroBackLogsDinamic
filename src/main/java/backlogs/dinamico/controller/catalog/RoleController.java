package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.service.core.RoleService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogs/roles")
@RequiredArgsConstructor
@CrossOrigin
public class RoleController {
  private final RoleService service;

  @GetMapping
  public Page<Role> list(@RequestParam(required = false) String q, Pageable pageable) {
    return service.list(q, pageable);
  }

  @GetMapping("/{id}")
  public Role get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<Role> create(@RequestBody Role body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
