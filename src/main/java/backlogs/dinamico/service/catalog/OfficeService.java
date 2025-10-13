package backlogs.dinamico.service.catalog;

import backlogs.dinamico.model.catalog.Office;
import backlogs.dinamico.repository.catalog.OfficeRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @RequiredArgsConstructor
public class OfficeService {
  private final OfficeRepository repo;
  public Page<Office> list(ObjectId tenantId, String city, String status, Pageable p){
    if(status!=null && !status.isBlank()) return repo.findByTenantIdAndStatus(tenantId, status, p);
    if(city!=null && !city.isBlank()) return repo.findByTenantIdAndCityContainingIgnoreCase(tenantId, city, p);
    return repo.findByTenantId(tenantId, p);
  }
  public Office get(ObjectId id){ return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
  public Office create(Office b){ return repo.insert(b); }
  public void delete(ObjectId id){ if(!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND); repo.deleteById(id); }
}
