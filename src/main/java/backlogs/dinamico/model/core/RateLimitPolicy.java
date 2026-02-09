package backlogs.dinamico.model.core;

import lombok.Data;

@Data
public class RateLimitPolicy {

    private boolean enabled = true;
    private BucketPolicy apiKey;
    private BucketPolicy tenant;

    @Data
    public static class BucketPolicy {
        private long capacity;
        private long refillTokens;
        private long refillSeconds;
    }

}
