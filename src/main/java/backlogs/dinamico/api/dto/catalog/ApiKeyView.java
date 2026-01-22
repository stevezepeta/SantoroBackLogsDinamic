package backlogs.dinamico.api.dto.catalog;

import java.time.Instant;
import java.util.List;

public record ApiKeyView(
        String id,
        String name,
        String status,
        List<String> scopes,
        String systemId,
        String environmentId,
        Instant createdAt,
        Instant updatedAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant rotatesAt
) {
}
