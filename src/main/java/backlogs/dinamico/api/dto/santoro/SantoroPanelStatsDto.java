package backlogs.dinamico.api.dto.santoro;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SantoroPanelStatsDto {

    private long totalOrganizations;
    private long activeOrganizations;
    private long disabledOrganizations;

    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long invitedUsers;

    private long totalApiKeys;
    private long activeApiKeys;
    private long revokedApiKeys;
    private long expiredApiKeys;
    private long expiringApiKeys;

}
