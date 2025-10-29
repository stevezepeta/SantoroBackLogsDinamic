package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.Organization;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizationRepository extends MongoRepository<Organization, ObjectId> {
  Page<Organization> findByStatus(String status, Pageable pageable);
  Page<Organization> findByNameContainingIgnoreCase(String name, Pageable pageable);

  Optional<Organization> findByCodeIgnoreCase(String code);

  Optional<Organization> findBySlug(String slug);
  Optional<Organization> findByDomainIgnoreCase(String domain);

  Optional<Organization> findBySlugIgnoreCase(String slug);
  boolean existsBySlugIgnoreCase(String slug);
  boolean existsByIdAndStatus(ObjectId id, String status);
  Optional<Organization> findByIdAndStatus(ObjectId is, String status);

}
