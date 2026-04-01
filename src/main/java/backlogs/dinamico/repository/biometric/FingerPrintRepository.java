package backlogs.dinamico.repository.biometric;

import backlogs.dinamico.model.biometric.FingerPrint;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FingerPrintRepository extends MongoRepository<FingerPrint, ObjectId> {

    Optional<FingerPrint> findByTenantIdAndPersonId(ObjectId tenantId, ObjectId personId);

}
