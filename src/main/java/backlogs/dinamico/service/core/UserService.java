package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.User;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.repository.core.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final OrganizationRepository orgRepo;

    public Page<User> list(ObjectId tenantId, String status, String q, Pageable pageable) {
        if (status != null && !status.isBlank())
            return userRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        if (q != null && !q.isBlank())
            return userRepository.findByTenantIdAndNameRegexIgnoreCase(tenantId, q, pageable);
        return userRepository.findByTenantId(tenantId, pageable);
    }

    public User get(ObjectId id) {
        return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    public User create(User user) {

        ObjectId tenantId = user.getTenantId();
        if(!orgRepo.existsByIdAndStatus(tenantId, "active")) {
            throw new ResponseStatusException(BAD_REQUEST, "Organizacion no existe o inactiva");
        }

        return userRepository.insert(user);
    }

    public User update(ObjectId id, User user) {
        User existing = get(id);
        user.setId(id);
        user.setCreatedAt(existing.getCreatedAt());
        return userRepository.save(user);
    }

    public void delete(ObjectId id) {
        if (!userRepository.existsById(id)) throw new ResponseStatusException(NOT_FOUND);
        userRepository.deleteById(id);
    }

    public Optional<User> findByEmail(ObjectId tenantId, String email) {
        return userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email);
    }

}
