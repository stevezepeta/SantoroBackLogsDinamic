package backlogs.dinamico.repository.log;

import backlogs.dinamico.model.log.LogEvent;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;
public interface LogEventRepository extends MongoRepository<LogEvent, ObjectId> {

    @Query("{ '_id': ?0, 'tenant_id': ?1 }")
    Optional<LogEvent> findByIdAndTenantId(ObjectId id, ObjectId tenantId);

}
