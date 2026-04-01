package backlogs.dinamico.repository.ai;

import backlogs.dinamico.model.ai.AiMetricRecord;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiMetricRepository extends MongoRepository<AiMetricRecord, ObjectId> {

    // ========= UPSERT por system (clave única: tenant+granularity+bucketStart+system) =========
    Optional<AiMetricRecord> findFirstByTenantIdAndGranularityAndBucketStartAndSystem(
            ObjectId tenantId, String granularity, Instant bucketStart, String system
    );

    // ========= Trend / Anomalías (histórico por system) =========
    List<AiMetricRecord> findTop120ByTenantIdAndGranularityAndSystemOrderByBucketStartDesc(
            ObjectId tenantId, String granularity, String system
    );

    // ========= EndPoint Paginacion ==============
    Page<AiMetricRecord> findByTenantIdAndGranularity(
            ObjectId tenantId, String granularity, Pageable pageable
    );

    Page<AiMetricRecord> findByTenantIdAndGranularityAndSystem(
            ObjectId tenantId, String granularity, String system, Pageable pageable
    );

    // ========= Series para gráficas (por system, rango de fechas) =========
    List<AiMetricRecord> findByTenantIdAndGranularityAndSystemAndBucketStartBetweenOrderByBucketStartAsc(
            ObjectId tenantId, String granularity, String system, Instant from, Instant to
    );

    // ========= (Opcional) Series globales sin system (dashboard general) =========
    List<AiMetricRecord> findByTenantIdAndGranularityAndBucketStartBetweenOrderByBucketStartAsc(
            ObjectId tenantId, String granularity, Instant from, Instant to
    );


}