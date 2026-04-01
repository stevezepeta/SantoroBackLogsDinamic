package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.dto.OfficeCreatedRequest;
import backlogs.dinamico.api.dto.OfficeResponse;
import backlogs.dinamico.api.dto.OfficeUpdateRequest;
import backlogs.dinamico.service.catalog.OfficeService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogs/offices")
@RequiredArgsConstructor
@CrossOrigin
public class OfficeController {

  private final OfficeService service;

  /** GET -> devuelve el ARREGLO con la estructura pedida */
  @GetMapping
  public List<OfficeResponse> listAll() {
    return service.listAllForOrg();
  }

  /** GET por id numérico (seq) */
  @GetMapping("/{id}")
  public OfficeResponse get(@PathVariable("id") ObjectId id) {
    return service.getBySeq(id);
  }

  /** POST -> recibe solo los campos solicitados */
  @PostMapping
  public ResponseEntity<OfficeResponse> create(@RequestBody OfficeCreatedRequest body) {
    var created = service.create(body);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /** PATCH/PUT -> actualiza por id numérico */
  @PatchMapping("/{id}")
  public OfficeResponse update(@PathVariable("id") ObjectId id, @RequestBody OfficeUpdateRequest patch) {
    return service.updateBySeq(id, patch);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable("id") ObjectId id) {
    service.deleteBySeq(id);
  }

}
