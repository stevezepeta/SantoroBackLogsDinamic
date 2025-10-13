package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.Device;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeviceRepository extends MongoRepository<Device, ObjectId> {
  Page<Device> findByTenantIdAndSystemId(ObjectId tenantId, ObjectId systemId, Pageable pageable);
  Page<Device> findByTenantIdAndSystemIdAndStatus(ObjectId tenantId, ObjectId systemId, String status, Pageable pageable);
  Page<Device> findByTenantIdAndSystemIdAndCodeContainingIgnoreCase(ObjectId tenantId, ObjectId systemId, String code, Pageable pageable);
}
