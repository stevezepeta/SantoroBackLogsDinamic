package backlogs.dinamico.repository.auth;


import backlogs.dinamico.model.auth.TwoFactorChallenge;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface TwoFactorChallengeRepo extends MongoRepository<TwoFactorChallenge, ObjectId> {
  Optional<TwoFactorChallenge> findByIdAndUsedFalse(ObjectId id);
  long deleteByExpiresAtBefore(Instant now);
}
