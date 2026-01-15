package backlogs.dinamico.security.auth;

import lombok.Builder;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
@Builder
public class AuthorizationContext {

    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    @Builder.Default
    private Set<String> allowedSystems = new HashSet<>();

    private boolean orgWide;

}
