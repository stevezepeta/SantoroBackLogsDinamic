package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.User;
import backlogs.dinamico.repository.core.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Page<User> list(ObjectId tenantId, String q, String status, Pageable pageable) {
        if (tenantId == null) throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");

        if (q != null && !q.isBlank()) {
            String safe = Pattern.quote(q.trim());
            String regex = ".*" + safe + ".*";
            log.info("[USERS] list search q='{}' tenant={} page={} size={} status={}",
                    q, tenantId.toHexString(), pageable.getPageNumber(), pageable.getPageSize(), status);

            // si viene status, aún se listan por búsqueda libre (ajústalo si quieres cruzar con status)
            return userRepository.findByTenantIdAndNameRegexIgnoreCaseOrTenantIdAndEmailRegexIgnoreCase(
                    tenantId, regex,
                    tenantId, regex,
                    pageable
            );
        }

        if (status != null && !status.isBlank()) {
            log.info("[USERS] list by status='{}' tenant={} page={} size={}",
                    status, tenantId.toHexString(), pageable.getPageNumber(), pageable.getPageSize());
            return userRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }

        log.info("[USERS] list all tenant={} page={} size={}",
                tenantId.toHexString(), pageable.getPageNumber(), pageable.getPageSize());
        return userRepository.findByTenantId(tenantId, pageable);
    }

    public User get(ObjectId tenantId, ObjectId id) {
        if (tenantId == null) throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");
        return userRepository.findById(id)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    public User create(ObjectId tenantId, User body) {
        if (tenantId == null) throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");
        if (body == null) throw new ResponseStatusException(BAD_REQUEST, "body_required");

        body.setId(null);
        body.setTenantId(tenantId);
        if (body.getCreatedAt() == null) body.setCreatedAt(Instant.now());
        body.setUpdatedAt(Instant.now());

        // Unicidad de email dentro del tenant
        if (body.getEmail() != null &&
                userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, body.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "email_already_exists");
        }

        return userRepository.save(body);
    }

    public User update(ObjectId tenantId, ObjectId id, User body) {
        if (tenantId == null) throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");
        if (body == null) throw new ResponseStatusException(BAD_REQUEST, "body_required");

        User existing = get(tenantId, id);
        existing.setName(body.getName());
        existing.setStatus(body.getStatus());
        if (body.getEmail() != null &&
                !body.getEmail().equalsIgnoreCase(existing.getEmail())) {
            if (userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, body.getEmail()))
                throw new ResponseStatusException(BAD_REQUEST, "email_already_exists");
            existing.setEmail(body.getEmail());
        }

        existing.setUpdatedAt(Instant.now());
        return userRepository.save(existing);
    }

    public void delete(ObjectId tenantId, ObjectId id) {
        if (tenantId == null) throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");
        User existing = get(tenantId, id);
        userRepository.deleteById(existing.getId());
    }

    public Optional<User> findByEmail(ObjectId tenantId, String email) {
        if (tenantId == null) throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");
        return userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email);
    }
}
