package backlogs.dinamico.api.dto.passport;

import io.jsonwebtoken.Header;

import java.time.Instant;
import java.util.List;

public record PassportTimelineResponse(
        Header passport,
        List<Item> items
) {

    // Info fija del pasaporte
    public record Header(
            String passportNumber,
            String personId,
            String fullName,
            String nationality
    ) {}

    public record Item(
            String id,
            Instant eventTime,
            String operationType,
            String status,

            String message,
            String officeId,
            String officeName,
            String channel,
            String userId,
            String username,
            String userFullName,
            String reasonCode,
            String reasonDescription,
            Long elapsedSeconds
    ) {}

}
