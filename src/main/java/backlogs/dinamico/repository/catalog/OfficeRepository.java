package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.Office;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OfficeRepository extends MongoRepository<Office, ObjectId> {
  boolean existsByTenantIdAndNameIgnoreCase(ObjectId tenantId, String name);

  List<Office> findByTenantIdOrderByNameAsc(ObjectId tenantId);

  Optional<Office> findByTenantIdAndId(ObjectId tenantId, ObjectId id);

  boolean existsByTenantIdAndId(ObjectId tenantId, ObjectId id);

  void deleteByTenantIdAndId(ObjectId tenantId, ObjectId id);
}
