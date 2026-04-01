package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.catalog.EventType;
import backlogs.dinamico.service.catalog.EventTypeService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogs/event-types")
@RequiredArgsConstructor
@CrossOrigin
public class EventTypeController {
  private final EventTypeService service;

  @GetMapping
  public Page<EventType> list(@RequestParam ObjectId tenantId,
                              @RequestParam(required = false) String q,
                              Pageable pageable) {
    return service.list(tenantId, q, pageable);
  }

  @GetMapping("/{id}")
  public EventType get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<EventType> create(@RequestBody EventType body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
