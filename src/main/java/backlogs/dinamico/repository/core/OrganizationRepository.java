package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.Organization;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizationRepository extends MongoRepository<Organization, ObjectId> {
  Page<Organization> findByStatus(String status, Pageable pageable);
  Page<Organization> findByNameContainingIgnoreCase(String name, Pageable pageable);

  boolean existsByIdAndStatus(ObjectId id, String status);
}
