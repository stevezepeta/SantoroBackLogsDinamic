package backlogs.dinamico.api.dto.catalog;

import java.time.Instant;
import java.util.List;

public record ApiKeyStatusResponse(
        List<Item> items,
        PageMeta page
) {

    public enum State{
        OK,
        RENEW_SOON,
        RENEW_REQUIRED,
        EXPIRED,
        DISABLED
    }

    public record Item(
            String id,
            String name,
            String status,
            Instant createdAt,
            Instant updateAt,
            Instant lastUsedAt,
            Instant rotateAt,
            Instant expiresAt,

            // computed
            State state,
            boolean renewRequired,
            boolean expired,
            long secondsToRotate,
            long secondsToExpire
    ) {}

    public record PageMeta(int page, int size, long total, boolean hasNext) {}

}
