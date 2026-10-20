package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Conflictos de identidad: usernames asociados a más de un actor.id distinto.
 * Mide duplicidad/inestabilidad de identificadores SIN fusionar.
 * NUNCA incluye fullName completo ni IPs — solo ids/username.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityConflictDto {

    private Long conflictedUsernames;

    private List<ConflictDto> conflicts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictDto {
        private String username;
        private Integer distinctActorIds;
        private List<String> sampleActorIds; // máx 3
    }
}
