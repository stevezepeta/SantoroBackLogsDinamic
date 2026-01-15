package backlogs.dinamico.controller.core;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.core.RoleCode;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.service.core.UserRoleService;
import backlogs.dinamico.service.core.UserService;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@RestController
@RequestMapping("/api/core/users")
@RequiredArgsConstructor
@CrossOrigin
public class UserController {

    private final UserService service;
    private final UserRoleService roleService;

    private ObjectId requireTenant() {
        var t = TenantContext.getTenantId();
        if (t == null) throw new ResponseStatusException(BAD_REQUEST, "X-Tenant inválido o ausente");
        return t;
    }

    @GetMapping
    public ApiResponse<PageDto<UserListItem>> list(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status
    ) {
        ObjectId tenantId = requireTenant();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        log.info("[USERS] GET list q='{}' status='{}' page={} size={} tenant={}",
                q, status, page, size, tenantId.toHexString());

        Page<User> p = service.list(tenantId, q, status, pageable);

        // Mapear cada User a UserListItem con roles
        List<UserListItem> items = p.getContent().stream()
                .map(u -> new UserListItem(
                        u.getId().toHexString(),
                        u.getEmail(),
                        u.getName(),
                        u.getStatus(),
                        u.getCreatedAt(),
                        u.getUpdatedAt(),
                        roleService.getRoleCodes(tenantId, u.getId())
                ))
                .toList();

        PageDto<UserListItem> dto = PageDto.of(p, items);

        log.info("[USERS] OK page={} size={} total={}", dto.getPage(), dto.getSize(), dto.getTotal());

        return ApiResponse.ok("Usuarios listados", null, dto);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDetail> get(@PathVariable ObjectId id) {
        ObjectId tenantId = requireTenant();

        User u = service.get(tenantId, id);
        List<String> roles = roleService.getRoleCodes(tenantId, u.getId());

        UserDetail dto = new UserDetail(
                u.getId().toHexString(),
                u.getEmail(),
                u.getName(),
                u.getStatus(),
                u.getCreatedAt(),
                u.getUpdatedAt(),
                roles
        );

        return ApiResponse.ok("Usuario encontrado", null, dto);
    }

    @PostMapping
    public ApiResponse<UserDetail> create(@RequestBody User body) {
        ObjectId tenantId = requireTenant();

        User saved = service.create(tenantId, body);
        List<String> roles = roleService.getRoleCodes(tenantId, saved.getId());

        UserDetail dto = new UserDetail(
                saved.getId().toHexString(),
                saved.getEmail(),
                saved.getName(),
                saved.getStatus(),
                saved.getCreatedAt(),
                saved.getUpdatedAt(),
                roles
        );

        return ApiResponse.created("Usuario creado", null, dto);
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDetail> update(@PathVariable ObjectId id, @RequestBody User body) {
        ObjectId tenantId = requireTenant();

        User updated = service.update(tenantId, id, body);
        List<String> roles = roleService.getRoleCodes(tenantId, updated.getId());

        UserDetail dto = new UserDetail(
                updated.getId().toHexString(),
                updated.getEmail(),
                updated.getName(),
                updated.getStatus(),
                updated.getCreatedAt(),
                updated.getUpdatedAt(),
                roles
        );

        return ApiResponse.ok("Usuario actualizado", null, dto);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable ObjectId id) {
        service.delete(requireTenant(), id);
        return ApiResponse.ok("Usuario eliminado", null, null);
    }

    @Value
    public static class UserListItem {
        String id;
        String email;
        String name;
        String status;
        Instant createdAt;
        Instant updatedAt;
        List<String> roles;
    }

    @Value
    public static class UserDetail {
        String id;
        String email;
        String name;
        String status;
        Instant createdAt;
        Instant updatedAt;
        List<String> roles;
    }

    @Value
    public static class PageDto<T> {
        int page;
        int size;
        long total;
        java.util.List<T> data;

        public static <T> PageDto<T> of(Page<?> p, List<T> items) {
            return new PageDto<>(p.getNumber(), p.getSize(), p.getTotalElements(), items);
        }
    }
}
