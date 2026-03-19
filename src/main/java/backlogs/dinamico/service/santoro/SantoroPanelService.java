package backlogs.dinamico.service.santoro;

import backlogs.dinamico.api.dto.CreateOrgRequest;
import backlogs.dinamico.api.dto.santoro.CreateOrgResponse;
import backlogs.dinamico.api.dto.santoro.OrgSummaryDto;
import backlogs.dinamico.api.dto.santoro.SantoroPanelStatsDto;
import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.service.email.EmailValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class SantoroPanelService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("America/Mexico_City"));

    private static final String SANTORO_DOMAIN = "grupo-santoro.com.mx";

    private final OrganizationRepository orgRepo;
    private final UserRepository userRepo;
    private final ApiKeyRep apiKeyRep;
    private final PasswordEncoder passwordEncoder;

    private final EmailValidationService emailValidationService;

    // ── Validación de dominio ─────────────────────────────────────────────────

    /**
     * Verifica que el email del usuario autenticado pertenezca al dominio Santoro.
     * Llamar al inicio de cada método de servicio.
     */
    public void assertSantoroDomain(String email) {
        if (!StringUtils.hasText(email) ||
                !email.toLowerCase(Locale.ROOT).endsWith("@" + SANTORO_DOMAIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acceso restringido al dominio @" + SANTORO_DOMAIN);
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public SantoroPanelStatsDto buildStats() {
        // Organizaciones
        long totalOrgs     = orgRepo.count();
        long activeOrgs    = orgRepo.findByStatus("active",   Pageable.unpaged()).getTotalElements();
        long disabledOrgs  = orgRepo.findByStatus("disabled", Pageable.unpaged()).getTotalElements();

        // Usuarios
        long totalUsers    = userRepo.count();
        long activeUsers   = userRepo.countByStatus("active");
        long inactiveUsers = userRepo.countByStatus("disabled");
        long invitedUsers  = userRepo.countByStatus("invited");

        // API Keys
        long totalKeys    = apiKeyRep.count();
        long activeKeys   = countKeysByStatus("active");
        long revokedKeys  = countKeysByStatus("revoked");
        long expiredKeys  = countKeysByStatus("expired");

        // Claves que vencen en los próximos 7 días
        Instant in7days = Instant.now().plusSeconds(7L * 24 * 60 * 60);
        long expiringKeys = apiKeyRep.findAll().stream()
                .filter(k -> "active".equalsIgnoreCase(k.getStatus()))
                .filter(k -> k.getExpiresAt() != null && k.getExpiresAt().isBefore(in7days))
                .count();

        return SantoroPanelStatsDto.builder()
                .totalOrganizations(totalOrgs)
                .activeOrganizations(activeOrgs)
                .disabledOrganizations(disabledOrgs)
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .invitedUsers(invitedUsers)
                .totalApiKeys(totalKeys)
                .activeApiKeys(activeKeys)
                .revokedApiKeys(revokedKeys)
                .expiredApiKeys(expiredKeys)
                .expiringApiKeys(expiringKeys)
                .build();
    }

    // ── Organizations ─────────────────────────────────────────────────────────

    public Page<OrgSummaryDto> listOrganizations(String search, String status,
                                                 int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Organization> orgs;

        if (StringUtils.hasText(search) && StringUtils.hasText(status)) {
            // Spring Data no soporta combinación directa: filtrar en memoria sobre búsqueda
            orgs = orgRepo.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCaseOrCodeContainingIgnoreCase(
                    search, search, search, pageable);
        } else if (StringUtils.hasText(search)) {
            orgs = orgRepo.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCaseOrCodeContainingIgnoreCase(
                    search, search, search, pageable);
        } else if (StringUtils.hasText(status)) {
            orgs = orgRepo.findByStatus(status.toLowerCase(Locale.ROOT), pageable);
        } else {
            orgs = orgRepo.findAll(pageable);
        }

        return orgs.map(this::toOrgSummary);
    }

    public OrgSummaryDto getOrganization(ObjectId id) {
        Organization org = orgRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "org_not_found"));
        return toOrgSummary(org);
    }

    public CreateOrgResponse createOrganization(CreateOrgRequest req) {
        // Validar duplicados
        if (orgRepo.findByDomainIgnoreCase(req.getOrgDomain()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "domain_already_exists");
        if (orgRepo.findByCode(req.getOrgCode().toUpperCase(Locale.ROOT)).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "code_already_exists");
        if (orgRepo.findBySlugIgnoreCase(req.getOrgSlug()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug_already_exists");

        emailValidationService.consumeToken(req.getVerificationToken(), req.getAdminEmail());

        // Crear organización
        Organization org = Organization.builder()
                .name(req.getOrgName().trim())
                .domain(req.getOrgDomain().trim().toLowerCase(Locale.ROOT))
                .code(req.getOrgCode().trim().toUpperCase(Locale.ROOT))
                .slug(req.getOrgSlug().trim().toLowerCase(Locale.ROOT))
                .status("active")
                .settings(Organization.Settings.builder()
                        .timezone(StringUtils.hasText(req.getTimezone())
                                ? req.getTimezone()
                                : "America/Mexico_City")
                        .retentionDays(req.getRetentionDays() != null
                                ? req.getRetentionDays()
                                : 90)
                        .build())
                .build();

        org = orgRepo.save(org);

        // Generar password temporal si no se proporcionó
        String rawPassword = StringUtils.hasText(req.getTemporaryPassword())
                ? req.getTemporaryPassword()
                : generateSecurePassword();

        // Crear superAdmin
        User admin = User.builder()
                .tenantId(org.getId())
                .name(req.getAdminName().trim())
                .email(req.getAdminEmail().trim().toLowerCase(Locale.ROOT))
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status("active")
                .mustChangePassword(true)   // obliga a cambiar en el primer login
                .build();

        admin = userRepo.save(admin);

        log.info("[SantoroPanelService] Org creada: {} | Admin: {}", org.getId(), admin.getEmail());

        return CreateOrgResponse.builder()
                .orgId(org.getId().toHexString())
                .orgName(org.getName())
                .orgDomain(org.getDomain())
                .orgCode(org.getCode())
                .orgSlug(org.getSlug())
                .orgStatus(org.getStatus())
                .adminUserId(admin.getId().toHexString())
                .adminName(admin.getName())
                .adminEmail(admin.getEmail())
                .temporaryPassword(rawPassword)   // solo esta vez
                .createdAt(fmt(org.getCreatedAt()))
                .build();
    }

    /**
     * Activa o desactiva una organización.
     * Cuando se deshabilita una org se deshabilitan también todas sus API Keys.
     */
    public OrgSummaryDto setOrganizationStatus(ObjectId id, String newStatus) {
        String status = newStatus.toLowerCase(Locale.ROOT);
        if (!status.equals("active") && !status.equals("disabled"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status_must_be_active_or_disabled");

        Organization org = orgRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "org_not_found"));

        org.setStatus(status);
        orgRepo.save(org);

        // Si se deshabilita la org → revocar todas sus API Keys activas
        if ("disabled".equals(status)) {
            List<ApiKey> keys = apiKeyRep.findByTenantIdOrderByCreatedAtDesc(id);
            keys.stream()
                    .filter(k -> "active".equalsIgnoreCase(k.getStatus()))
                    .forEach(k -> {
                        k.setStatus("revoked");
                        apiKeyRep.save(k);
                    });
            log.info("[SantoroPanelService] Org {} deshabilitada. {} API Keys revocadas.",
                    id.toHexString(), keys.size());
        }

        return toOrgSummary(org);
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    public Page<UserView> listAllUsers(String search, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users;
        if (StringUtils.hasText(search)) {
            users = userRepo.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    search, search, pageable);
        } else if (StringUtils.hasText(status)) {
            users = userRepo.findByStatus(status.toLowerCase(Locale.ROOT), pageable);
        } else {
            users = userRepo.findAllByOrderByCreatedAtDesc(pageable);
        }

        return users.map(this::toUserView);
    }

    public Page<UserView> listUsersByOrg(ObjectId orgId, String search, String status,
                                         int page, int size) {
        orgRepo.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "org_not_found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users;
        if (StringUtils.hasText(search)) {
            users = userRepo.findByTenantIdAndNameContainingIgnoreCaseOrTenantIdAndEmailContainingIgnoreCase(
                    orgId, search, orgId, search, pageable);
        } else if (StringUtils.hasText(status)) {
            users = userRepo.findByTenantIdAndStatus(orgId, status.toLowerCase(Locale.ROOT), pageable);
        } else {
            users = userRepo.findByTenantId(orgId, pageable);
        }

        return users.map(this::toUserView);
    }

    // ── API Keys ──────────────────────────────────────────────────────────────

    public Page<ApiKeyView> listAllApiKeys(String search, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ApiKey> keys;
        if (StringUtils.hasText(search) && StringUtils.hasText(status)) {
            keys = apiKeyRep.findByTenantIdAndStatusAndNameContainingIgnoreCase(
                    null, status.toLowerCase(Locale.ROOT), search, pageable);
        } else if (StringUtils.hasText(status)) {
            // Sin tenant específico → necesitamos all, no hay método global en el repo → fallback
            keys = apiKeyRep.findAll(pageable);
        } else {
            keys = apiKeyRep.findAll(pageable);
        }

        return keys.map(this::toApiKeyView);
    }

    public Page<ApiKeyView> listApiKeysByOrg(ObjectId orgId, String search, String status,
                                             int page, int size) {
        orgRepo.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "org_not_found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ApiKey> keys;
        if (StringUtils.hasText(search) && StringUtils.hasText(status)) {
            keys = apiKeyRep.findByTenantIdAndStatusAndNameContainingIgnoreCase(
                    orgId, status.toLowerCase(Locale.ROOT), search, pageable);
        } else if (StringUtils.hasText(search)) {
            keys = apiKeyRep.findByTenantIdAndNameContainingIgnoreCase(orgId, search, pageable);
        } else if (StringUtils.hasText(status)) {
            keys = apiKeyRep.findByTenantIdAndStatus(orgId, status.toLowerCase(Locale.ROOT), pageable);
        } else {
            keys = apiKeyRep.findByTenantId(orgId, pageable);
        }

        return keys.map(this::toApiKeyView);
    }

    /**
     * Activa o revoca una API Key individual.
     * Solo se permiten transiciones: active ↔ revoked
     */
    public ApiKeyView setApiKeyStatus(ObjectId orgId, ObjectId keyId, String newStatus) {
        String status = newStatus.toLowerCase(Locale.ROOT);
        if (!status.equals("active") && !status.equals("revoked"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status_must_be_active_or_revoked");

        ApiKey key = apiKeyRep.findByTenantIdAndId(orgId, keyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api_key_not_found"));

        key.setStatus(status);
        apiKeyRep.save(key);

        log.info("[SantoroPanelService] ApiKey {} → status={}", keyId.toHexString(), status);
        return toApiKeyView(key);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private OrgSummaryDto toOrgSummary(Organization org) {
        ObjectId id = org.getId();
        return OrgSummaryDto.builder()
                .id(id.toHexString())
                .name(org.getName())
                .domain(org.getDomain())
                .code(org.getCode())
                .slug(org.getSlug())
                .status(org.getStatus())
                .timezone(org.getSettings() != null ? org.getSettings().getTimezone() : null)
                .createdAt(fmt(org.getCreatedAt()))
                .totalUsers(userRepo.countByTenantId(id))
                .activeUsers(userRepo.countByTenantIdAndStatus(id, "active"))
                .inactiveUsers(userRepo.countByTenantIdAndStatus(id, "disabled"))
                .totalApiKeys(apiKeyRep.findByTenantId(id, Pageable.unpaged()).getTotalElements())
                .activeApiKeys(apiKeyRep.findByTenantIdAndStatus(id, "active", Pageable.unpaged()).getTotalElements())
                .revokedApiKeys(apiKeyRep.findByTenantIdAndStatus(id, "revoked", Pageable.unpaged()).getTotalElements())
                .expiredApiKeys(apiKeyRep.findByTenantIdAndStatus(id, "expired", Pageable.unpaged()).getTotalElements())
                .build();
    }

    private UserView toUserView(User u) {
        // Resolver nombre de la organización
        String orgName = u.getTenantId() != null
                ? orgRepo.findById(u.getTenantId()).map(Organization::getName).orElse("—")
                : "—";

        return UserView.builder()
                .id(u.getId().toHexString())
                .tenantId(u.getTenantId() != null ? u.getTenantId().toHexString() : null)
                .orgName(orgName)
                .email(u.getEmail())
                .name(u.getName())
                .status(u.getStatus())
                .mustChangePassword(u.isMustChangePassword())
                .lastLoginAt(fmt(u.getLastLoginAt()))
                .createdAt(fmt(u.getCreatedAt()))
                .build();
    }

    private ApiKeyView toApiKeyView(ApiKey k) {
        String orgName = k.getTenantId() != null
                ? orgRepo.findById(k.getTenantId()).map(Organization::getName).orElse("—")
                : "—";

        return ApiKeyView.builder()
                .id(k.getId().toHexString())
                .tenantId(k.getTenantId() != null ? k.getTenantId().toHexString() : null)
                .orgName(orgName)
                .name(k.getName())
                .status(k.getStatus())
                .lastUsedAt(fmt(k.getLastUsedAt()))
                .expiresAt(fmt(k.getExpiresAt()))
                .createdAt(fmt(k.getCreatedAt()))
                .build();
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    @lombok.Data @lombok.Builder
    public static class UserView {
        private String id, tenantId, orgName, email, name, status;
        private boolean mustChangePassword;
        private String lastLoginAt, createdAt;
    }

    @lombok.Data @lombok.Builder
    public static class ApiKeyView {
        private String id, tenantId, orgName, name, status;
        private String lastUsedAt, expiresAt, createdAt;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long countKeysByStatus(String status) {
        return apiKeyRep.findAll().stream()
                .filter(k -> status.equalsIgnoreCase(k.getStatus()))
                .count();
    }

    private String fmt(Instant t) {
        return t == null ? null : FMT.format(t);
    }

    /**
     * Genera una contraseña temporal segura de 12 caracteres.
     * Formato: letras + números + símbolo, fácil de comunicar verbalmente.
     */
    private String generateSecurePassword() {
        byte[] bytes = new byte[9];
        new SecureRandom().nextBytes(bytes);
        String base = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return base.substring(0, 8) + "!";   // ej. "aBcD1234!"
    }
}
