package backlogs.dinamico.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "backlogs.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    @Valid
    private Bucket apiKey = new Bucket();

    @Valid
    private Bucket tenant = new Bucket();

    @Valid
    private Cache cache = new Cache();

    @Data
    public static class Bucket {

        @Min(1)
        private long capacity = 1;

        @Min(1)
        private long refillTokens = 1;

        @Min(1)
        private long refillSeconds = 1;
    }

    @Data
    public static class Cache {

        @Min(1)
        private long ttlMinutes = 30;

        @Min(1)
        private long maxSize = 200000;
    }

}
