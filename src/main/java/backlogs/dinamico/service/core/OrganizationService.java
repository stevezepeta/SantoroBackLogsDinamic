package backlogs.dinamico.service.core;

import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.repository.core.OrganizationRepository;
import com.mongodb.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository repo;

  public Page<Organization> list(String q, Pageable p) {
    if(StringUtils.hasText(q)) {
      return repo.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCaseOrCodeContainingIgnoreCase(q, q, q, p);
    }
    return repo.findAll(p);
  }

  public Organization get(ObjectId id) {
    return repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
  }

  public Organization create(Organization in) {
    if (in == null) throw new ResponseStatusException(BAD_REQUEST, "Body requerido");

    if (!StringUtils.hasText(in.getStatus())) in.setStatus("active");
    if (in.getStatus() == null) {
      in.setSettings(Organization.Settings.builder().timezone("UTC").retentionDays(90).build());
    }

    if (!StringUtils.hasText(in.getCode())) {
      in.setCode(generateCode(
              StringUtils.hasText(in.getName()) ? in.getName() : in.getDomain()
      ));
    }
    if (!StringUtils.hasText(in.getSlug())) {
      in.setSlug(slugify(
              StringUtils.hasText(in.getName()) ? in.getName() : in.getDomain()
      ));
    }

    ensureUnique(in);

    if (in.getCreatedAt() == null) in.setCreatedAt(Instant.now());
    in.setUpdatedAt(Instant.now());

    try {
      return repo.save(in);
    } catch (DuplicateKeyException dk) {
      throw new ResponseStatusException(CONFLICT, "Organization duplicada (code/slug/domain ya existen)");
    }
  }

  public Organization update(ObjectId id, Organization patch) {
    var curr = get(id);

    if (StringUtils.hasText(patch.getName())) curr.setName(patch.getName());
    if (StringUtils.hasText(patch.getDomain())) curr.setDomain(patch.getDomain());
    if (StringUtils.hasText(patch.getStatus())) curr.setStatus(patch.getStatus());

    if (patch.getSettings() != null) {
      var s = curr.getSettings() == null ? new Organization.Settings() : curr.getSettings();
      if (StringUtils.hasText(patch.getSettings().getTimezone())) {
        s.setTimezone(patch.getSettings().getTimezone());
      }
      if (patch.getSettings().getRetentionDays() != null) {
        s.setRetentionDays(patch.getSettings().getRetentionDays());
      }
      curr.setSettings(s);
    }

    if (StringUtils.hasText(patch.getCode())) curr.setCode(normalizeCode(patch.getCode()));
    if (StringUtils.hasText(patch.getSlug())) curr.setSlug(slugify(patch.getSlug()));

    ensureUniqueOnUpdate(curr);

    curr.setUpdatedAt(Instant.now());
    try {
      return repo.save(curr);
    } catch (DuplicateKeyException dk) {
      throw new ResponseStatusException(CONFLICT, "Organization duplicada (code/slug/domain ya existe)");
    }
  }

  public void delete(ObjectId id) {
    var curr = get(id);
    repo.delete(curr);
  }

  private void ensureUnique(Organization o) {
    repo.findByDomain(o.getDomain()).ifPresent(x -> {
      throw new ResponseStatusException(CONFLICT, "domain ya existe");
    });
    repo.findByCode(o.getCode()).ifPresent(x -> {
      throw new ResponseStatusException(CONFLICT, "code ya existe");
    });
    repo.findBySlug(o.getSlug()).ifPresent(x -> {
      throw new ResponseStatusException(CONFLICT, "slug ya existe");
    });
  }

  private void ensureUniqueOnUpdate(Organization o) {
    repo.findByDomain(o.getDomain()).ifPresent(x -> {
      if (!x.getId().equals(o.getId())) throw new ResponseStatusException(CONFLICT, "domain ya existe");
    });
    repo.findByCode(o.getCode()).ifPresent(x -> {
      if (!x.getId().equals(o.getId())) throw new ResponseStatusException(CONFLICT, "code ya existe");
    });
    repo.findBySlug(o.getSlug()).ifPresent(x -> {
      if (!x.getId().equals(o.getId())) throw new ResponseStatusException(CONFLICT, "slug ya existe");
    });
  }

  private String generateCode(String seed) {
    String base = normalizeCode(seed);
    if (!StringUtils.hasText(base)) base = "ORG";
    String candidate = base;
    int n = 1;
    while (repo.findByCode(candidate).isPresent()) {
      candidate = base + "_" + n++;
    }
    return candidate;
  }

  private String normalizeCode(String s) {
    if (!StringUtils.hasText(s)) return null;
    String out = s.trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    if (out.length() > 32) out = out.substring(0, 32);
    if (!StringUtils.hasText(out)) out = "ORG";
    return out;
  }

  private String slugify(String s) {
    if (!StringUtils.hasText(s)) return "org";
    String out = s.trim().toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "-")
            .replaceAll("[^a-z0-9-]", "")
            .replaceAll("^-+|-+$", "");
    if (!StringUtils.hasText(out)) out = "org";
    // asegurar unicidad aquí también
    String candidate = out;
    int n = 1;
    while (repo.findBySlug(candidate).isPresent()) {
      candidate = out + "-" + n++;
    }
    return candidate;
  }

}
