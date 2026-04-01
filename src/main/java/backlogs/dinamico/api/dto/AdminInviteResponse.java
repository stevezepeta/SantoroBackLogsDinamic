package backlogs.dinamico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminInviteResponse {

    private String inviteToken;
    private String inviteLink;

    private OrganizationSummary organization;

    private String email;
    private Instant expirestAt;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationSummary {
        private String id;
        private String name;
    }

}
