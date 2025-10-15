package backlogs.dinamico.controller.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.service.core.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/core/users/{userId}/roles")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRoleService service;

    // Listar los roles de un usuario
    @GetMapping
    public List<Role> list(@RequestHeader("X-Tenant") ObjectId tenantId,
                           @PathVariable ObjectId userId) {
        return service.list(tenantId, userId);
    }

    // Asignacion de rol
    @PostMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestHeader("X-Tenant") ObjectId tenantId,
                    @PathVariable ObjectId userId,
                    @PathVariable ObjectId roleId) {
        service.add(tenantId, userId, roleId);
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader("X-Tenant") ObjectId tenantId,
                       @PathVariable ObjectId userId,
                       @PathVariable ObjectId roleId) {
        service.remove(tenantId, userId, roleId);
    }

    @PutMapping
    public ResponseEntity<Void> replace(@RequestHeader("X-Tenant") ObjectId tenantId,
                                        @PathVariable ObjectId userId,
                                        @RequestParam List<String> roleIdsHex) {
        var roleIds = roleIdsHex.stream().map(ObjectId::new).toList();
        service.replace(tenantId, userId, roleIds);
        return ResponseEntity.noContent().build();
    }

}
