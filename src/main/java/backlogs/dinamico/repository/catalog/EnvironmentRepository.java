package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.Environment;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EnvironmentRepository extends MongoRepository<Environment, ObjectId> {
  Page<Environment> findByTenantIdAndSystemId(ObjectId tenantId, ObjectId systemId, Pageable pageable);
  Page<Environment> findByTenantIdAndSystemIdAndStatus(ObjectId tenantId, ObjectId systemId, String status, Pageable pageable);
}
