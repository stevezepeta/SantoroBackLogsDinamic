package backlogs.dinamico.repository.log;

import backlogs.dinamico.model.log.LogEntry;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LogRepository extends MongoRepository<LogEntry, ObjectId> {


}
