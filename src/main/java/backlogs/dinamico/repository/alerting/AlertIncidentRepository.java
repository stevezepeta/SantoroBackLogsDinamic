package backlogs.dinamico.repository.alerting;

import backlogs.dinamico.model.alerting.AlertIncident;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface AlertIncidentRepository extends MongoRepository<AlertIncident, ObjectId> {

    Page<AlertIncident> findByTenantId(ObjectId tenantId, Pageable pageable);

    Page<AlertIncident> findByTenantIdAndStatus(ObjectId tenantId, String status, Pageable pageable);

    Optional<AlertIncident> findByIdAndTenantId(ObjectId id, ObjectId tenantId);

    @Query("{ 'tenantId': ?0, '$or': [ { 'context.deviceId': ?1 }, { 'context.caseId': ?1 }, { 'context.device_id': ?1 }, { 'context.id': ?1 } ] }")
    Optional<AlertIncident> findByTenantIdAndContextIdentifier(ObjectId tenantId, String identifier);
}
