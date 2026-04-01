package backlogs.dinamico.api.dto.santoro;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrgSummaryDto {

    private String id;
    private String name;
    private String domain;
    private String code;
    private String slug;
    private String status;
    private String timezone;
    private String createdAt;

    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;

    private long totalApiKeys;
    private long activeApiKeys;
    private long revokedApiKeys;
    private long expiredApiKeys;

}
