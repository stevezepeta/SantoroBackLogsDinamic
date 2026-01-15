package backlogs.dinamico.security.auth;

import backlogs.dinamico.infra.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Component
public class ScopeGuard {

    public void requireSystemAccess(AuthUser user, String system) {

        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");

        String sys = normalizeUpper(system);
        if (!StringUtils.hasText(sys)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system is required");
        }

        if (user.isOrgWide()) return;

        boolean allowed = user.getAllowedSystems() != null
                && user.getAllowedSystems().stream().anyMatch(s -> sys.equalsIgnoreCase(s));

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden_system_scope");
        }
    }

    public static String normalizeUpper(String v) {

        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;
        if ("null".equalsIgnoreCase(v)) return null;

        return v.toUpperCase(Locale.ROOT);

    }

}
