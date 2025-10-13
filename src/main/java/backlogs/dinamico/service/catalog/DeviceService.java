package backlogs.dinamico.service.catalog;

import backlogs.dinamico.model.catalog.Device;
import backlogs.dinamico.repository.catalog.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @RequiredArgsConstructor
public class DeviceService {
  private final DeviceRepository repo;
  public Page<Device> list(ObjectId tenantId, ObjectId systemId, String status, String q, Pageable p){
    if(status!=null && !status.isBlank()) return repo.findByTenantIdAndSystemIdAndStatus(tenantId, systemId, status, p);
    if(q!=null && !q.isBlank()) return repo.findByTenantIdAndSystemIdAndCodeContainingIgnoreCase(tenantId, systemId, q, p);
    return repo.findByTenantIdAndSystemId(tenantId, systemId, p);
  }
  public Device get(ObjectId id){ return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
  public Device create(Device b){ return repo.insert(b); }
  public void delete(ObjectId id){ if(!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND); repo.deleteById(id); }
}
