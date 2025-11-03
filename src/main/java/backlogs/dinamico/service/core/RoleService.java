package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

  private final RoleRepository repo;

  public Page<Role> list(ObjectId tenantId, String q, Pageable pageable) {
    if (tenantId == null) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    try {
      if (StringUtils.hasText(q)) {
        return repo.searchByTenantAndCodeOrName(tenantId, q, pageable);
      }
      return repo.findByTenantId(tenantId, pageable);
    } catch (IllegalArgumentException | DataAccessException ex) {
      log.error("[RoleService] list failed tenant={} q='{}' pageable={} -> {}: {}",
              tenantId.toHexString(), q, pageable, ex.getClass().getSimpleName(), ex.getMessage(), ex);
      throw new ResponseStatusException(BAD_REQUEST, "invalid_roles_query: " + ex.getMessage(), ex);
    }
  }

  public Page<Role> list(String q, Pageable pageable) {
    String tenantHex = TenantContext.getTenantIdHex();
    if (!StringUtils.hasText(tenantHex)) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    return list(new ObjectId(tenantHex), q, pageable);
  }

  // -------- GET -----------
  public Role get(ObjectId id) {
    String tenantHex = TenantContext.getTenantIdHex();
    if (!StringUtils.hasText(tenantHex)) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    ObjectId tenantId = new ObjectId(tenantHex);

    Role r = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "role_not_found"));

    if (r.getTenantId() == null || !tenantId.equals(r.getTenantId())) {
      throw new ResponseStatusException(NOT_FOUND, "role_not_found");
    }
    return r;
  }

  // ------- CREATE ----------
  public Role create(Role body) {
    String tenantHex = TenantContext.getTenantIdHex();
    if (!StringUtils.hasText(tenantHex)) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    ObjectId tenantId = new ObjectId(tenantHex);

    if (body.getTenantId() == null) {
      body.setTenantId(tenantId);
    } else if (!tenantId.equals(body.getTenantId())) {
      throw new ResponseStatusException(BAD_REQUEST, "invalid_tenant_in_payload");
    }

    // Se normaliza code y name
    if (StringUtils.hasText(body.getCode())) {
      body.setCode(body.getCode().trim());
    }
    if (StringUtils.hasText(body.getName())) {
      body.setName(body.getName().trim());
    }

    if (StringUtils.hasText(body.getCode())) {
      repo.findByTenantIdAndCode(tenantId, body.getCode()).ifPresent(x -> {
        throw new ResponseStatusException(BAD_REQUEST, "role_code_already_exists");
      });
    }

    return repo.save(body);
  }

  // ------- DELETE --------
  public void delete(ObjectId id) {
    String tenantHex = TenantContext.getTenantIdHex();
    if (!StringUtils.hasText(tenantHex)) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    ObjectId tenantId = new ObjectId(tenantHex);

    Role existing = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "role_not_found"));

    if (existing.getTenantId() == null || !tenantId.equals(existing.getTenantId())) {
      throw new ResponseStatusException(NOT_FOUND, "role_not_found");
    }
    repo.deleteById(id);
  }
}
