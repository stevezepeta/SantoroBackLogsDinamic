package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.service.core.RoleService;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/catalogs/roles")
@RequiredArgsConstructor
@CrossOrigin
public class RoleController {

  private final RoleService service;

  @GetMapping
  public ResponseEntity<?> list(
          @RequestParam(name = "q", required = false) String q,
          @RequestParam(name = "page", defaultValue = "0") Integer page,
          @RequestParam(name = "size", defaultValue = "10") Integer size
  ) {
    try {
      // Logs de entrada
      log.info("[ROLES] GET list q='{}' page={} size={} tenant={}",
              q, page, size, TenantContext.getTenantIdHex());

      if (page == null || page < 0) page = 0;
      if (size == null || size <= 0 || size > 200) size = 10;

      Page<Role> pageData = service.listSafe(q, page, size);

      var dtoList = pageData.getContent().stream()
              .map(r -> new RoleDto(
                      r.getId() != null ? r.getId().toHexString() : null,
                      r.getCode(),
                      r.getName()
              ))
              .toList();

      var body = new PageDto<>(pageData.getNumber(), pageData.getSize(), pageData.getTotalElements(), dtoList);

      log.info("[ROLES] OK page={} size={} total={}", body.page, body.size, body.total);
      return ResponseEntity.ok(body);

    } catch (Exception ex) {
      // Siempre deja un log útil
      log.error("[ROLES] ERROR list: {}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
              .body(new Err("roles_list_failed", ex.getMessage()));
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable ObjectId id) {
    try {
      Role r = service.get(id);
      return ResponseEntity.ok(new RoleDto(
              r.getId() != null ? r.getId().toHexString() : null,
              r.getCode(),
              r.getName()
      ));
    } catch (Exception ex) {
      log.error("[ROLES] ERROR get {}: {}: {}", id, ex.getClass().getSimpleName(), ex.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(new Err("role_not_found", "Role not found or not in tenant"));
    }
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody Role body) {
    try {
      Role saved = service.create(body);
      return ResponseEntity.status(HttpStatus.CREATED).body(new RoleDto(
              saved.getId() != null ? saved.getId().toHexString() : null,
              saved.getCode(),
              saved.getName()
      ));
    } catch (Exception ex) {
      log.error("[ROLES] ERROR create: {}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
              .body(new Err("role_create_failed", ex.getMessage()));
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable ObjectId id) {
    try {
      service.delete(id);
      return ResponseEntity.noContent().build();
    } catch (Exception ex) {
      log.error("[ROLES] ERROR delete {}: {}: {}", id, ex.getClass().getSimpleName(), ex.getMessage(), ex);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(new Err("role_delete_failed", "Role not found or not in tenant"));
    }
  }

  // ===== DTOs =====
  @Value static class Err { String error; String message; }
  @Value static class RoleDto { String id; String code; String name; }
  @Value static class PageDto<T> { int page; int size; long total; List<T> data; }
}
