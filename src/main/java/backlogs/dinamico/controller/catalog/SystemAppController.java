package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.catalog.SystemApp;
import backlogs.dinamico.service.catalog.SystemAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Systems Catalog", description = "Catálogo de sistemas del tenant — respeta RBAC (allowedSystems)")
@RestController
@RequestMapping("/api/catalogs/systems")
@RequiredArgsConstructor
@CrossOrigin
public class SystemAppController {

  private final SystemAppService service;

  @Operation(summary = "Listar sistemas visibles para el usuario autenticado",
             description = "VIEWER: devuelve solo sus allowedSystems. ORG_ADMIN/ORG_OWNER: devuelve todos.")
  @GetMapping
  public ApiResponse<Page<SystemApp>> list(
          Authentication auth,
          @RequestParam(required = false) String status,
          @RequestParam(required = false) String q,
          Pageable pageable) {
    return ApiResponse.ok("Sistemas", "systems_list", service.list(auth, status, q, pageable));
  }

  @GetMapping("/{id}")
  public ApiResponse<SystemApp> get(@PathVariable ObjectId id) {
    return ApiResponse.ok("Sistema", "system", service.get(id));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SystemApp>> create(@RequestBody SystemApp body) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok("Sistema creado", "system", service.create(body)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }
}
