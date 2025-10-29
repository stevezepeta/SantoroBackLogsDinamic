package backlogs.dinamico.repository.biometric;

import backlogs.dinamico.model.biometric.Person;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PersonRepository extends MongoRepository<Person, ObjectId> {

    Optional<Person> findByTenantIdAndCurp(ObjectId tenantId, String curp);

}
