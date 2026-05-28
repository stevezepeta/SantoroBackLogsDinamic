package backlogs.dinamico.service.logs;

import backlogs.dinamico.api.dto.logs.LogEventIngestReq;
import backlogs.dinamico.api.dto.logs.LogTimelineResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.infra.security.LogFilterCriteria;
import backlogs.dinamico.infra.ws.DashboardNotifier;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.repository.log.LogEventRepository;
import backlogs.dinamico.security.auth.ScopeGuard;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LogEventService {

    private static final String COLLECTION = "log_events";

    private final LogEventRepository repo;
    private final MongoTemplate mongoTemplate;
    private final ScopeGuard scopeGuard;
    private final DashboardNotifier dashboardNotifier;
    private final EventTypeNormalizer eventTypeNormalizer;

    private final DeviceRegistryService deviceRegistryService;

    // ── ALL ───────────────────────────────────────────────────────────────────

    public Page<LogEvent> all(
            Authentication auth,
            String system,       // ← NUEVO
            String eventType,    // ← NUEVO
            String eventCode,
            String status,       // ← NUEVO
            String outcome,      // ← NUEVO
            String severity,     // ← NUEVO
            Instant from,
            Instant to,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));

        // ── SYSTEM FILTER ─────────────────────────────────────────────────────
        String systemNorm = normalizeUpper(system);
        if (StringUtils.hasText(systemNorm)) {
            // Verificar que el usuario tenga acceso al sistema solicitado
            scopeGuard.requireSystemAccess(user, systemNorm);
            cs.add(Criteria.where("system").is(systemNorm));
        } else {

            boolean isAdminOrOwner = user.getRoles() != null &&
                    (user.getRoles().contains("ORG_ADMIN") ||
                        user.getRoles().contains("ORG_OWNER"));

            // Sin system explícito → scope normal del usuario
            if (!user.isOrgWide() && !isAdminOrOwner) {
                var allowed = (user.getAllowedSystems() == null) ? List.<String>of()
                        : user.getAllowedSystems().stream()
                        .filter(StringUtils::hasText)
                        .map(s -> s.trim().toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();
                if (allowed.isEmpty()) return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
                cs.add(Criteria.where("system").in(allowed));
            } else if (user.getAllowedSystems() != null && !user.getAllowedSystems().isEmpty()) {
                var allowed = user.getAllowedSystems().stream()
                        .filter(StringUtils::hasText)
                        .map(s -> s.trim().toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();
                cs.add(Criteria.where("system").in(allowed));
            }
        }

        // ── FILTROS ADICIONALES ───────────────────────────────────────────────
        applyEventTypeFilter(cs, eventType, eventCode);
        if (StringUtils.hasText(status))    cs.add(Criteria.where("status").is(normalizeUpper(status)));
        if (StringUtils.hasText(outcome))   cs.add(Criteria.where("outcome").is(normalizeUpper(outcome)));
        if (StringUtils.hasText(severity))  cs.add(Criteria.where("severity").is(normalizeUpper(severity)));

        // ── FECHAS ────────────────────────────────────────────────────────────
        if (from != null || to != null) {
            Criteria time = Criteria.where("eventTime");
            if (from != null) time = time.gte(from);
            if (to != null)   time = time.lt(to);
            cs.add(time);
        }

        Criteria finalC = new Criteria().andOperator(cs.toArray(new Criteria[0]));
        finalC = LogFilterCriteria.apply(finalC);

        Query q = new Query(finalC);

        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "eventTime";

        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortField));
        q.with(pageable);

        List<LogEvent> items = mongoTemplate.find(q, LogEvent.class, COLLECTION);
        long total = mongoTemplate.count(Query.of(q).limit(-1).skip(-1), COLLECTION);

        return new PageImpl<>(items, pageable, total);
    }


    // ── SEARCH ────────────────────────────────────────────────────────────────

    public Page<LogEvent> search(
            Authentication auth,
            String system,
            Instant from,
            Instant to,
            String caseId,
            String eventType,
            String eventCode,
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
        if (tenantId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");

        system = normalizeUpper(system);

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));

        boolean isAdminOrOwner = user.getRoles() != null &&
                (user.getRoles().contains("ORG_ADMIN") ||
                        user.getRoles().contains("ORG_OWNER"));

        // SCOPE / SYSTEM FILTER
        if (StringUtils.hasText(system)) {
            if(!isAdminOrOwner) {
                scopeGuard.requireSystemAccess(user, system);
            }
            cs.add(Criteria.where("system").is(system));
        } else {
            if (isAdminOrOwner || (user.isOrgWide() &&
                    (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()))) {
            } else {
                var allowed = (user.getAllowedSystems() == null) ? List.<String>of()
                        : user.getAllowedSystems().stream()
                        .filter(StringUtils::hasText)
                        .map(s -> s.trim().toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();

                if (allowed.isEmpty()) return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
                cs.add(Criteria.where("system").in(allowed));
            }
        }

        // FECHAS
        if (from != null || to != null) {
            Criteria time = Criteria.where("eventTime");
            if (from != null) time = time.gte(from);
            if (to != null)   time = time.lt(to);
            cs.add(time);
        }

        // FILTROS del request (los del usuario pueden quedar anulados por logFilters si se solapan)
        if (StringUtils.hasText(caseId))     cs.add(Criteria.where("caseId").is(normalize(caseId)));
        applyEventTypeFilter(cs, eventType, eventCode);
        if (StringUtils.hasText(status))     cs.add(Criteria.where("status").is(normalizeUpper(status)));
        if (StringUtils.hasText(outcome))    cs.add(Criteria.where("outcome").is(normalizeUpper(outcome)));
        if (StringUtils.hasText(severity))   cs.add(Criteria.where("severity").is(normalizeUpper(severity)));

        if (StringUtils.hasText(actorId))    cs.add(Criteria.where("actor.id").is(normalize(actorId)));
        if (StringUtils.hasText(locationId)) cs.add(Criteria.where("location.id").is(normalize(locationId)));
        if (StringUtils.hasText(requestId))  cs.add(Criteria.where("correlation.requestId").is(normalize(requestId)));

        if (StringUtils.hasText(text)) {
            Pattern p = Pattern.compile(".*" + Pattern.quote(text.trim()) + ".*", Pattern.CASE_INSENSITIVE);
            cs.add(new Criteria().orOperator(
                    Criteria.where("message").regex(p),
                    Criteria.where("reason.description").regex(p)
            ));
        }

        Criteria finalC = new Criteria().andOperator(cs.toArray(new Criteria[0]));

        // ── APLICAR logFilters del VIEWER (outcome, status, severity, eventType)
        finalC = LogFilterCriteria.apply(finalC);

        Query q = new Query(finalC);

        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "eventTime";

        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortField));
        q.with(pageable);

        List<LogEvent> items = mongoTemplate.find(q, LogEvent.class, COLLECTION);
        long total = mongoTemplate.count(Query.of(q).limit(-1).skip(-1), COLLECTION);

        return new PageImpl<>(items, pageable, total);
    }

    // ── DETAIL ────────────────────────────────────────────────────────────────

    public LogEvent getById(Authentication auth, ObjectId id) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        LogEvent ev = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "log_not_found"));

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        scopeGuard.requireSystemAccess(user, ev.getSystem());

        return ev;
    }

    // ── TIMELINE ──────────────────────────────────────────────────────────────

    public LogTimelineResponse timeline(
            Authentication auth,
            String system,
            String caseId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        scopeGuard.requireSystemAccess(user, system);

        system = normalizeUpper(system);
        caseId = normalize(caseId);

        if (!StringUtils.hasText(system) || !StringUtils.hasText(caseId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system and caseId are required");

        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 500) size = 500;

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));
        cs.add(Criteria.where("system").is(system));
        cs.add(Criteria.where("caseId").is(caseId));

        if (from != null || to != null) {
            Criteria time = Criteria.where("eventTime");
            if (from != null) time = time.gte(from);
            if (to != null)   time = time.lt(to);
            cs.add(time);
        }

        Criteria finalC = new Criteria().andOperator(cs.toArray(new Criteria[0]));

        // ── APLICAR logFilters también en timeline
        finalC = LogFilterCriteria.apply(finalC);

        Query q = new Query(finalC)
                .with(Sort.by(Sort.Direction.ASC, "eventTime"))
                .skip((long) page * size)
                .limit(size + 1);

        List<LogEvent> eventsPlus = mongoTemplate.find(q, LogEvent.class, COLLECTION);

        boolean hasNext = eventsPlus.size() > size;
        List<LogEvent> events = hasNext ? eventsPlus.subList(0, size) : eventsPlus;

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
                        ev.getActor()       != null ? ev.getActor().getId()                  : null,
                        ev.getActor()       != null ? ev.getActor().getUsername()             : null,
                        ev.getActor()       != null ? ev.getActor().getFullName()             : null,
                        ev.getLocation()    != null ? ev.getLocation().getId()                : null,
                        ev.getLocation()    != null ? ev.getLocation().getName()              : null,
                        ev.getCorrelation() != null ? ev.getCorrelation().getRequestId()      : null,
                        ev.getGeo()         != null ? ev.getGeo().getCoordinates()            : null,
                        ev.getGeo()         != null ? ev.getGeo().getAccuracyMeters()         : null
                ))
                .toList();

        return LogTimelineResponse.withMeta(header, items, page, size, hasNext);
    }

    // ── INGEST (solo API Key) ─────────────────────────────────────────────────

    public LogEvent ingest(LogEventIngestReq req) {
        if (req == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body_required");

        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        if (!TenantContext.isApiKey()) {
            if (TenantContext.isJwt() || TenantContext.get().getUserId() != null)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "human_jwt_cannot_ingest_logs");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "api_key_required_for_ingest");
        }

        validateGeo(req.geo());

        String system = normalizeUpper(req.system());
        if (!StringUtils.hasText(system))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system is required");

        String env    = normalize(req.environment());
        String caseId = normalize(req.caseId());
        Instant eventTime = (req.eventTime() != null) ? req.eventTime() : Instant.now();

        EventTypeNormalizer.NormalizedEventType evType =
                eventTypeNormalizer.normalize(req.eventType());

        String severityRaw  = normalizeUpper(req.severity());
        String severityNorm = LogNormalizationUtils.normalizeSeverity(severityRaw);
        String statusNorm   = normalizeUpper(req.status());
        String outcomeNorm  = normalizeUpper(req.outcome());

        String msgNorm    = normalize(req.message());
        String reasonDesc = (req.reason() != null) ? normalize(req.reason().description()) : null;
        String messageKey = LogNormalizationUtils.buildMessageKey(msgNorm, reasonDesc);
        boolean isError   = LogNormalizationUtils.computeIsError(severityNorm, statusNorm, outcomeNorm);

        LogEvent event = LogEvent.builder()
                .tenantId(tenantId)
                .schemaVersion(req.schemaVersion() == null ? 1 : req.schemaVersion())
                .system(system)
                .environment(env)
                .caseId(caseId)
                .eventTime(eventTime)

                .eventType(evType.category())     // "APP_EVENT"
                .eventCode(evType.code())         // "1034"
                .eventTypeRaw(evType.raw())       // "APP_EVENT_1034"

                .status(statusNorm)
                .outcome(outcomeNorm)
                .severity(severityNorm)
                .message(msgNorm)
                .messageKey(messageKey)
                .isError(isError)
                .geo(mapGeo(req.geo()))
                .actor(req.actor() == null ? null : new LogEvent.Actor(
                        normalize(req.actor().id()),
                        normalizeUpper(req.actor().type()),
                        normalize(req.actor().username()),
                        normalize(req.actor().fullName())
                ))
                .location(req.location() == null ? null : new LogEvent.Location(
                        normalize(req.location().id()),
                        normalize(req.location().name()),
                        normalize(req.location().city()),
                        normalizeUpper(req.location().country())
                ))
                .correlation(req.correlation() == null ? null : new LogEvent.Correlation(
                        normalize(req.correlation().requestId()),
                        normalize(req.correlation().traceId()),
                        normalize(req.correlation().spanId())
                ))
                .http(req.http() == null ? null : new LogEvent.HttpInfo(
                        normalizeUpper(req.http().method()),
                        normalize(req.http().path()),
                        req.http().statusCode(),
                        req.http().latencyMs()
                ))
                .sla(req.sla() == null ? null : new LogEvent.SlaInfo(
                        req.sla().startTime(),
                        req.sla().endTime(),
                        req.sla().elapsedSeconds()
                ))
                .reason(req.reason() == null ? null : new LogEvent.ReasonInfo(
                        normalizeUpper(req.reason().code()),
                        normalize(req.reason().description())
                ))
                .tags(req.tags() == null ? List.of() :
                        req.tags().stream()
                                .filter(StringUtils::hasText)
                                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                                .distinct()
                                .toList()
                )
                .payload(req.payload())
                .meta(req.meta())
                .remoteConnection(req.remoteConnection() == null ? null : new LogEvent.RemoteConnection(
                        req.remoteConnection().sourceIp(),
                        req.remoteConnection().sourcePort(),
                        req.remoteConnection().destinationIp(),
                        req.remoteConnection().destinationPort(),
                        req.remoteConnection().protocol(),
                        req.remoteConnection().authMethod(),
                        req.remoteConnection().authResult(),
                        req.remoteConnection().user(),
                        req.remoteConnection().sessionId(),
                        req.remoteConnection().sessionDuration(),
                        req.remoteConnection().clientType(),
                        req.remoteConnection().sourceCountry(),
                        req.remoteConnection().sourceCity(),
                        req.remoteConnection().isLocalNetwork(),
                        req.remoteConnection().riskScore(),
                        req.remoteConnection().metadata()
                ))
                .build();

        LogEvent saved = repo.save(event);

        // ── Auto-registro de dispositivo ──────────────────────────────────────────
        try {
            String devSystem = saved.getSystem();
            String devType   = null;
            String devIp     = null;
            String devHost   = null;
            String devEvent  = saved.getEventType();

            if (saved.getActor() != null) {
                devType = saved.getActor().getType();
                devHost = saved.getActor().getFullName();
            }

            if (saved.getMeta() != null) {
                Object ipObj = saved.getMeta().get("ip");
                devIp = ipObj != null ? ipObj.toString() : null;
            }

            // Clave de deduplicación: hostname identifica la máquina física.
            // Si el mismo servidor envía logs con IP privada y pública, ambos
            // apuntan al mismo deviceId y se consolidan en UN solo documento.
            String devId = (devHost != null && !devHost.isBlank())
                    ? devHost
                    : saved.getCaseId();

            Double lat = null, lng = null;
            String locName = null;

            if (saved.getGeo() != null
                    && saved.getGeo().getCoordinates() != null
                    && saved.getGeo().getCoordinates().size() >= 2) {
                lng = saved.getGeo().getCoordinates().get(0);
                lat = saved.getGeo().getCoordinates().get(1);
            }

            if (saved.getLocation() != null) {
                locName = saved.getLocation().getName();
            }

            log.info("[LogEvent] Registrando dispositivo: devId={}, system={}, type={}, ip={}, lat={}, lng={}, loc={}",
                    devId, devSystem, devType, devIp, lat, lng, locName);

            deviceRegistryService.upsertFromLog(
                    saved.getTenantId(), devId, devSystem, devType, devIp, devHost,
                    lat, lng, locName, devEvent
            );

            log.info("[LogEvent] Dispositivo registrado OK: {}", devId);

        } catch (Exception e) {
            log.error("[LogEvent] Error registrando dispositivo: {}", e.getMessage(), e);
        }

        // ── Notificar dashboard en tiempo real ───────────────────────────────
        dashboardNotifier.notifyNewLog(tenantId, saved.getSystem());

        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void applyEventTypeFilter(List<Criteria> cs, String eventType, String eventCode) {

        // Si viene un eventType crudo tipo "APP_EVENT_1034", lo normalizamos
        if (StringUtils.hasText(eventType)) {
            EventTypeNormalizer.NormalizedEventType normalized =
                    eventTypeNormalizer.normalize(eventType);

            // Siempre filtra por categoría
            cs.add(Criteria.where("eventType").is(normalized.category()));

            // Si el raw tenía código numérico ("APP_EVENT_1034"),
            // lo agrega como filtro adicional de eventCode
            if (StringUtils.hasText(normalized.code())) {
                cs.add(Criteria.where("eventCode").is(normalized.code()));
            }
            // Si además viene eventCode explícito, tiene prioridad sobre el extraído del raw
            else if (StringUtils.hasText(eventCode)) {
                cs.add(Criteria.where("eventCode").is(eventCode.trim()));
            }
            return;
        }

        // Si no viene eventType pero sí eventCode solo (ej: buscar todos los "1034" sin importar categoría)
        if (StringUtils.hasText(eventCode)) {
            cs.add(Criteria.where("eventCode").is(eventCode.trim()));
        }
    }

    private static String normalize(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v)) return null;
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
        if (c == null || c.size() != 2)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "geo.coordinates debe ser [lon, lat]");
        Double lon = c.get(0), lat = c.get(1);
        if (lon == null || lat == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "geo.coordinates contiene null");
        if (lon < -180 || lon > 180 || lat < -90 || lat > 90)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "geo.coordinates fuera de rango válido");
    }
}