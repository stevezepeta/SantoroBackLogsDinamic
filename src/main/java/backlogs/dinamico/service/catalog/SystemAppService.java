package backlogs.dinamico.service.catalog;

import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.catalog.SystemApp;
import backlogs.dinamico.repository.catalog.SystemAppRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SystemAppService {

  private final SystemAppRepository repo;

  /**
   * Lista los sistemas visibles para el usuario autenticado.
   * - ORG_ADMIN / ORG_OWNER / orgWide sin sistemas asignados → ve todos
   * - Usuario con allowedSystems → solo los sistemas en su lista
   */
  public Page<SystemApp> list(Authentication auth, String status, String q, Pageable pageable) {
    AuthUser user = extractUser(auth);
    ObjectId tenantId = user.getTenantId();

    boolean isAdminOrOwner = user.getRoles() != null &&
        (user.getRoles().contains("ORG_ADMIN") || user.getRoles().contains("ORG_OWNER"));

    // Determinar si el usuario tiene restricciones de sistemas
    boolean isUnrestricted = isAdminOrOwner ||
        (user.isOrgWide() && (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()));

    if (isUnrestricted) {
      // --- Sin restricciones: devuelve todos ---
      if (status != null && !status.isBlank())
        return repo.findByTenantIdAndStatus(tenantId, status, pageable);
      if (q != null && !q.isBlank())
        return repo.findByTenantIdAndNameContainingIgnoreCase(tenantId, q, pageable);
      return repo.findByTenantId(tenantId, pageable);
    }

    // --- Con restricciones: filtra por allowedSystems ---
    List<String> allowed = user.getAllowedSystems();
    if (allowed == null || allowed.isEmpty())
      return new PageImpl<>(List.of(), pageable, 0);

    // Normalizar a uppercase para coincidir con SystemApp.code
    Set<String> codes = allowed.stream()
        .filter(s -> s != null && !s.isBlank())
        .map(s -> s.trim().toUpperCase(Locale.ROOT))
        .collect(Collectors.toSet());

    if (status != null && !status.isBlank())
      return repo.findByTenantIdAndCodeInAndStatus(tenantId, codes, status, pageable);
    if (q != null && !q.isBlank())
      return repo.findByTenantIdAndCodeInAndNameContainingIgnoreCase(tenantId, codes, q, pageable);
    return repo.findByTenantIdAndCodeIn(tenantId, codes, pageable);
  }

  public SystemApp get(ObjectId id) {
    return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
  }

  public SystemApp create(SystemApp body) {
    return repo.insert(body);
  }

  public void delete(ObjectId id) {
    if (!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND);
    repo.deleteById(id);
  }

  // ── Helper ────────────────────────────────────────────────────────────────

  private AuthUser extractUser(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof AuthUser user))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
    return user;
  }
}
