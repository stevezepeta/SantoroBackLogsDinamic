package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;

    public void assertUserInTenant(ObjectId tenantId, ObjectId userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Usuario no existe"));
        if(u.getTenantId() == null || !u.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(NOT_FOUND, "Usuario fuera del tenant");
        }
    }

    public void requireRoleExist(ObjectId roleId) {
        if(!roleRepo.existsById(roleId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Rol invalido");
        }
    }

    // Se listan los roles asigandos a un usuario
    public List<Role> list(ObjectId tenantId, ObjectId userId) {
        assertUserInTenant(tenantId, userId);
        var links = userRoleRepo.findByTenantIdAndUserId(tenantId, userId);
        var roleIds = links.stream().map(UserRole::getRoleId).toList();

        return roleIds.isEmpty() ? List.of() : roleRepo.findAllById(roleIds);
    }

    // Asigna un rol
    public void add(ObjectId tenantId, ObjectId userId, ObjectId roleId) {
        assertUserInTenant(tenantId, userId);
        requireRoleExist(roleId);
        if(userRoleRepo.existsByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId)) return;
        userRoleRepo.save(UserRole.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .build());
    }

    // Eliminacion de un rol
    public void remove(ObjectId tenantId, ObjectId userId, ObjectId roleId) {
        assertUserInTenant(tenantId, userId);
        userRoleRepo.deleteByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId);
    }

    public void replace(ObjectId tenantId, ObjectId userId, List<ObjectId> newRoleIds) {
        assertUserInTenant(tenantId, userId);

        // validando la existencia de los roles
        if(newRoleIds != null && !newRoleIds.isEmpty()) {
            long count = roleRepo.countByIdIn(tenantId, newRoleIds);
            if(count != newRoleIds.size()) {
                throw new ResponseStatusException(BAD_REQUEST, "Algun rol no existe");
            }
        }

        var current = userRoleRepo.findByTenantIdAndUserId(tenantId, userId);
        Set<ObjectId> currentSet = new HashSet<>(current.stream().map(UserRole::getRoleId).toList());
        Set<ObjectId> newSet = new HashSet<>(newRoleIds == null ? List.of() : newRoleIds);

        // quita los que ya no están
        for (ObjectId rid : currentSet) {
            if (!newSet.contains(rid)) {
                userRoleRepo.deleteByTenantIdAndUserIdAndRoleId(tenantId, userId, rid);
            }
        }
        // agrega los nuevos
        for (ObjectId rid : newSet) {
            if (!currentSet.contains(rid)) {
                add(tenantId, userId, rid);
            }
        }
    }


}
