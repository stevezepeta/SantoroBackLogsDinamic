package backlogs.dinamico.infra.security;

import org.bson.types.ObjectId;

import java.util.List;

public record AuthUser(
        ObjectId userId,
        String email,
        String name,
        ObjectId tenantId,
        List<String> roles
        ) {

}
