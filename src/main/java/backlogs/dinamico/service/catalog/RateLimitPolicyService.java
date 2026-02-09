package backlogs.dinamico.service.catalog;

import backlogs.dinamico.model.core.RateLimitPolicy;
import backlogs.dinamico.repository.core.OrganizationRateLimitView;
import backlogs.dinamico.repository.core.OrganizationRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitPolicyService {

    private final OrganizationRepository orgRepo;
    private final backlogs.dinamico.config.RateLimitProperties rateLimitProps;

    private Cache<String, RateLimitPolicy> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(rateLimitProps.getCache().getTtlMinutes()))
                .maximumSize(rateLimitProps.getCache().getMaxSize())
                .build();

        log.info("[RateLimitPolicyService] cache ttlMin={}, maxSize={}",
                rateLimitProps.getCache().getTtlMinutes(),
                rateLimitProps.getCache().getMaxSize());
    }

    public RateLimitPolicy resolve(ObjectId tenantId, RateLimitPolicy fallback) {
        if (tenantId == null) return normalizeFallback(fallback);

        String key = tenantId.toHexString();

        RateLimitPolicy cached = cache.getIfPresent(key);
        if (cached != null) return cached;

        RateLimitPolicy resolved = fetchAndMerge(tenantId, normalizeFallback(fallback));
        cache.put(key, resolved);
        return resolved;
    }

    public void invalidate(ObjectId tenantId) {
        if (tenantId == null) return;
        cache.invalidate(tenantId.toHexString());
    }

    private RateLimitPolicy fetchAndMerge(ObjectId tenantId, RateLimitPolicy fallback) {
        Optional<OrganizationRateLimitView> viewOpt = orgRepo.findProjectedById(tenantId);

        if (viewOpt.isEmpty()) return fallback;

        RateLimitPolicy db = viewOpt.get().getRateLimit();
        if (db == null) return fallback;

        return merge(db, fallback);
    }


    private static RateLimitPolicy merge(RateLimitPolicy db, RateLimitPolicy fb) {
        RateLimitPolicy out = new RateLimitPolicy();

        // enabled manda si existe rate_limit en DB
        out.setEnabled(db.isEnabled());

        out.setApiKey(mergeBucket(db.getApiKey(), fb.getApiKey()));
        out.setTenant(mergeBucket(db.getTenant(), fb.getTenant()));

        return out;
    }

    private static RateLimitPolicy.BucketPolicy mergeBucket(RateLimitPolicy.BucketPolicy db, RateLimitPolicy.BucketPolicy fb) {
        // si no hay fallback, crea uno mínimo para no reventar
        if (fb == null) fb = minBucket();

        RateLimitPolicy.BucketPolicy out = new RateLimitPolicy.BucketPolicy();

        if (db == null) {
            out.setCapacity(positiveOrDefault(0, fb.getCapacity()));
            out.setRefillTokens(positiveOrDefault(0, fb.getRefillTokens()));
            out.setRefillSeconds(positiveOrDefault(0, fb.getRefillSeconds()));
            return out;
        }

        out.setCapacity(positiveOrDefault(db.getCapacity(), fb.getCapacity()));
        out.setRefillTokens(positiveOrDefault(db.getRefillTokens(), fb.getRefillTokens()));
        out.setRefillSeconds(positiveOrDefault(db.getRefillSeconds(), fb.getRefillSeconds()));
        return out;
    }

    private static long positiveOrDefault(long v, long def) {
        if (v > 0) return v;
        return (def > 0) ? def : 1;
    }

    private static RateLimitPolicy normalizeFallback(RateLimitPolicy fb) {
        if (fb == null) {
            RateLimitPolicy p = new RateLimitPolicy();
            p.setEnabled(true);
            p.setApiKey(minBucket());
            p.setTenant(minBucket());
            return p;
        }
        if (fb.getApiKey() == null) fb.setApiKey(minBucket());
        if (fb.getTenant() == null) fb.setTenant(minBucket());

        // evitar ceros en fallback
        if (fb.getApiKey().getCapacity() <= 0) fb.getApiKey().setCapacity(1);
        if (fb.getApiKey().getRefillTokens() <= 0) fb.getApiKey().setRefillTokens(1);
        if (fb.getApiKey().getRefillSeconds() <= 0) fb.getApiKey().setRefillSeconds(1);

        if (fb.getTenant().getCapacity() <= 0) fb.getTenant().setCapacity(1);
        if (fb.getTenant().getRefillTokens() <= 0) fb.getTenant().setRefillTokens(1);
        if (fb.getTenant().getRefillSeconds() <= 0) fb.getTenant().setRefillSeconds(1);

        return fb;
    }

    private static RateLimitPolicy.BucketPolicy minBucket() {
        RateLimitPolicy.BucketPolicy b = new RateLimitPolicy.BucketPolicy();
        b.setCapacity(1);
        b.setRefillTokens(1);
        b.setRefillSeconds(1);
        return b;
    }

}
