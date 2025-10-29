package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.*;
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

  public Page<Role> listSafe(String q, Integer page, Integer size) {
    String tenantHex = TenantContext.getTenantIdHex();
    if (!StringUtils.hasText(tenantHex)) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    ObjectId tenantId = new ObjectId(tenantHex);

    if (page == null || page < 0) page = 0;
    if (size == null || size <= 0 || size > 200) size = 10;
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));

    try {
      if (StringUtils.hasText(q)) {
        return repo.searchByTenantAndCodeOrName(tenantId, q, pageable);
      }
      return repo.findByTenantId(tenantId, pageable);
    } catch (IllegalArgumentException | DataAccessException ex) {
      log.error("[RoleService] Query failed tenant={} q='{}' page={} size={} -> {}: {}",
              tenantId.toHexString(), q, page, size, ex.getClass().getSimpleName(), ex.getMessage(), ex);

      throw new ResponseStatusException(BAD_REQUEST, "invalid_roles_query: " + ex.getMessage(), ex);
    }
  }

  public Page<Role> list(String q, Pageable pageable) {
    String tenantHex = TenantContext.getTenantIdHex();
    if (!StringUtils.hasText(tenantHex)) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    ObjectId tenantId = new ObjectId(tenantHex);
    try {
      if (StringUtils.hasText(q)) {
        return repo.searchByTenantAndCodeOrName(tenantId, q, pageable);
      }
      return repo.findByTenantId(tenantId, pageable);
    } catch (IllegalArgumentException | DataAccessException ex) {
      log.error("[RoleService] Query failed tenant={} q='{}' pageable={} -> {}: {}",
              tenantId.toHexString(), q, pageable, ex.getClass().getSimpleName(), ex.getMessage(), ex);
      throw new ResponseStatusException(BAD_REQUEST, "invalid_roles_query: " + ex.getMessage(), ex);
    }
  }

  public Role get(ObjectId id) {
    String tenantHex = TenantContext.getTenantIdHex();
    if (!StringUtils.hasText(tenantHex)) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    ObjectId tenantId = new ObjectId(tenantHex);

    Role r = repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "role_not_found"));
    if (r.getTenantId() == null || !tenantId.equals(r.getTenantId())) {
      throw new ResponseStatusException(NOT_FOUND, "role_not_found");
    }
    return r;
  }

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

    if (StringUtils.hasText(body.getCode())) {
      repo.findByTenantIdAndCode(tenantId, body.getCode()).ifPresent(x -> {
        throw new ResponseStatusException(BAD_REQUEST, "role_code_already_exists");
      });
    }

    return repo.save(body);
  }

  public void delete(ObjectId id) {
    String tenantHex = TenantContext.getTenantIdHex();
    if (!StringUtils.hasText(tenantHex)) {
      throw new ResponseStatusException(BAD_REQUEST, "missing_tenant_ctx");
    }
    ObjectId tenantId = new ObjectId(tenantHex);

    Role existing = repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "role_not_found"));
    if (existing.getTenantId() == null || !tenantId.equals(existing.getTenantId())) {
      throw new ResponseStatusException(NOT_FOUND, "role_not_found");
    }
    repo.deleteById(id);
  }
}
