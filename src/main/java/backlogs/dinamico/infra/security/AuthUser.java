package backlogs.dinamico.infra.security;

import backlogs.dinamico.model.core.UserRole;
import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Principal del SecurityContext.
 * Incluye todos los datos del JWT ya parseados, incluyendo logFilters.
 */
@Getter
public class AuthUser implements UserDetails {

        private final ObjectId userId;
        private final String   email;
        private final String   name;
        private final ObjectId tenantId;

        private final List<String> roles;
        private final List<String> permissions;
        private final boolean      orgWide;
        private final List<String> allowedSystems;

        /**
         * Filtros de visibilidad de logs extraídos del JWT.
         * Si está vacío = el usuario ve todos los registros.
         */
        private final UserRole.LogFilter logFilters;

        private final Collection<? extends GrantedAuthority> authorities;

        public AuthUser(ObjectId userId,
                        String email,
                        String name,
                        ObjectId tenantId,
                        List<String> roles,
                        List<String> permissions,
                        boolean orgWide,
                        List<String> allowedSystems) {
                this(userId, email, name, tenantId, roles, permissions, orgWide,
                        allowedSystems, null, List.of());
        }

        public AuthUser(ObjectId userId,
                        String email,
                        String name,
                        ObjectId tenantId,
                        List<String> roles,
                        List<String> permissions,
                        boolean orgWide,
                        List<String> allowedSystems,
                        UserRole.LogFilter logFilters,
                        Collection<? extends GrantedAuthority> authorities) {
                this.userId         = userId;
                this.email          = email;
                this.name           = name;
                this.tenantId       = tenantId;
                this.roles          = roles          != null ? roles          : List.of();
                this.permissions    = permissions    != null ? permissions    : List.of();
                this.orgWide        = orgWide;
                this.allowedSystems = allowedSystems != null ? allowedSystems : List.of();
                this.logFilters     = logFilters     != null ? logFilters : new UserRole.LogFilter();
                this.authorities    = authorities    != null ? authorities    : List.of();
        }

        /** ¿Tiene algún filtro activo de logs? */
        public boolean hasLogFilters() {
                return logFilters != null && !logFilters.isEmpty();
        }

        // ── UserDetails ───────────────────────────────────────────────────────────

        @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
        @Override public String  getPassword()                                    { return null; }
        @Override public String  getUsername()                                    { return email; }
        @Override public boolean isAccountNonExpired()                            { return true; }
        @Override public boolean isAccountNonLocked()                             { return true; }
        @Override public boolean isCredentialsNonExpired()                        { return true; }
        @Override public boolean isEnabled()                                      { return true; }
}