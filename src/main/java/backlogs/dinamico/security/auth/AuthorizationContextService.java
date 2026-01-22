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

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;


import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationContextService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    private final MongoTemplate mongoTemplate;

    public AuthorizationContext build(ObjectId tenantId, ObjectId userId) {

        List<UserRole> links = userRoleRepository.findByTenantIdAndUserId(tenantId, userId);

        if (links == null || links.isEmpty()) {
            return AuthorizationContext.builder()
                    .orgWide(false)
                    .allowedSystems(Set.of())
                    .roles(Set.of())
                    .permissions(Set.of())
                    .build();
        }

        Set<ObjectId> roleIds = links.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return AuthorizationContext.builder()
                    .orgWide(false)
                    .allowedSystems(Set.of())
                    .roles(Set.of())
                    .permissions(Set.of())
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

        // CAMBIO: si orgWide, devolvemos TODOS los systems del tenant
        if (orgWide) {
            allowedSystems.clear();

            Query q = new Query();
            q.addCriteria(Criteria.where("tenant_id").is(tenantId));
            // opcional: si quieres solo systems con logs activos/no borrados, etc.

            List<String> systems = mongoTemplate.findDistinct(q, "system", "log_events", String.class);

            systems.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .forEach(allowedSystems::add);
        }


        return AuthorizationContext.builder()
                .roles(roleCodes)
                .permissions(permissions)
                .allowedSystems(allowedSystems)
                .orgWide(orgWide)
                .build();
    }
}
