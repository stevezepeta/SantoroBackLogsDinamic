package backlogs.dinamico.controller.core;

import backlogs.dinamico.model.core.User;
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

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@RestController
@RequestMapping("/api/core/users")
@RequiredArgsConstructor
@CrossOrigin
public class UserController {

    private final UserService service;

    private ObjectId requireTenant() {
        var t = TenantContext.getTenantId();
        if (t == null) throw new ResponseStatusException(BAD_REQUEST, "X-Tenant inválido o ausente");
        return t;
    }

    @GetMapping
    public PageDto<User> list(
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
        log.info("[USERS] OK page={} size={} total={}", p.getNumber(), p.getSize(), p.getTotalElements());
        return PageDto.of(p);
    }

    @GetMapping("/{id}")
    public User get(@PathVariable ObjectId id) {
        return service.get(requireTenant(), id);
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User body) {
        User saved = service.create(requireTenant(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public User update(@PathVariable ObjectId id, @RequestBody User body) {
        return service.update(requireTenant(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable ObjectId id) {
        service.delete(requireTenant(), id);
    }

    @Value
    public static class PageDto<T> {
        int page;
        int size;
        long total;
        java.util.List<T> data;

        public static <T> PageDto<T> of(Page<T> p) {
            return new PageDto<>(p.getNumber(), p.getSize(), p.getTotalElements(), p.getContent());
        }
    }
}
