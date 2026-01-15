package backlogs.dinamico.controller.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.service.core.UserRoleService;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/api/core/users/{userId}/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@CrossOrigin
public class UserRoleController {

    private final UserRoleService service;

    /**
     * Devuelve asignaciones (role + allowedSystems).
     * Esto es lo correcto para UI, porque SYSTEM_MANAGER depende del scope.
     */
    @GetMapping
    public ResponseEntity<List<UserRoleAssignmentDto>> listUserRoles(@PathVariable ObjectId userId) {
        var tenantId = TenantContext.getTenantId();
        log.info("[USER-ROLES] LIST userId={} tenant={}", userId, tenantId);

        var assignments = service.listAssignmentsOfUser(tenantId, userId);
        return ResponseEntity.ok(assignments.stream().map(UserRoleAssignmentDto::from).toList());
    }

    /**
     * Asigna un rol al usuario. Si el rol es systemScoped, se requiere allowedSystems.
     */
    @PostMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addRole(
            @PathVariable ObjectId userId,
            @PathVariable ObjectId roleId,
            @RequestBody(required = false) @Valid AssignRoleReq body
    ) {
        var tenantId = TenantContext.getTenantId();
        log.info("[USER-ROLES] ADD userId={} roleId={} tenant={}", userId, roleId, tenantId);

        List<String> systems = (body == null) ? null : body.getAllowedSystems();
        service.add(tenantId, userId, roleId, systems);
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable ObjectId userId, @PathVariable ObjectId roleId) {
        var tenantId = TenantContext.getTenantId();
        log.info("[USER-ROLES] REMOVE userId={} roleId={} tenant={}", userId, roleId, tenantId);
        service.remove(tenantId, userId, roleId);
    }

    /**
     * Reemplaza todas las asignaciones (roles + allowedSystems).
     * Ideal para pantalla de administración.
     */
    @PutMapping
    public ResponseEntity<List<UserRoleAssignmentDto>> setAll(
            @PathVariable ObjectId userId,
            @RequestBody @Valid SetRolesReq body
    ) {
        var tenantId = TenantContext.getTenantId();
        log.info("[USER-ROLES] SET-ALL userId={} assignments={} tenant={}", userId, body.getAssignments(), tenantId);

        var saved = service.setAll(tenantId, userId, body.getAssignments());
        return ResponseEntity.ok(saved.stream().map(UserRoleAssignmentDto::from).toList());
    }

    // ===== DTOs =====

    @Value
    public static class AssignRoleReq {
        List<String> allowedSystems;
    }

    @Value
    public static class SetRolesReq {
        @NotNull
        List<Assignment> assignments;
    }

    @Value
    public static class Assignment {
        @NotNull
        ObjectId roleId;
        List<String> allowedSystems;
    }

    /**
     * DTO de respuesta: rol + scope. (lo que UI realmente necesita)
     */
    @Value
    public static class UserRoleAssignmentDto {
        String roleId;
        String code;
        String name;
        boolean orgWide;
        boolean systemScoped;
        List<String> allowedSystems;

        public static UserRoleAssignmentDto from(UserRoleService.UserRoleAssignment a) {
            Role r = a.role();
            UserRole ur = a.userRole();

            return new UserRoleAssignmentDto(
                    r.getId() != null ? r.getId().toHexString() : null,
                    r.getCode() != null ? r.getCode().name() : null,
                    r.getName(),
                    r.isOrgWide(),
                    r.isSystemScoped(),
                    ur.getAllowedSystems() == null ? List.of() : ur.getAllowedSystems().stream().toList()
            );
        }
    }
}
