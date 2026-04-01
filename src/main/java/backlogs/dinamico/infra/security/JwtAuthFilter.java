package backlogs.dinamico.infra.security;

import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String CLAIM_PERMS_PRIMARY = "perms";
    private static final String CLAIM_PERMS_ALT     = "permissions";
    private static final String CLAIM_ORG_WIDE      = "orgWide";
    private static final String CLAIM_SYSTEMS       = "systems";

    private final JwtTokenService tokens;

    private static final RequestMatcher LOG_EVENTS_POST =
            new AntPathRequestMatcher("/api/logs/events/**", "POST");
    private static final RequestMatcher LOGS_POST =
            new AntPathRequestMatcher("/api/logs/**", "POST");
    private static final RequestMatcher INGEST_POST =
            new AntPathRequestMatcher("/api/ingest/**", "POST");
    private static final RequestMatcher FINGERPRINT_POST =
            new AntPathRequestMatcher("/api/fingerprint/**", "POST");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;
        return LOG_EVENTS_POST.matches(request)
                || LOGS_POST.matches(request)
                || INGEST_POST.matches(request)
                || FINGERPRINT_POST.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        boolean hasBearer = header != null && header.startsWith("Bearer ");

        if (!hasBearer || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7).trim();
            if (!StringUtils.hasText(token)) {
                chain.doFilter(request, response);
                return;
            }

            Claims c = tokens.verifyAccess(token);

            String email  = c.getSubject();
            String name   = c.get("name", String.class);
            String uidHex = c.get("uid", String.class);

            String tenantHex = firstNonBlank(
                    c.get("tenantId", String.class),
                    c.get("orgId", String.class),
                    c.get("organizationId", String.class),
                    c.get("organization_id", String.class),
                    c.get("org", String.class)
            );

            String orgHex = firstNonBlank(
                    c.get("orgId", String.class),
                    c.get("organizationId", String.class),
                    c.get("organization_id", String.class),
                    c.get("org", String.class)
            );

            ObjectId tenantId = toObjectId(tenantHex);
            ObjectId orgId    = toObjectId(orgHex);
            ObjectId userId   = toObjectId(uidHex);

            var prev = TenantContext.get();
            if (tenantId == null) tenantId = prev.getTenantId();
            if (orgId == null)    orgId    = prev.getOrganizationId();

            if (tenantId == null) throw new JwtException("tenant_not_resolved_in_jwt");
            if (orgId == null)    orgId = tenantId;

            // ── Roles ─────────────────────────────────────────────────────────
            @SuppressWarnings("unchecked")
            List<Object> rolesRaw = c.get("roles", List.class);

            List<String> roleCodes = normalizeList(rolesRaw)
                    .map(s -> s.startsWith("ROLE_") ? s.substring("ROLE_".length()) : s)
                    .map(s -> s.toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();

            // ── Permisos ──────────────────────────────────────────────────────
            @SuppressWarnings("unchecked")
            List<Object> permsRaw = firstNonNullList(
                    c.get(CLAIM_PERMS_PRIMARY, List.class),
                    c.get(CLAIM_PERMS_ALT, List.class)
            );

            List<String> permCodes = normalizeList(permsRaw)
                    .map(s -> s.startsWith("PERM_") ? s.substring("PERM_".length()) : s)
                    .map(s -> s.toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();

            // ── Scope ─────────────────────────────────────────────────────────
            Boolean orgWide = c.get(CLAIM_ORG_WIDE, Boolean.class);
            boolean isOrgWide = orgWide != null && orgWide;

            @SuppressWarnings("unchecked")
            List<Object> systemsRaw = c.get(CLAIM_SYSTEMS, List.class);

            List<String> allowedSystems = normalizeList(systemsRaw)
                    .map(s -> s.toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();

            if (isOrgWide) allowedSystems = List.of();

            // ── TenantContext ─────────────────────────────────────────────────
            TenantContext.set(TenantContext.Ctx.builder()
                    .tenantId(tenantId)
                    .organizationId(orgId)
                    .userId(userId)
                    .email(email)
                    .name(name)
                    .systemId(prev.getSystemId())
                    .environmentId(prev.getEnvironmentId())
                    .dbName(prev.getDbName())
                    .collectionSuffix(prev.getCollectionSuffix())
                    .build());

            // ── Log Filters ───────────────────────────────────────────────────
            @SuppressWarnings("unchecked")
            List<Object> lfOutcomesRaw   = c.get("lfOutcomes",   List.class);
            @SuppressWarnings("unchecked")
            List<Object> lfStatusesRaw   = c.get("lfStatuses",   List.class);
            @SuppressWarnings("unchecked")
            List<Object> lfSeveritiesRaw = c.get("lfSeverities", List.class);
            @SuppressWarnings("unchecked")
            List<Object> lfEventTypesRaw = c.get("lfEventTypes", List.class);

            UserRole.LogFilter logFilters = UserRole.LogFilter.builder()
                    .allowedOutcomes(toStringSet(lfOutcomesRaw))
                    .allowedStatuses(toStringSet(lfStatusesRaw))
                    .allowedSeverities(toStringSet(lfSeveritiesRaw))
                    .allowedEventTypes(toStringSet(lfEventTypesRaw))
                    .build();

            // ── Authorities ───────────────────────────────────────────────────
            List<GrantedAuthority> authorities = new ArrayList<>(roleCodes.size() + permCodes.size());
            roleCodes.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            permCodes.forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));

            // ── Principal ─────────────────────────────────────────────────────
            AuthUser principal = new AuthUser(
                    userId,
                    email,
                    name,
                    tenantId,
                    roleCodes,
                    permCodes,
                    isOrgWide,
                    allowedSystems,
                    logFilters,
                    authorities
            );

            // ── CRÍTICO: setear autenticación ANTES de chain.doFilter() ───────
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            chain.doFilter(request, response);   // ← al final, con auth ya lista

        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            log.warn("[JwtAuthFilter] JWT invalid: {} — token: {}", e.getMessage(),
                    header != null ? header.substring(0, Math.min(header.length(), 30)) : "null");
            chain.doFilter(request, response);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Set<String> toStringSet(List<Object> raw) {
        if (raw == null || raw.isEmpty()) return new HashSet<>();
        return raw.stream()
                .map(String::valueOf)
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static ObjectId toObjectId(String hex) {
        if (!StringUtils.hasText(hex)) return null;
        return ObjectId.isValid(hex) ? new ObjectId(hex) : null;
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) return null;
        for (String x : xs) {
            if (StringUtils.hasText(x)) return x.trim();
        }
        return null;
    }

    @SafeVarargs
    private static List<Object> firstNonNullList(List<Object>... lists) {
        if (lists == null) return null;
        for (List<Object> l : lists) {
            if (l != null) return l;
        }
        return null;
    }

    private static Stream<String> normalizeList(List<Object> raw) {
        if (raw == null) return Stream.empty();
        return raw.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(s -> !s.isBlank());
    }
}