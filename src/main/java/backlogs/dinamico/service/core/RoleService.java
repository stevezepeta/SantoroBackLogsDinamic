package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.repository.core.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @RequiredArgsConstructor
public class RoleService {
  private final RoleRepository repo;

  public Page<Role> list(String q, Pageable p) {
    return (q != null && !q.isBlank())
        ? repo.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(q, q, p)
        : repo.findAll(p);
  }
  public Role get(ObjectId id){ return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)); }
  public Role create(Role b){ return repo.insert(b); }
  public void delete(ObjectId id){ if(!repo.existsById(id)) throw new ResponseStatusException(NOT_FOUND); repo.deleteById(id); }
}
