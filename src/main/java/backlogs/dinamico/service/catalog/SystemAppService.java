package backlogs.dinamico.service.catalog;

import backlogs.dinamico.model.catalog.SystemApp;
import backlogs.dinamico.repository.catalog.SystemAppRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SystemAppService {
  private final SystemAppRepository repo;

  public Page<SystemApp> list(ObjectId tenantId, String status, String q, Pageable pageable) {
    if (status != null && !status.isBlank()) {
      return repo.findByTenantIdAndStatus(tenantId, status, pageable);
    }
    if (q != null && !q.isBlank()) {
      return repo.findByTenantIdAndNameContainingIgnoreCase(tenantId, q, pageable);
    }
    return repo.findByTenantId(tenantId, pageable);
  }

  public SystemApp get(ObjectId id) {
    return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
  }

  public SystemApp create(SystemApp body) {
    // evita problemas con setId(null): usa insert
    return repo.insert(body);
  }

  public void delete(ObjectId id) {
    if (!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND);
    repo.deleteById(id);
  }
}
