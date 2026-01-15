package backlogs.dinamico.security.auth;

import backlogs.dinamico.model.core.PermissionCode;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.RoleCode;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationContextService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public AuthorizationContext build(ObjectId tenantId, ObjectId userId) {

        List<UserRole> links = userRoleRepository.findByTenantIdAndUserId(tenantId, userId);

        if (links == null || links.isEmpty()) {
            return AuthorizationContext.builder()
                    .orgWide(false)
                    .build();
        }

        Set<ObjectId> roleIds = links.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return AuthorizationContext.builder()
                    .orgWide(false)
                    .build();
        }

        // FIX: filtrar por tenant
        List<Role> roles = roleRepository.findByTenantIdAndIdIn(tenantId, roleIds);

        Map<ObjectId, Role> roleById = roles.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Role::getId, r -> r, (a, b) -> a));

        boolean orgWide = false;

        Set<String> roleCodes = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        Set<String> allowedSystems = new HashSet<>();

        for (UserRole ur : links) {
            Role role = roleById.get(ur.getRoleId());
            if (role == null) continue;

            RoleCode code = role.getCode();
            if (code != null) roleCodes.add(code.name());

            orgWide = orgWide || role.isOrgWide();

            if (role.getPermissions() != null) {
                for (PermissionCode p : role.getPermissions()) {
                    if (p != null) permissions.add(p.name());
                }
            }

            // systems solo si el rol es systemScoped
            if (role.isSystemScoped() && ur.getAllowedSystems() != null) {
                ur.getAllowedSystems().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(String::toUpperCase)
                        .forEach(allowedSystems::add);
            }
        }

        if (orgWide) {
            allowedSystems.clear();
        }

        return AuthorizationContext.builder()
                .roles(roleCodes)
                .permissions(permissions)
                .allowedSystems(allowedSystems)
                .orgWide(orgWide)
                .build();
    }
}
