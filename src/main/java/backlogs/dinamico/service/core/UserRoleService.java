package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRoleRepository userRoleRepo;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;

    // --- Consultar roles de un usuario ---
    public List<Role> listRolesOfUser(ObjectId tenantId, ObjectId userId) {
        ensureUserInTenant(tenantId, userId);
        var assignments = userRoleRepo.findByTenantIdAndUserId(tenantId, userId);
        if (assignments.isEmpty()) return List.of();

        var roleIds = assignments.stream().map(UserRole::getRoleId).toList();
        var roles = roleRepo.findAllById(roleIds);

        return roles.stream()
                .filter(r -> tenantId.equals(r.getTenantId()))
                .toList();
    }

    // --- Agregar un rol ---
    public void add(ObjectId tenantId, ObjectId userId, ObjectId roleId) {
        var user = ensureUserInTenant(tenantId, userId);
        var role = ensureRoleInTenant(tenantId, roleId);

        boolean exists = userRoleRepo.existsByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId);
        if (exists) return;

        var ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setUserId(user.getId());
        ur.setRoleId(role.getId());
        ur.setCreatedAt(new Date().toInstant());
        userRoleRepo.save(ur);
    }

    public void remove(ObjectId tenantId, ObjectId userId, ObjectId roleId) {
        ensureUserInTenant(tenantId, userId);
        ensureRoleInTenant(tenantId, roleId);
        userRoleRepo.deleteByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId);
    }

    public List<Role> setAll(ObjectId tenantId, ObjectId userId, List<ObjectId> newRoles) {
        ensureUserInTenant(tenantId, userId);

        var current = userRoleRepo.findByTenantIdAndUserId(tenantId, userId);
        var currentSet = current.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        var newSet = new HashSet<>(Optional.ofNullable(newRoles).orElse(List.of()));


        for (ObjectId rid : currentSet) {
            if (!newSet.contains(rid)) {
                userRoleRepo.deleteByTenantIdAndUserIdAndRoleId(tenantId, userId, rid);
            }
        }

        // agregar los nuevos
        for (ObjectId rid : newSet) {
            add(tenantId, userId, rid);
        }

        return listRolesOfUser(tenantId, userId);
    }

    private User ensureUserInTenant(ObjectId tenantId, ObjectId userId) {
        var user = userRepo.findById(userId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user_not_found"));
        if (!tenantId.equals(user.getTenantId())) {
            throw new ResponseStatusException(BAD_REQUEST, "user_not_in_tenant");
        }
        return user;
    }

    private Role ensureRoleInTenant(ObjectId tenantId, ObjectId roleId) {
        var role = roleRepo.findById(roleId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "role_not_found"));
        if (!tenantId.equals(role.getTenantId())) {
            throw new ResponseStatusException(BAD_REQUEST, "role_not_in_tenant");
        }
        return role;
    }
}
