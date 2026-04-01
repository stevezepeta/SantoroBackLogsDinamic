package backlogs.dinamico.service.catalog;

import backlogs.dinamico.model.catalog.EventType;
import backlogs.dinamico.repository.catalog.EventTypeRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @RequiredArgsConstructor
public class EventTypeService {
  private final EventTypeRepository repo;
  public Page<EventType> list(ObjectId tenantId, String q, Pageable p){
    return (q!=null && !q.isBlank())
        ? repo.findByTenantIdAndCodeContainingIgnoreCase(tenantId, q, p)
        : repo.findByTenantId(tenantId, p);
  }
  public EventType get(ObjectId id){ return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
  public EventType create(EventType b){ return repo.insert(b); }
  public void delete(ObjectId id){ if(!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND); repo.deleteById(id); }
}
