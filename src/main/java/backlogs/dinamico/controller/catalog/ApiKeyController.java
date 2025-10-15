package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.catalog.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRepository;
import lombok.RequiredArgsConstructor;

import org.bson.types.ObjectId;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;

@RestController
@RequestMapping("/api/catalogs/api-keys")   // << ruta que estás llamando desde Postman
@CrossOrigin
@RequiredArgsConstructor
public class ApiKeyController {

  private final ApiKeyRepository repo;

  // --------- Helpers ----------
  private static String newKey() {
    byte[] bytes = new byte[30];
    new SecureRandom().nextBytes(bytes);
    return "ak_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  // --------- Endpoints ----------
  @PostMapping
  public ResponseEntity<ApiKey> create(@RequestBody ApiKey body) {
    body.setId(null);                       // lo genera Mongo
    if (body.getStatus() == null) body.setStatus("active");
    if (body.getKey() == null || body.getKey().isBlank()) {
      body.setKey(newKey());                // genera la clave si no la envías
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(body));
  }

@GetMapping("/{id}")
public ApiKey get(@PathVariable ObjectId id) {
  return repo.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
}

@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable ObjectId id) {
  if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  repo.deleteById(id);
}
}
