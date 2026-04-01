package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public record UserRoleAssignment(Role role, UserRole userRole) {}

    // ================= LIST =================

    public List<UserRoleAssignment> listAssignmentsOfUser(ObjectId tenantId, ObjectId userId) {
        requireTenant(tenantId);

        List<UserRole> links = userRoleRepository.findByTenantIdAndUserId(tenantId, userId);
        if (links.isEmpty()) return List.of();

        Set<ObjectId> roleIds = links.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        List<Role> roles = roleRepository.findAllById(roleIds);

        Map<ObjectId, Role> roleById = roles.stream().collect(Collectors.toMap(Role::getId, r -> r));

        List<UserRoleAssignment> out = new ArrayList<>();
        for (UserRole ur : links) {
            Role r = roleById.get(ur.getRoleId());
            if (r != null) out.add(new UserRoleAssignment(r, ur));
        }
        return out;
    }

    // (si aún lo usa tu UI vieja)
    public List<Role> listRolesOfUser(ObjectId tenantId, ObjectId userId) {
        return listAssignmentsOfUser(tenantId, userId).stream().map(UserRoleAssignment::role).toList();
    }

    // ================= ADD =================

    public void add(ObjectId tenantId, ObjectId userId, ObjectId roleId, List<String> allowedSystems) {
        requireTenant(tenantId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "role_not_found"));

        // si ya existe, no duplicar
        if (userRoleRepository.existsByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId)) {
            return;
        }

        Set<String> normalizedSystems = normalizeSystems(allowedSystems);

        // Validación: si role es systemScoped => requiere systems
        if (role.isSystemScoped()) {
            if (normalizedSystems.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "allowedSystems_required_for_systemScoped_role");
            }
        } else {
            // orgWide o rol sin scope: ignorar systems
            normalizedSystems = Set.of();
        }

        UserRole ur = UserRole.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .allowedSystems(normalizedSystems)
                .build();

        userRoleRepository.save(ur);
    }

    // ================= REMOVE =================

    public void remove(ObjectId tenantId, ObjectId userId, ObjectId roleId) {
        requireTenant(tenantId);
        userRoleRepository.deleteByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId);
    }

    // ================= SET ALL =================

    public List<UserRoleAssignment> setAll(ObjectId tenantId, ObjectId userId,
                                           List<backlogs.dinamico.controller.core.UserRoleController.Assignment> assignments) {
        requireTenant(tenantId);
        if (assignments == null) assignments = List.of();

        // Limpia existentes
        userRoleRepository.deleteByTenantIdAndUserId(tenantId, userId);

        // Inserta nuevas
        for (var a : assignments) {
            ObjectId roleId = a.getRoleId();
            List<String> allowedSystems = a.getAllowedSystems();
            add(tenantId, userId, roleId, allowedSystems);
        }

        return listAssignmentsOfUser(tenantId, userId);
    }

    // ================= Helpers =================

    private static void requireTenant(ObjectId tenantId) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }
    }

    private static Set<String> normalizeSystems(List<String> systems) {
        if (systems == null) return new HashSet<>();
        return systems.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<String> getRoleCodes(ObjectId tenantId, ObjectId userId) {
        return listAssignmentsOfUser(tenantId, userId).stream()
                .map(a -> a.role().getCode() == null ? null : a.role().getCode().name())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

}
