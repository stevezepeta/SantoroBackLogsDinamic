package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.Office;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OfficeRepository extends MongoRepository<Office, ObjectId> {
  boolean existsByTenantIdAndNameIgnoreCase(ObjectId tenantId, String name);

  List<Office> findByTenantIdOrderBySeqAsc(ObjectId tenantId);

  Optional<Office> findByTenantIdAndSeq(ObjectId tenantId, Long seq);

  boolean existsByTenantIdAndSeq(ObjectId tenantId, Long seq);

  void deleteByTenantIdAndSeq(ObjectId tenantId, Long seq);
}
