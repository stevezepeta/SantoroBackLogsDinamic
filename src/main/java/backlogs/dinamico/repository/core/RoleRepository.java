package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.Role;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface RoleRepository extends MongoRepository<Role, ObjectId> {
  Page<Role> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(String code, String name, Pageable pageable);

  long countByIdIn(ObjectId tenantId, Collection<ObjectId> ids);
}
