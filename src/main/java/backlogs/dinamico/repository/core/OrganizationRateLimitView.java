package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.RateLimitPolicy;
import org.bson.types.ObjectId;

public interface OrganizationRateLimitView {

    ObjectId getId();
    RateLimitPolicy getRateLimit();

}
