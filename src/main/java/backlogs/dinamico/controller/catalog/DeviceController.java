package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.catalog.Device;
import backlogs.dinamico.service.catalog.DeviceService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogs/devices")
@RequiredArgsConstructor
@CrossOrigin
public class DeviceController {
  private final DeviceService service;

  @GetMapping
  public Page<Device> list(@RequestParam ObjectId tenantId,
                           @RequestParam ObjectId systemId,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String q,
                           Pageable pageable) {
    return service.list(tenantId, systemId, status, q, pageable);
  }

  @GetMapping("/{id}")
  public Device get(@PathVariable ObjectId id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<Device> create(@RequestBody Device body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
