package backlogs.dinamico.repository.passport;

import backlogs.dinamico.model.passport.PassportEvent;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PassportEventRepository extends MongoRepository<PassportEvent, ObjectId> {

    List<PassportEvent> findByTenantIdAndEventTimeBetween(
            ObjectId tenantId,
            Instant from,
            Instant to
    );

    List<PassportEvent> findByTenantIdOrderByEventTimeDesc(ObjectId tenantId);

    Page<PassportEvent> findByTenantId(ObjectId tenantId, Pageable pageable);

    Optional<PassportEvent> findByIdAndTenantId(ObjectId id, ObjectId tenantId);

}
