package backlogs.dinamico.repository.ai;

import backlogs.dinamico.model.ai.AiAlertRecord;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiAlertRepository extends MongoRepository<AiAlertRecord, ObjectId> {

    Optional<AiAlertRecord> findFirstByTenantIdAndFingerprint(ObjectId tenantId, String fingerprint);

    Page<AiAlertRecord> findByTenantId(ObjectId tenantId, Pageable pageable);

    Page<AiAlertRecord> findByTenantIdAndState(ObjectId tenantId, String state, Pageable pageable);

    Page<AiAlertRecord> findByTenantIdAndStateAndStatus(ObjectId tenantId, String state, String status, Pageable pageable);

    Page<AiAlertRecord> findByTenantIdAndGranularity(ObjectId tenantId, String granularity, Pageable pageable);

    Page<AiAlertRecord> findByTenantIdAndGranularityAndState(ObjectId tenantId, String granularity, String state, Pageable pageable);

    // ===================================== ====================================
    List<AiAlertRecord> findByTenantIdAndGranularityAndBucketStartBetweenOrderByBucketStartAsc(
            ObjectId tenantId, String granularity, Instant from, Instant to);

    List<AiAlertRecord> findTop60ByTenantIdAndGranularityOrderByBucketStartDesc(
            ObjectId tenantId, String granularity);

    List<AiAlertRecord> findTop60ByTenantIdAndGranularityAndStatusOrderByBucketStartDesc(
            ObjectId tenantId, String granularity, String status);

}
