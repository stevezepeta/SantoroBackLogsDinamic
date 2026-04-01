package backlogs.dinamico.service.catalog;

import backlogs.dinamico.model.catalog.Environment;
import backlogs.dinamico.repository.catalog.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @RequiredArgsConstructor
public class EnvironmentService {
  private final EnvironmentRepository repo;
  public Page<Environment> list(ObjectId tenantId, ObjectId systemId, String status, Pageable p){
    if(status!=null && !status.isBlank())
      return repo.findByTenantIdAndSystemIdAndStatus(tenantId, systemId, status, p);
    return repo.findByTenantIdAndSystemId(tenantId, systemId, p);
  }
  public Environment get(ObjectId id){ return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
  public Environment create(Environment b){ return repo.insert(b); }
  public void delete(ObjectId id){ if(!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND); repo.deleteById(id); }
}
