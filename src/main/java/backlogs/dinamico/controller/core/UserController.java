package backlogs.dinamico.controller.core;

import backlogs.dinamico.model.core.User;
import backlogs.dinamico.service.core.UserService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/core/users")
@RequiredArgsConstructor
@CrossOrigin
public class UserController {

    private final UserService userService;

    @GetMapping
    public Page<User> List(@RequestHeader("X-Tenant")ObjectId tenantId,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return userService.list(tenantId, status, q, pageable);
    }

    // GET BY ID
    @GetMapping("/{id}")
    public User get(@RequestHeader("X-Tenant")ObjectId tenantId,
                    @PathVariable ObjectId id) {
        User u = userService.get(id);
        if(u.getTenantId() == null || !u.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        return u;
    }

    // POST (fuerza el tenantId y valida el email unico por tenant)
    @PostMapping
    public ResponseEntity<User> create(@RequestHeader("X-Tenant")ObjectId tenantId,
                                       @RequestBody User body) {
        // Setea el request de Tenant
        body.setTenantId(tenantId);

        if(body.getEmail() != null &&
                userService.findByEmail(tenantId, body.getEmail()).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "El email ya existe en este tenant");
        }

        User saved = userService.create(body);
        return ResponseEntity
                .created(URI.create("/api/core/users/" + saved.getId().toHexString()))
                .body(saved);
    }

    // UPDATE
    @PutMapping("/{id}")
    public User update(@RequestHeader("X-Tenant")ObjectId tenantId,
                       @RequestBody User body,
                       @PathVariable ObjectId id) {

        User existing = userService.get(id);
        if(existing.getTenantId() == null || !existing.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        // El tenant no cambia
        body.setTenantId(existing.getTenantId());

        // Si cambia el email, se valida dentro del Tenant
        if(body.getEmail() != null &&
            !body.getEmail().equalsIgnoreCase(existing.getEmail()) &&
            userService.findByEmail(tenantId, body.getEmail()).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "El email ya existe en este tenant");
        }

        return userService.update(id, body);
    }

    // DELETE
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Tenant")ObjectId tenantId,
                       @PathVariable ObjectId id) {

        User existing = userService.get(id);
        if(existing.getTenantId() == null || !existing.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        userService.delete(id);
    }



}
