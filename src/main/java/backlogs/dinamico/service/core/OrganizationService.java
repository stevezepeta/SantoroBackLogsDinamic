package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.repository.core.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @RequiredArgsConstructor
public class OrganizationService {
  private final OrganizationRepository repo;
  public Page<Organization> list(String status, String q, Pageable p){
    if(status!=null && !status.isBlank()) return repo.findByStatus(status, p);
    if(q!=null && !q.isBlank()) return repo.findByNameContainingIgnoreCase(q, p);
    return repo.findAll(p);
  }
  public Organization get(ObjectId id){ return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
  public Organization create(Organization b){ return repo.insert(b); }
  public void delete(ObjectId id){ if(!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND); repo.deleteById(id); }
}
