package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.catalog.SystemHealthDto;
import backlogs.dinamico.api.dto.catalog.SystemStatus24hItemDto;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.catalog.SystemApp;
import backlogs.dinamico.service.catalog.CatalogService;
import backlogs.dinamico.service.catalog.SystemAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Systems Catalog", description = "Catálogo de sistemas del tenant — respeta RBAC (allowedSystems)")
@RestController
@RequestMapping("/api/catalogs")
@RequiredArgsConstructor
@CrossOrigin
public class SystemAppController {

  private final SystemAppService service;
  private final CatalogService catalogService;

  @Operation(summary = "Listar sistemas visibles para el usuario autenticado",
             description = "VIEWER: devuelve solo sus allowedSystems. ORG_ADMIN/ORG_OWNER: devuelve todos.")
  @GetMapping("/systems")
  @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
  public ApiResponse<Page<SystemApp>> list(
          Authentication auth,
          @RequestParam(required = false) String status,
          @RequestParam(required = false) String q,
          Pageable pageable) {
    return ApiResponse.ok("Sistemas", "systems_list", service.list(auth, status, q, pageable));
  }

  @GetMapping("/systems/{id}")
  @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
  public ApiResponse<SystemApp> get(@PathVariable ObjectId id) {
    return ApiResponse.ok("Sistema", "system", service.get(id));
  }

  @PostMapping("/systems")
  @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN')")
  public ResponseEntity<ApiResponse<SystemApp>> create(@RequestBody SystemApp body) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok("Sistema creado", "system", service.create(body)));
  }

  @DeleteMapping("/systems/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN')")
  public void delete(@PathVariable ObjectId id) {
    service.delete(id);
  }

  /**
   * Salud operativa de los sistemas visibles para el usuario.
   * Si no se recibe periodo, evalúa las últimas 24 horas.
   * Un sistema sin eventos en el periodo se marca INACTIVE.
   */
  @Operation(summary = "Salud operativa de sistemas",
             description = "Devuelve systemCode, systemName, status, totalEvents, errorEvents y errorRate " +
                     "por sistema. El periodo es configurable mediante from/to; si se omiten, evalúa las últimas 24 horas.")
  @GetMapping("/systems-health")
  @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
  public ApiResponse<List<SystemHealthDto>> systemsHealth(
          Authentication auth,
          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
  ) {
    AuthUser user = extractUser(auth);
    List<String> allowedSystems = resolveAllowedSystems(user);

    List<SystemHealthDto> data = catalogService.getSystemsHealth(allowedSystems, from, to);
    return ApiResponse.ok("Salud de sistemas", "systems_health", data);
  }

  /**
   * Estado simplificado de los sistemas visibles para el menú desplegable.
   * Devuelve solo el nombre del sistema y su color de estado basado en el
   * día actual en el huso horario local.
   */
  @Operation(summary = "Estados 24h simplificados para menú",
             description = "Devuelve system + color para cada sistema visible. " +
                     "Evalúa las últimas 24 horas.")
  @GetMapping("/systems-status-24h")
  @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
  public ApiResponse<List<SystemStatus24hItemDto>> systemsStatus24h(Authentication auth) {
    AuthUser user = extractUser(auth);
    List<String> allowedSystems = resolveAllowedSystems(user);

    List<SystemStatus24hItemDto> data = catalogService.getSystemsStatus24h(allowedSystems);
    return ApiResponse.ok("Estados 24h", "systems_status_24h", data);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private AuthUser extractUser(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
      throw new org.springframework.web.server.ResponseStatusException(
              HttpStatus.UNAUTHORIZED, "unauthenticated");
    }
    return user;
  }

  private List<String> resolveAllowedSystems(AuthUser user) {
    boolean isAdminOrOwner = user.getRoles() != null &&
            (user.getRoles().contains("ORG_ADMIN") || user.getRoles().contains("ORG_OWNER"));

    boolean isUnrestricted = isAdminOrOwner ||
            (user.isOrgWide() && (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()));

    if (isUnrestricted) return null; // sin restricción

    if (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()) {
      return List.of();
    }

    Set<String> normalized = user.getAllowedSystems().stream()
            .filter(s -> s != null && !s.isBlank())
            .map(s -> s.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());

    return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
  }
}
