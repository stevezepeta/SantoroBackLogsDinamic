package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.EventType;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventTypeRepository extends MongoRepository<EventType, ObjectId> {
  Page<EventType> findByTenantId(ObjectId tenantId, Pageable pageable);
  Page<EventType> findByTenantIdAndCodeContainingIgnoreCase(ObjectId tenantId, String code, Pageable pageable);
}
