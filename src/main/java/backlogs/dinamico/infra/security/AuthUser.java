package backlogs.dinamico.infra.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class AuthUser {
        private final ObjectId id;
        private final String email;
        private final String name;
        private final ObjectId tenantId;

        private final List<String> roles;
        private final List<String> permissions;

        private final boolean orgWide;
        private final List<String> allowedSystems;
}
