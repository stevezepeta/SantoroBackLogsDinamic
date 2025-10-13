package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.Office;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OfficeRepository extends MongoRepository<Office, ObjectId> {
  Page<Office> findByTenantId(ObjectId tenantId, Pageable pageable);
  Page<Office> findByTenantIdAndCityContainingIgnoreCase(ObjectId tenantId, String city, Pageable pageable);
  Page<Office> findByTenantIdAndStatus(ObjectId tenantId, String status, Pageable pageable);
}
