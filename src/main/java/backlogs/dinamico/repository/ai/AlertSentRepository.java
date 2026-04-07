package backlogs.dinamico.repository.ai;

import backlogs.dinamico.model.ai.AlertSentRecord;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AlertSentRepository extends MongoRepository<AlertSentRecord, ObjectId> {

    Optional<AlertSentRecord> findByTenantIdAndSystemAndDayKey(
            ObjectId tenantId, String system, String dayKey
    );

}
