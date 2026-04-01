package backlogs.dinamico.service.catalog;

import backlogs.dinamico.model.catalog.ErrorCode;
import backlogs.dinamico.repository.catalog.ErrorCodeRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @RequiredArgsConstructor
public class ErrorCodeService {
  private final ErrorCodeRepository repo;
  public Page<ErrorCode> list(ObjectId tenantId, ObjectId systemId, String severity, String q, Pageable p){
    if(severity!=null && !severity.isBlank()) return repo.findByTenantIdAndSystemIdAndSeverity(tenantId, systemId, severity, p);
    if(q!=null && !q.isBlank()) return repo.findByTenantIdAndSystemIdAndCodeContainingIgnoreCase(tenantId, systemId, q, p);
    return repo.findByTenantIdAndSystemId(tenantId, systemId, p);
  }
  public ErrorCode get(ObjectId id){ return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
  public ErrorCode create(ErrorCode b){ return repo.insert(b); }
  public void delete(ObjectId id){ if(!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND); repo.deleteById(id); }
}
