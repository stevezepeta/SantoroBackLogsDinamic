package backlogs.dinamico.service.logs;

import backlogs.dinamico.api.dto.logs.LogEventIngestReq;
import backlogs.dinamico.api.dto.logs.LogTimelineResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.repository.log.LogEventRepository;
import backlogs.dinamico.security.auth.ScopeGuard;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LogEventService {

    private static final String COLLECTION = "log_events";

    private final LogEventRepository repo;
    private final MongoTemplate mongoTemplate;
    private final ScopeGuard scopeGuard;

    //  ---------------- ALL LOG'S -----------------
    public Page<LogEvent> all(
            Authentication auth,
            Instant from,
            Instant to,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));

        // SCOPE: todos los systems visibles
        if (!user.isOrgWide()) {
            var allowed = (user.getAllowedSystems() == null) ? List.<String>of() :
                    user.getAllowedSystems().stream()
                            .filter(StringUtils::hasText)
                            .map(s -> s.trim().toUpperCase(Locale.ROOT))
                            .distinct()
                            .toList();

            if (allowed.isEmpty()) {
                return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
            }
            cs.add(Criteria.where("system").in(allowed));
        }

        // FECHAS
        if (from != null || to != null) {
            Criteria time = Criteria.where("eventTime");
            if (from != null) time = time.gte(from);
            if (to != null) time = time.lt(to); // exclusivo
            cs.add(time);
        }

        Criteria finalC = new Criteria().andOperator(cs.toArray(new Criteria[0]));
        Query q = new Query(finalC);

        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "eventTime";

        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortField));
        q.with(pageable);

        List<LogEvent> items = mongoTemplate.find(q, LogEvent.class, COLLECTION);
        long total = mongoTemplate.count(Query.of(q).limit(-1).skip(-1), COLLECTION);

        return new PageImpl<>(items, pageable, total);
    }

    // ---------- INGEST (SOLO API KEY) ----------
    public LogEvent ingest(LogEventIngestReq req) {

        // 0) Body obligatorio
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body_required");
        }

        // 1) Tenant obligatorio (lo setea ApiKeyTenantFilter)
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }

        // 2) Ingest SOLO por API KEY
        //    - Si llega JWT humano (TenantContext trae userId o authKind=JWT) => forbid
        //    - Si llega header X-Tenant solamente (TenantResolutionFilter) => unauthorized
        if (!TenantContext.isApiKey()) {
            // Diferenciamos mensajes para debug
            if (TenantContext.isJwt() || TenantContext.get().getUserId() != null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "human_jwt_cannot_ingest_logs");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "api_key_required_for_ingest");
        }

        // 3) (Opcional) validar scope de ingesta si tu ScopeGuard lo maneja
        //    Si NO lo usas, puedes comentar esta línea.
        // scopeGuard.requireIngestScope(); // <- si existe en tu proyecto

        // 4) Validaciones normales
        validateGeo(req.geo());

        String system = normalizeUpper(req.system());
        if (!StringUtils.hasText(system)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system is required");
        }

        String env    = normalize(req.environment()); // puede ser null
        String caseId = normalize(req.caseId());

        Instant eventTime = (req.eventTime() != null) ? req.eventTime() : Instant.now();

        LogEvent event = LogEvent.builder()
                .tenantId(tenantId)
                .schemaVersion(req.schemaVersion() == null ? 1 : req.schemaVersion())
                .system(system)
                .environment(env)
                .caseId(caseId)
                .eventTime(eventTime)
                .eventType(normalizeUpper(req.eventType()))
                .status(normalizeUpper(req.status()))
                .outcome(normalizeUpper(req.outcome()))
                .severity(normalizeUpper(req.severity()))
                .message(normalize(req.message()))
                .geo(mapGeo(req.geo()))
                .actor(req.actor() == null ? null :
                        new LogEvent.Actor(
                                normalize(req.actor().id()),
                                normalizeUpper(req.actor().type()),
                                normalize(req.actor().username()),
                                normalize(req.actor().fullName())
                        ))
                .location(req.location() == null ? null :
                        new LogEvent.Location(
                                normalize(req.location().id()),
                                normalize(req.location().name()),
                                normalize(req.location().city()),
                                normalizeUpper(req.location().country())
                        ))
                .correlation(req.correlation() == null ? null :
                        new LogEvent.Correlation(
                                normalize(req.correlation().requestId()),
                                normalize(req.correlation().traceId()),
                                normalize(req.correlation().spanId())
                        ))
                .http(req.http() == null ? null :
                        new LogEvent.HttpInfo(
                                normalizeUpper(req.http().method()),
                                normalize(req.http().path()),
                                req.http().statusCode(),
                                req.http().latencyMs()
                        ))
                .sla(req.sla() == null ? null :
                        new LogEvent.SlaInfo(
                                req.sla().startTime(),
                                req.sla().endTime(),
                                req.sla().elapsedSeconds()
                        ))
                .reason(req.reason() == null ? null :
                        new LogEvent.ReasonInfo(
                                normalizeUpper(req.reason().code()),
                                normalize(req.reason().description())
                        ))
                .tags(req.tags() == null ? List.of() : req.tags())
                .payload(req.payload())
                .meta(req.meta())
                .build();

        return repo.save(event);
    }

    // -------- SEARCH --------------
    public Page<LogEvent> search(
            Authentication auth,
            String system,
            Instant from,
            Instant to,
            String caseId,
            String eventType,
            String status,
            String outcome,
            String severity,
            String actorId,
            String locationId,
            String requestId,
            String text,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }

        // Normaliza system si viene
        system = normalizeUpper(system);

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));

        // SCOPE / SYSTEM FILTER
        if (StringUtils.hasText(system)) {
            // Modo normal: 1 system
            scopeGuard.requireSystemAccess(user, system);
            cs.add(Criteria.where("system").is(system));

        } else {
            // Modo ALL: todos los systems visibles
            if (user.isOrgWide()) {
                // orgWide
            } else {
                var allowed = (user.getAllowedSystems() == null) ? List.<String>of() :
                        user.getAllowedSystems().stream()
                                .filter(StringUtils::hasText)
                                .map(s -> s.trim().toUpperCase(java.util.Locale.ROOT))
                                .distinct()
                                .toList();

                if (allowed.isEmpty()) {
                    return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
                }

                cs.add(Criteria.where("system").in(allowed));
            }
        }

        // FILTROS (los de siempre)
        if (from != null || to != null) {
            Criteria time = Criteria.where("eventTime");
            if (from != null) time = time.gte(from);
            if (to != null) time = time.lt(to); // exclusivo
            cs.add(time);
        }

        if (StringUtils.hasText(caseId)) cs.add(Criteria.where("caseId").is(normalize(caseId)));
        if (StringUtils.hasText(eventType)) cs.add(Criteria.where("eventType").is(normalizeUpper(eventType)));
        if (StringUtils.hasText(status)) cs.add(Criteria.where("status").is(normalizeUpper(status)));
        if (StringUtils.hasText(outcome)) cs.add(Criteria.where("outcome").is(normalizeUpper(outcome)));
        if (StringUtils.hasText(severity)) cs.add(Criteria.where("severity").is(normalizeUpper(severity)));

        if (StringUtils.hasText(actorId)) cs.add(Criteria.where("actor.id").is(normalize(actorId)));
        if (StringUtils.hasText(locationId)) cs.add(Criteria.where("location.id").is(normalize(locationId)));
        if (StringUtils.hasText(requestId)) cs.add(Criteria.where("correlation.requestId").is(normalize(requestId)));

        if (StringUtils.hasText(text)) {
            Pattern p = Pattern.compile(".*" + Pattern.quote(text.trim()) + ".*", Pattern.CASE_INSENSITIVE);
            cs.add(new Criteria().orOperator(
                    Criteria.where("message").regex(p),
                    Criteria.where("reason.description").regex(p)
            ));
        }

        Criteria finalC = new Criteria().andOperator(cs.toArray(new Criteria[0]));
        Query q = new Query(finalC);

        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "eventTime";

        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortField));
        q.with(pageable);

        List<LogEvent> items = mongoTemplate.find(q, LogEvent.class, COLLECTION);
        long total = mongoTemplate.count(Query.of(q).limit(-1).skip(-1), COLLECTION);

        return new PageImpl<>(items, pageable, total);
    }

    // ---------- DETAIL ----------
    public LogEvent getById(Authentication auth, ObjectId id) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        LogEvent ev = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "log_not_found"));

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        scopeGuard.requireSystemAccess(user, ev.getSystem());

        return ev;
    }

    // ---------- TIMELINE ----------
    public LogTimelineResponse timeline(Authentication auth, String system, String caseId, Instant from, Instant to) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        scopeGuard.requireSystemAccess(user, system);

        system = normalizeUpper(system);
        caseId = normalize(caseId);

        if (!StringUtils.hasText(system) || !StringUtils.hasText(caseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system and caseId are required");
        }

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));
        cs.add(Criteria.where("system").is(system));
        cs.add(Criteria.where("caseId").is(caseId));

        if (from != null || to != null) {
            Criteria time = Criteria.where("eventTime");
            if (from != null) time = time.gte(from);
            if (to != null) time = time.lt(to);
            cs.add(time);
        }

        Query q = new Query(new Criteria().andOperator(cs.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.ASC, "eventTime"));

        List<LogEvent> events = mongoTemplate.find(q, LogEvent.class, COLLECTION);

        LogTimelineResponse.Header header = new LogTimelineResponse.Header(system, caseId);

        List<LogTimelineResponse.Item> items = events.stream()
                .map(ev -> new LogTimelineResponse.Item(
                        ev.getId() != null ? ev.getId().toHexString() : null,
                        ev.getEventTime(),
                        ev.getEventType(),
                        ev.getStatus(),
                        ev.getOutcome(),
                        ev.getSeverity(),
                        ev.getMessage(),
                        ev.getActor() != null ? ev.getActor().getId() : null,
                        ev.getActor() != null ? ev.getActor().getUsername() : null,
                        ev.getActor() != null ? ev.getActor().getFullName() : null,
                        ev.getLocation() != null ? ev.getLocation().getId() : null,
                        ev.getLocation() != null ? ev.getLocation().getName() : null,
                        ev.getCorrelation() != null ? ev.getCorrelation().getRequestId() : null,
                        ev.getGeo() != null ? ev.getGeo().getCoordinates() : null,
                        ev.getGeo() != null ? ev.getGeo().getAccuracyMeters() : null
                ))
                .toList();

        return new LogTimelineResponse(header, items);
    }

    // ---------- Helpers ----------
    private static String normalize(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;
        if ("null".equalsIgnoreCase(v)) return null;
        return v;
    }

    private static String normalizeUpper(String v) {
        v = normalize(v);
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }

    private LogEvent.GeoPoint mapGeo(LogEventIngestReq.GeoPoint g) {
        if (g == null) return null;
        return new LogEvent.GeoPoint(
                StringUtils.hasText(g.type()) ? g.type() : "Point",
                g.coordinates(),
                g.accuracyMeters()
        );
    }

    private void validateGeo(LogEventIngestReq.GeoPoint geo) {
        if (geo == null) return;
        List<Double> c = geo.coordinates();
        if (c == null || c.size() != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "geo.coordinates debe ser [lon, lat]");
        }
        Double lon = c.get(0);
        Double lat = c.get(1);

        if (lon == null || lat == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "geo.coordinates contiene null");
        }
        if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "geo.coordinates fuera de rango válido");
        }
    }
}
