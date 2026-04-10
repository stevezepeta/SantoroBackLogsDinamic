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
    private final RoleRepository     roleRepository;
    private final MongoTemplate      mongoTemplate;

    public AuthorizationContext build(ObjectId tenantId, ObjectId userId) {

        List<UserRole> links = userRoleRepository.findByTenantIdAndUserId(tenantId, userId);

        if (links == null || links.isEmpty()) {
            return AuthorizationContext.builder()
                    .orgWide(false)
                    .allowedSystems(Set.of())
                    .roles(Set.of())
                    .permissions(Set.of())
                    .logFilters(new UserRole.LogFilter())
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
                    .logFilters(new UserRole.LogFilter())
                    .build();
        }

        List<Role> roles = roleRepository.findByTenantIdAndIdIn(tenantId, roleIds);

        Map<ObjectId, Role> roleById = roles.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Role::getId, r -> r, (a, b) -> a));

        boolean orgWide = false;

        Set<String> roleCodes      = new HashSet<>();
        Set<String> permissions    = new HashSet<>();
        Set<String> allowedSystems = new HashSet<>();

        // ── Acumular logFilters: UNIÓN de todos los roles del usuario ─────────
        // Si el usuario tiene VIEWER + otro rol sin filtros, hereda la libertad
        // del rol sin filtros. Si todos sus roles tienen filtros, ve la unión.
        Set<String> mergedOutcomes    = new HashSet<>();
        Set<String> mergedStatuses    = new HashSet<>();
        Set<String> mergedSeverities  = new HashSet<>();
        Set<String> mergedEventTypes  = new HashSet<>();
        boolean hasUnrestrictedRole   = false;   // si algún rol no tiene filtros → sin restricción

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

            // ── Systems: prioridad a los sistemas específicos del UserRole ────
            // Si el UserRole tiene allowedSystems definidos, úsalos aunque el
            // rol sea orgWide (el superAdmin los asignó explícitamente al invitar)
            Set<String> urSystems = ur.getAllowedSystems();
            boolean urHasSystems  = urSystems != null && !urSystems.isEmpty();

            if (urHasSystems) {
                // Sistemas específicos del invite → siempre tienen prioridad
                urSystems.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(String::toUpperCase)
                        .forEach(allowedSystems::add);
            } else if (role.isSystemScoped() && urSystems != null) {
                urSystems.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(String::toUpperCase)
                        .forEach(allowedSystems::add);
            }

            // ── Merge de logFilters ───────────────────────────────────────────
            UserRole.LogFilter lf = ur.getLogFilters();
            if (lf == null || lf.isEmpty()) {
                // Este rol no tiene restricciones → el usuario ve todo
                hasUnrestrictedRole = true;
            } else {
                if (lf.getAllowedOutcomes()    != null) mergedOutcomes.addAll(lf.getAllowedOutcomes());
                if (lf.getAllowedStatuses()    != null) mergedStatuses.addAll(lf.getAllowedStatuses());
                if (lf.getAllowedSeverities()  != null) mergedSeverities.addAll(lf.getAllowedSeverities());
                if (lf.getAllowedEventTypes()  != null) mergedEventTypes.addAll(lf.getAllowedEventTypes());
            }
        }

        // Si tiene algún rol sin restricciones → logFilters vacío (ve todo)
        UserRole.LogFilter mergedFilters;
        if (hasUnrestrictedRole) {
            mergedFilters = new UserRole.LogFilter();
        } else {
            mergedFilters = UserRole.LogFilter.builder()
                    .allowedOutcomes(mergedOutcomes)
                    .allowedStatuses(mergedStatuses)
                    .allowedSeverities(mergedSeverities)
                    .allowedEventTypes(mergedEventTypes)
                    .build();
        }

        // Solo expandir a todos los sistemas si es orgWide Y ningún UserRole
        // tiene sistemas específicos asignados (caso: VIEWER con systems restringidos)
        boolean hasSpecificSystems = !allowedSystems.isEmpty();

        boolean isAdminOrOwner = roleCodes.contains(RoleCode.ORG_OWNER.name())
                || roleCodes.contains(RoleCode.ORG_ADMIN.name());

        if (orgWide && !hasSpecificSystems || isAdminOrOwner) {
            Query q = new Query();
            q.addCriteria(Criteria.where("tenant_id").is(tenantId));
            List<String> systems = mongoTemplate.findDistinct(
                    q, "system", "log_events", String.class);
            systems.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .forEach(allowedSystems::add);
        }

        // Si orgWide=true pero hasSpecificSystems=true → respetar los systems del invite
        return AuthorizationContext.builder()
                .roles(roleCodes)
                .permissions(permissions)
                .allowedSystems(allowedSystems)
                .orgWide(orgWide)
                .logFilters(mergedFilters)
                .build();
    }
}