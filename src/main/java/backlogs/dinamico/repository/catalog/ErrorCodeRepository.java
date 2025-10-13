package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.ErrorCode;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ErrorCodeRepository extends MongoRepository<ErrorCode, ObjectId> {
  Page<ErrorCode> findByTenantIdAndSystemId(ObjectId tenantId, ObjectId systemId, Pageable pageable);
  Page<ErrorCode> findByTenantIdAndSystemIdAndSeverity(ObjectId tenantId, ObjectId systemId, String severity, Pageable pageable);
  Page<ErrorCode> findByTenantIdAndSystemIdAndCodeContainingIgnoreCase(ObjectId tenantId, ObjectId systemId, String code, Pageable pageable);
}
