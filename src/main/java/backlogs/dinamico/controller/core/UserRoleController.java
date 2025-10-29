package backlogs.dinamico.controller.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.service.core.UserRoleService;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/core/users/{userId}/roles")
@RequiredArgsConstructor
@CrossOrigin
public class UserRoleController {

    private final UserRoleService service;

    @GetMapping
    public ResponseEntity<List<RoleDto>> listUserRoles(@PathVariable ObjectId userId) {
        var tenantId = TenantContext.getTenantId();
        log.info("[USER-ROLES] LIST userId={} tenant={}", userId, tenantId);
        List<Role> roles = service.listRolesOfUser(tenantId, userId);
        return ResponseEntity.ok(roles.stream().map(RoleDto::from).toList());
    }

    @PostMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addRole(@PathVariable ObjectId userId, @PathVariable ObjectId roleId) {
        var tenantId = TenantContext.getTenantId();
        log.info("[USER-ROLES] ADD userId={} roleId={} tenant={}", userId, roleId, tenantId);
        service.add(tenantId, userId, roleId);
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable ObjectId userId, @PathVariable ObjectId roleId) {
        var tenantId = TenantContext.getTenantId();
        log.info("[USER-ROLES] REMOVE userId={} roleId={} tenant={}", userId, roleId, tenantId);
        service.remove(tenantId, userId, roleId);
    }

    @PutMapping
    public ResponseEntity<List<RoleDto>> setAll(
            @PathVariable ObjectId userId,
            @RequestBody SetRolesReq body
    ) {
        var tenantId = TenantContext.getTenantId();
        log.info("[USER-ROLES] SET-ALL userId={} roles={} tenant={}", userId, body.getRoleIds(), tenantId);
        List<Role> roles = service.setAll(tenantId, userId, body.getRoleIds());
        return ResponseEntity.ok(roles.stream().map(RoleDto::from).toList());
    }

    // ===== DTOs =====
    @Value
    public static class SetRolesReq {
        List<ObjectId> roleIds;
    }

    @Value
    public static class RoleDto {
        String id;
        String code;
        String name;

        public static RoleDto from(Role r) {
            return new RoleDto(
                    r.getId() == null ? null : r.getId().toHexString(),
                    r.getCode(),
                    r.getName()
            );
        }
    }
}
