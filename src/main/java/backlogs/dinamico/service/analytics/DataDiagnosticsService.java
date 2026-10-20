package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.intelligence.*;
import backlogs.dinamico.security.auth.ScopeGuard;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Diagnóstico de cobertura de datos — Fase M0 del módulo "Inteligencia de Actividad y Usuarios".
 *
 * SOLO LECTURA sobre log_events. No crea colecciones, no hace backfills,
 * no modifica datos. Sirve para decidir si el tenant+system está listo para
 * construir identidades unificadas (Fase M1) sobre los datos existentes.
 *
 * Todas las agregaciones usan hint idx_tenant_system_time_v2, allowDiskUse,
 * maxTime 3000ms y cursor batchSize 1000 (patrón DashboardLogService).
 * Nunca se cargan eventos a memoria: solo resultados agregados acotados.
 *
 * Antes de agregar se verifica en metadata (solo lectura, sin escaneo) que
 * existe el índice requerido; si falta o no se puede verificar, la consulta se
 * rechaza con 503 y el motivo, para no degradar a COLLSCAN en producción.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataDiagnosticsService {

    private static final String COLLECTION = "log_events";
    private static final String HINT_INDEX = "idx_tenant_system_time_v2";

    /** Índices que deben existir antes de ejecutar cualquier agregación (verificación de solo lectura). */
    private static final List<String> REQUIRED_INDEXES = List.of(HINT_INDEX);

    private static final int CURSOR_BATCH_SIZE = 1000;
    private static final long MAX_AGGREGATION_TIME_MS = 3000;

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 90;

    private static final String LAG_NOTE = "estimated_from_objectid";
    private static final String AUTH_EVENT_REGEX =
            "LOGIN|LOGOUT|AUTH|SESSION|SESION|REGISTER|REGISTRO|SIGNIN|SIGNOUT";

    private static final String READINESS_READY = "READY_FOR_BACKFILL";
    private static final String READINESS_CAVEATS = "READY_WITH_CAVEATS";
    private static final String READINESS_NOT_READY = "NOT_READY";

    private final MongoTemplate mongoTemplate;

    /**
     * Genera el reporte de cobertura para un tenant+system en una ventana de días.
     *
     * @param system sistema requerido, normalizado a UPPER
     * @param days ventana en días (default 30, máx 90)
     * @return CoverageReportDto nunca null
     */
    public CoverageReportDto coverage(String system, Integer days) {
        // ── 1. Validaciones y normalización ──────────────────────────────────
        String normalizedSystem = ScopeGuard.normalizeUpper(system);
        if (normalizedSystem == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system is required");
        }

        int windowDays = days != null ? days : DEFAULT_WINDOW_DAYS;
        if (windowDays < 1) windowDays = 1;
        if (windowDays > MAX_WINDOW_DAYS) windowDays = MAX_WINDOW_DAYS;

        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_not_resolved");
        }

        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(windowDays));

        // ── 1b. Verificación de índices requeridos (solo lectura, metadata) ──
        // Si falta el hint o no se puede verificar, se rechaza la consulta de
        // forma segura (sin agregaciones costosas) y se explica el motivo.
        verifyRequiredIndexes();

        Criteria windowCriteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").is(normalizedSystem)
                .and("eventTime").gte(Date.from(from)).lt(Date.from(now));

        // ── 2. Agregaciones (identidad / sesión-auth / temporal / global) ────
        IdentityFacet identity = runIdentityAggregation(windowCriteria);
        SessionFacet session = runSessionAggregation(windowCriteria);
        TemporalFacet temporal = runTemporalAggregation(windowCriteria, now);
        GlobalBounds bounds = runGlobalBounds(tenantId, normalizedSystem);

        // ── 3. Construcción de DTOs ──────────────────────────────────────────
        long total = temporal.totalEvents;
        boolean empty = total == 0;

        ActorCoverageDto actorCoverage = buildActorCoverage(identity, empty);
        IdentityConflictDto conflicts = buildIdentityConflicts(identity);
        SessionAuthDto sessionAuth = buildSessionAuth(session, empty);
        TemporalDto temporalDto = buildTemporal(temporal, bounds, empty);
        DataQualityDto dataQuality = buildDataQuality(temporal);

        // ── 4. Readiness conservador ─────────────────────────────────────────
        List<String> readinessNotes = new ArrayList<>();
        String readiness = computeReadiness(total, identity, readinessNotes);
        appendDataQualityNotes(temporal, readinessNotes);

        return CoverageReportDto.builder()
                .tenantId(tenantId.toHexString())
                .system(normalizedSystem)
                .windowDays(windowDays)
                .generatedAt(now)
                .actorCoverage(actorCoverage)
                .identityConflicts(conflicts)
                .sessionAuth(sessionAuth)
                .temporal(temporalDto)
                .dataQuality(dataQuality)
                .readiness(readiness)
                .readinessNotes(readinessNotes)
                .build();
    }

    /**
     * Verifica en MongoDB (operación de solo lectura sobre metadata, sin escaneo
     * de documentos) que existen los índices que las agregaciones usan como hint.
     *
     * Si falta un índice requerido, rechaza la consulta con 503 y explica el
     * motivo: ejecutarlas implicaría COLLSCAN por tenant+system sobre toda la
     * colección. Equivalente manual: db.log_events.getIndexes().
     */
    private void verifyRequiredIndexes() {
        Set<String> existing;
        try {
            existing = mongoTemplate.indexOps(COLLECTION).getIndexInfo().stream()
                    .map(index -> index.getName())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[DataDiagnostics] Index verification failed: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "index_verification_failed: no se pudo verificar la existencia de índices "
                            + "requeridos (" + REQUIRED_INDEXES + "); consultas abortadas por seguridad");
        }

        List<String> missing = REQUIRED_INDEXES.stream()
                .filter(index -> !existing.contains(index))
                .toList();
        if (!missing.isEmpty()) {
            log.warn("[DataDiagnostics] Required index missing: {}", missing);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "required_index_missing: " + missing
                            + " — sin este índice las agregaciones degradan a COLLSCAN; "
                            + "verificar con db.log_events.getIndexes() y crear manualmente "
                            + "antes de ejecutar el diagnóstico");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READINESS
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Regla conservadora documentada:
     * - 0 eventos en ventana ⇒ NOT_READY.
     * - actor.id < 20% o actor.type ausente > 40% ⇒ NOT_READY (gravedad extrema).
     * - actor.id < 50% o actor.type ausente > 20% ⇒ READY_WITH_CAVEATS.
     * - resto ⇒ READY_FOR_BACKFILL.
     */
    private String computeReadiness(long total, IdentityFacet identity, List<String> notes) {
        if (total == 0) {
            notes.add("no_events_in_window: no hay eventos para evaluar cobertura");
            return READINESS_NOT_READY;
        }

        double actorIdCoverage = pct(identity.actorIdPresent, total);
        double typeMissing = pct(total - identity.typePresent, total);

        boolean severe = actorIdCoverage < 20.0 || typeMissing > 40.0;
        boolean caveats = actorIdCoverage < 50.0 || typeMissing > 20.0;

        if (actorIdCoverage < 50.0) {
            notes.add(String.format(
                    Locale.ROOT,
                    "actor_id_coverage_below_50: solo %.1f%% de los eventos tienen actor.id (%d/%d)",
                    actorIdCoverage, identity.actorIdPresent, total));
        }
        if (typeMissing > 20.0) {
            notes.add(String.format(
                    Locale.ROOT,
                    "actor_type_missing_above_20: %.1f%% de los eventos sin actor.type",
                    typeMissing));
        }

        if (severe) {
            notes.add("severity_extreme: actor.id < 20% o actor.type ausente > 40%");
            return READINESS_NOT_READY;
        }
        if (caveats) {
            return READINESS_CAVEATS;
        }
        return READINESS_READY;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUILDERS DE DTOs
    // ─────────────────────────────────────────────────────────────────────────

    private ActorCoverageDto buildActorCoverage(IdentityFacet f, boolean empty) {
        if (empty) {
            return ActorCoverageDto.builder()
                    .actorIdCoveragePct(MetricValueDto.notAvailable("no_events_in_window"))
                    .actorIdDistinct(MetricValueDto.notAvailable("no_events_in_window"))
                    .usernameCoveragePct(MetricValueDto.notAvailable("no_events_in_window"))
                    .usernameDistinct(MetricValueDto.notAvailable("no_events_in_window"))
                    .fullNameCoveragePct(MetricValueDto.notAvailable("no_events_in_window"))
                    .fullNameDistinct(MetricValueDto.notAvailable("no_events_in_window"))
                    .typeCoveragePct(MetricValueDto.notAvailable("no_events_in_window"))
                    .typeDistinct(MetricValueDto.notAvailable("no_events_in_window"))
                    .typeDistribution(List.of())
                    .identityCounts(Map.of())
                    .identityNote("no_events_in_window")
                    .build();
        }

        long total = f.total;
        double typeMissingPct = pct(total - f.typePresent, total);
        String identityNote = typeMissingPct > 20.0
                ? String.format(Locale.ROOT,
                        "preliminary_classification: actor.type ausente en %.1f%% de los eventos; "
                                + "conteos por identidad preliminar basados en actor.type (DEGRADED)",
                        typeMissingPct)
                : "preliminary_classification based on actor.type";

        return ActorCoverageDto.builder()
                .actorIdCoveragePct(MetricValueDto.degraded(pct(f.actorIdPresent, total),
                        "presence_not_validated"))
                .actorIdDistinct(MetricValueDto.available(f.actorIdDistinct))
                .usernameCoveragePct(MetricValueDto.degraded(pct(f.usernamePresent, total),
                        "presence_not_validated"))
                .usernameDistinct(MetricValueDto.available(f.usernameDistinct))
                .fullNameCoveragePct(MetricValueDto.degraded(pct(f.fullNamePresent, total),
                        "presence_not_validated"))
                .fullNameDistinct(MetricValueDto.available(f.fullNameDistinct))
                .typeCoveragePct(MetricValueDto.degraded(pct(f.typePresent, total),
                        "presence_not_validated"))
                .typeDistinct(MetricValueDto.available(f.typeDistinct))
                .typeDistribution(f.typeDistribution)
                .identityCounts(f.identityCounts)
                .identityNote(identityNote)
                .build();
    }

    private IdentityConflictDto buildIdentityConflicts(IdentityFacet f) {
        List<IdentityConflictDto.ConflictDto> conflicts = f.conflictsTop.stream()
                .map(doc -> IdentityConflictDto.ConflictDto.builder()
                        .username(doc.getString("username"))
                        .distinctActorIds(((Number) doc.getOrDefault("distinctActorIds", 0)).intValue())
                        .sampleActorIds(doc.getList("sampleActorIds", String.class, List.of()))
                        .build())
                .toList();

        return IdentityConflictDto.builder()
                .conflictedUsernames(f.conflictedUsernames)
                .conflicts(conflicts)
                .build();
    }

    private SessionAuthDto buildSessionAuth(SessionFacet f, boolean empty) {
        if (empty) {
            return SessionAuthDto.builder()
                    .sessionIdCoveragePct(MetricValueDto.notAvailable("no_events_in_window"))
                    .sessionIdDistinct(MetricValueDto.notAvailable("no_events_in_window"))
                    .authResultCoveragePct(MetricValueDto.notAvailable("no_events_in_window"))
                    .authResultDistribution(List.of())
                    .authRelatedEventTypes(List.of())
                    .build();
        }

        return SessionAuthDto.builder()
                .sessionIdCoveragePct(MetricValueDto.degraded(pct(f.sessionIdPresent, f.total),
                        "presence_not_validated"))
                .sessionIdDistinct(MetricValueDto.available(f.sessionIdDistinct))
                .authResultCoveragePct(MetricValueDto.degraded(pct(f.authResultPresent, f.total),
                        "presence_not_validated"))
                .authResultDistribution(f.authResultDistribution)
                .authRelatedEventTypes(f.authEventTypes)
                .build();
    }

    private TemporalDto buildTemporal(TemporalFacet t, GlobalBounds bounds, boolean empty) {
        MetricValueDto avg;
        MetricValueDto min;
        MetricValueDto p95;
        if (t.lagCount == 0) {
            avg = MetricValueDto.notAvailable("no_eventTime_values");
            min = MetricValueDto.notAvailable("no_eventTime_values");
            p95 = MetricValueDto.notAvailable("no_eventTime_values");
        } else {
            avg = MetricValueDto.degraded(round1(t.lagAvgMin), LAG_NOTE);
            min = MetricValueDto.degraded(round1(t.lagMinMin), LAG_NOTE);
            p95 = MetricValueDto.degraded(round1(t.lagP95Min), LAG_NOTE + ", p95_bucketed_approx");
        }

        return TemporalDto.builder()
                .totalEventsInWindow(t.totalEvents)
                .dailyVolume(empty ? List.of() : t.dailyVolume)
                .firstEventTime(bounds.firstEventTime)
                .lastEventTime(bounds.lastEventTime)
                .ingestionLagAvgMin(avg)
                .ingestionLagMinMin(min)
                .ingestionLagP95Min(p95)
                .build();
    }

    private DataQualityDto buildDataQuality(TemporalFacet t) {
        return DataQualityDto.builder()
                .eventsWithoutEventTimeInWindow(t.eventsWithoutEventTime)
                .futureEventsInWindow(t.futureEvents)
                .potentialDuplicateGroups(t.duplicateGroups)
                .potentialDuplicateExtraEvents(t.duplicateExtraEvents)
                .build();
    }

    /** Notas informativas de calidad de datos (no cambian el readiness por sí solas). */
    private void appendDataQualityNotes(TemporalFacet t, List<String> notes) {
        if (t.eventsWithoutEventTime > 0) {
            notes.add(String.format(Locale.ROOT,
                    "events_without_eventtime: %d documentos sin eventTime en ventana "
                            + "(invisibles para consultas temporales; posible emisor legacy/roto)",
                    t.eventsWithoutEventTime));
        }
        if (t.futureEvents > 0) {
            notes.add(String.format(Locale.ROOT,
                    "future_events_detected: %d eventos con eventTime futuro excluidos del cálculo de lag",
                    t.futureEvents));
        }
        if (t.duplicateGroups > 0) {
            notes.add(String.format(Locale.ROOT,
                    "potential_duplicates: %d grupos con %d documentos extra (misma tupla "
                            + "eventType+actor.id+eventTime); heurística, verificar antes de deduplicar",
                    t.duplicateGroups, t.duplicateExtraEvents));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGREGACIÓN 1: IDENTIDAD (actor) + CONFLICTOS — una sola pasada con $facet
    // ─────────────────────────────────────────────────────────────────────────

    private IdentityFacet runIdentityAggregation(Criteria windowCriteria) {
        IdentityFacet facet = new IdentityFacet();

        Document presenceGroup = new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", 1))
                .append("actorIdPresent", sumIfPresent("actor.id"))
                .append("actorIdSet", addToSetIfPresent("actor.id"))
                .append("usernamePresent", sumIfPresent("actor.username"))
                .append("usernameSet", addToSetIfPresent("actor.username"))
                .append("fullNamePresent", sumIfPresent("actor.fullName"))
                .append("fullNameSet", addToSetIfPresent("actor.fullName"))
                .append("typePresent", sumIfPresent("actor.type"))
                .append("typeSet", addToSetIfPresent("actor.type")));

        Document usernameNotEmptyMatch = new Document("$match", new Document("actor.username",
                new Document("$exists", true).append("$nin", Arrays.asList(null, ""))));

        Document conflictsGroup = new Document("$group", new Document("_id", "$actor.username")
                .append("actorIds", new Document("$addToSet", "$actor.id")));

        Document conflictsSizeMatch = new Document("$match", new Document(
                "$expr", new Document("$gt", List.of(new Document("$size", "$actorIds"), 1))));

        Document conflictsProject = new Document("$project", new Document("username", "$_id")
                .append("_id", 0)
                .append("distinctActorIds", new Document("$size", "$actorIds"))
                .append("sampleActorIds", new Document("$slice", List.of("$actorIds", 3))));

        Document conflictsSort = new Document("$sort", new Document("distinctActorIds", -1));

        Document conflictsLimit = new Document("$limit", 10);

        Document conflictsCount = new Document("$count", "total");

        Document typeGroup = new Document("$group", new Document("_id",
                new Document("$ifNull", List.of("$actor.type", "UNKNOWN")))
                .append("count", new Document("$sum", 1)));

        Document facetStage = new Document("$facet", new Document()
                .append("presence", List.of(presenceGroup))
                .append("typeDist", List.of(typeGroup))
                .append("conflictsTop", List.of(
                        usernameNotEmptyMatch, conflictsGroup, conflictsSizeMatch,
                        conflictsProject, conflictsSort, conflictsLimit))
                .append("conflictsTotal", List.of(
                        usernameNotEmptyMatch, conflictsGroup, conflictsSizeMatch, conflictsCount)));

        Aggregation aggregation = Aggregation.newAggregation(
                        Aggregation.match(windowCriteria),
                        op(facetStage))
                .withOptions(buildOptions());

        List<Document> results = aggregateQuietly(aggregation, "identity");
        if (results.isEmpty()) {
            return facet;
        }

        Document root = results.get(0);

        List<Document> presence = root.getList("presence", Document.class, List.of());
        if (!presence.isEmpty()) {
            Document p = presence.get(0);
            facet.total = getLong(p, "total");
            facet.actorIdPresent = getLong(p, "actorIdPresent");
            facet.actorIdDistinct = p.getList("actorIdSet", Object.class, List.of()).size();
            facet.usernamePresent = getLong(p, "usernamePresent");
            facet.usernameDistinct = p.getList("usernameSet", Object.class, List.of()).size();
            facet.fullNamePresent = getLong(p, "fullNamePresent");
            facet.fullNameDistinct = p.getList("fullNameSet", Object.class, List.of()).size();
            facet.typePresent = getLong(p, "typePresent");
            facet.typeDistinct = p.getList("typeSet", Object.class, List.of()).size();
        }

        facet.typeDistribution = root.getList("typeDist", Document.class, List.of()).stream()
                .map(doc -> ValueCountDto.builder()
                        .value(String.valueOf(doc.get("_id")))
                        .count(getLong(doc, "count"))
                        .build())
                .toList();

        Map<String, Long> identityCounts = new LinkedHashMap<>();
        identityCounts.put("HUMAN", 0L);
        identityCounts.put("SERVICE", 0L);
        identityCounts.put("DEVICE", 0L);
        identityCounts.put("UNKNOWN", 0L);
        for (ValueCountDto item : facet.typeDistribution) {
            identityCounts.merge(identityBucket(item.getValue()), item.getCount(), Long::sum);
        }
        facet.identityCounts = identityCounts;

        facet.conflictsTop = root.getList("conflictsTop", Document.class, List.of());
        List<Document> conflictsTotal = root.getList("conflictsTotal", Document.class, List.of());
        facet.conflictedUsernames = conflictsTotal.isEmpty() ? 0L : getLong(conflictsTotal.get(0), "total");

        return facet;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGREGACIÓN 2: SESIONES / AUTH — una sola pasada con $facet
    // ─────────────────────────────────────────────────────────────────────────

    private SessionFacet runSessionAggregation(Criteria windowCriteria) {
        SessionFacet facet = new SessionFacet();

        Document summaryGroup = new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", 1))
                .append("sessionIdPresent", sumIfPresent("remoteConnection.sessionId"))
                .append("sessionIdSet", addToSetIfPresent("remoteConnection.sessionId"))
                .append("authResultPresent", sumIfPresent("remoteConnection.authResult")));

        Document authResultGroup = new Document("$group", new Document("_id", "$remoteConnection.authResult")
                .append("count", new Document("$sum", 1)));

        Document authResultSort = new Document("$sort", new Document("count", -1));

        Document authRegexMatch = new Document("$match", new Document("eventType",
                new Document("$regex", AUTH_EVENT_REGEX).append("$options", "i")));

        Document eventTypeGroup = new Document("$group", new Document("_id", "$eventType")
                .append("count", new Document("$sum", 1)));

        Document eventTypeSort = new Document("$sort", new Document("count", -1));
        Document eventTypeLimit = new Document("$limit", 10);

        Document facetStage = new Document("$facet", new Document()
                .append("summary", List.of(summaryGroup))
                .append("authResultDist", List.of(authResultGroup, authResultSort))
                .append("authEventTypes", List.of(authRegexMatch, eventTypeGroup, eventTypeSort, eventTypeLimit)));

        Aggregation aggregation = Aggregation.newAggregation(
                        Aggregation.match(windowCriteria),
                        op(facetStage))
                .withOptions(buildOptions());

        List<Document> results = aggregateQuietly(aggregation, "session-auth");
        if (results.isEmpty()) {
            return facet;
        }

        Document root = results.get(0);

        List<Document> summary = root.getList("summary", Document.class, List.of());
        if (!summary.isEmpty()) {
            Document s = summary.get(0);
            facet.total = getLong(s, "total");
            facet.sessionIdPresent = getLong(s, "sessionIdPresent");
            facet.sessionIdDistinct = s.getList("sessionIdSet", Object.class, List.of()).size();
            facet.authResultPresent = getLong(s, "authResultPresent");
        }

        facet.authResultDistribution = root.getList("authResultDist", Document.class, List.of()).stream()
                .map(doc -> ValueCountDto.builder()
                        .value(String.valueOf(doc.get("_id")))
                        .count(getLong(doc, "count"))
                        .build())
                .toList();

        facet.authEventTypes = root.getList("authEventTypes", Document.class, List.of()).stream()
                .map(doc -> ValueCountDto.builder()
                        .value(String.valueOf(doc.get("_id")))
                        .count(getLong(doc, "count"))
                        .build())
                .toList();

        return facet;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGREGACIÓN 3: TEMPORAL (ventana: total, diario, min/max, lag)
    // ─────────────────────────────────────────────────────────────────────────

    private TemporalFacet runTemporalAggregation(Criteria windowCriteria, Instant now) {
        TemporalFacet facet = new TemporalFacet();

        Document windowGroup = new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", 1))
                .append("first", new Document("$min", "$eventTime"))
                .append("last", new Document("$max", "$eventTime")));

        Document dailyGroup = new Document("$group", new Document("_id",
                new Document("$dateToString", new Document("format", "%Y-%m-%d")
                        .append("date", new Document("$ifNull",
                                List.of("$eventTime", new Document("$toDate", "$_id"))))))
                .append("count", new Document("$sum", 1)));

        Document dailySort = new Document("$sort", new Document("_id", 1));

        Document lagProject = new Document("$project", new Document("lagMinutes",
                new Document("$divide", List.of(
                        new Document("$subtract", List.of(
                                new Document("$toLong", new Document("$toDate", "$_id")),
                                new Document("$toLong", "$eventTime"))),
                        60000.0))));

        // El lag solo se mide sobre eventos ya ocurridos: los eventos con
        // eventTime futuro producen lag negativo y contaminan promedio/percentiles.
        Document lagNonNegative = new Document("$match", new Document("lagMinutes",
                new Document("$gte", 0)));

        Document lagGroup = new Document("$group", new Document("_id", null)
                .append("count", new Document("$sum", 1))
                .append("avg", new Document("$avg", "$lagMinutes"))
                .append("min", new Document("$min", "$lagMinutes")));

        Document lagBucket = new Document("$bucketAuto",
                new Document("groupBy", "$lagMinutes")
                        .append("buckets", 20));

        Document lagMatch = new Document("$match", new Document("eventTime",
                new Document("$exists", true).append("$ne", null)));

        // Documentos sin eventTime (emisores legacy/rotos): invisibles para las
        // medidas temporales, se contabilizan aparte.
        Document noEventTimeMatch = new Document("$match", new Document("$or", List.of(
                new Document("eventTime", new Document("$exists", false)),
                new Document("eventTime", null))));
        Document noEventTimeCount = new Document("$count", "n");

        // Eventos con eventTime futuro (relojes de emisores desajustados).
        Document futureMatch = new Document("$match", new Document("eventTime",
                new Document("$gt", Date.from(now))));
        Document futureCount = new Document("$count", "n");

        // Posibles duplicados: misma tupla (eventType, actor.id, eventTime).
        Document dupGroup = new Document("$group", new Document("_id", new Document()
                .append("t", "$eventType")
                .append("a", "$actor.id")
                .append("ts", "$eventTime"))
                .append("count", new Document("$sum", 1)));
        Document dupMatch = new Document("$match", new Document("$expr",
                new Document("$gt", List.of("$count", 1))));
        Document dupGroupsCount = new Document("$count", "n");
        Document dupExtraGroup = new Document("$group", new Document("_id", null)
                .append("extra", new Document("$sum",
                        new Document("$subtract", List.of("$count", 1)))));

        Document facetStage = new Document("$facet", new Document()
                .append("windowStats", List.of(windowGroup))
                .append("daily", List.of(dailyGroup, dailySort))
                .append("lagSummary", List.of(lagMatch, lagProject, lagNonNegative, lagGroup))
                .append("lagBuckets", List.of(lagMatch, lagProject, lagNonNegative, lagBucket))
                .append("noEventTime", List.of(noEventTimeMatch, noEventTimeCount))
                .append("futures", List.of(futureMatch, futureCount))
                .append("dupGroups", List.of(dupGroup, dupMatch, dupGroupsCount))
                .append("dupExtra", List.of(dupGroup, dupMatch, dupExtraGroup)));

        Aggregation aggregation = Aggregation.newAggregation(
                        Aggregation.match(windowCriteria),
                        op(facetStage))
                .withOptions(buildOptions());

        List<Document> results = aggregateQuietly(aggregation, "temporal");
        if (results.isEmpty()) {
            return facet;
        }

        Document root = results.get(0);

        List<Document> windowStats = root.getList("windowStats", Document.class, List.of());
        if (!windowStats.isEmpty()) {
            Document w = windowStats.get(0);
            facet.totalEvents = getLong(w, "total");
        }

        facet.dailyVolume = root.getList("daily", Document.class, List.of()).stream()
                .map(doc -> DailyCountDto.builder()
                        .date(String.valueOf(doc.get("_id")))
                        .count(getLong(doc, "count"))
                        .build())
                .toList();

        List<Document> lagSummary = root.getList("lagSummary", Document.class, List.of());
        if (!lagSummary.isEmpty()) {
            Document l = lagSummary.get(0);
            facet.lagCount = getLong(l, "count");
            facet.lagAvgMin = ((Number) l.getOrDefault("avg", 0.0)).doubleValue();
            facet.lagMinMin = ((Number) l.getOrDefault("min", 0.0)).doubleValue();
        }

        List<Document> lagBuckets = root.getList("lagBuckets", Document.class, List.of());
        facet.lagP95Min = approximateP95(lagBuckets, facet.lagCount);

        List<Document> noEventTime = root.getList("noEventTime", Document.class, List.of());
        if (!noEventTime.isEmpty()) {
            facet.eventsWithoutEventTime = getLong(noEventTime.get(0), "n");
        }

        List<Document> futures = root.getList("futures", Document.class, List.of());
        if (!futures.isEmpty()) {
            facet.futureEvents = getLong(futures.get(0), "n");
        }

        List<Document> dupGroups = root.getList("dupGroups", Document.class, List.of());
        if (!dupGroups.isEmpty()) {
            facet.duplicateGroups = getLong(dupGroups.get(0), "n");
        }

        List<Document> dupExtra = root.getList("dupExtra", Document.class, List.of());
        if (!dupExtra.isEmpty()) {
            facet.duplicateExtraEvents = getLong(dupExtra.get(0), "extra");
        }

        return facet;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGREGACIÓN 4: MIN/MAX GLOBAL DE eventTime (tenant+system, sin ventana)
    // ─────────────────────────────────────────────────────────────────────────

    private GlobalBounds runGlobalBounds(ObjectId tenantId, String system) {
        GlobalBounds bounds = new GlobalBounds();

        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").is(system);

        Document boundsGroup = new Document("$group", new Document("_id", null)
                .append("first", new Document("$min", "$eventTime"))
                .append("last", new Document("$max", "$eventTime")));

        Aggregation aggregation = Aggregation.newAggregation(
                        Aggregation.match(criteria),
                        op(boundsGroup))
                .withOptions(buildOptions());

        List<Document> results = aggregateQuietly(aggregation, "global-bounds");
        if (results.isEmpty()) {
            return bounds;
        }

        Document doc = results.get(0);
        bounds.firstEventTime = toInstant(doc.get("first"));
        bounds.lastEventTime = toInstant(doc.get("last"));
        return bounds;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private AggregationOptions buildOptions() {
        return AggregationOptions.builder()
                .cursorBatchSize(CURSOR_BATCH_SIZE)
                .allowDiskUse(true)
                .hint(HINT_INDEX)
                .maxTime(Duration.ofMillis(MAX_AGGREGATION_TIME_MS))
                .build();
    }

    private List<Document> aggregateQuietly(Aggregation aggregation, String name) {
        try {
            return mongoTemplate
                    .aggregate(aggregation, COLLECTION, Document.class)
                    .getMappedResults();
        } catch (Exception e) {
            log.warn("[DataDiagnostics] Aggregation {} failed or timed out: {}", name, e.getMessage());
            return List.of();
        }
    }

    /** Operación de agregación cruda con toString útil para logging y diagnóstico. */
    private static AggregationOperation op(Document doc) {
        return new AggregationOperation() {
            @Override
            public Document toDocument(AggregationOperationContext context) {
                return doc;
            }

            @Override
            public String toString() {
                return doc.toString();
            }
        };
    }

    private static Document sumIfPresent(String field) {
        return new Document("$sum", new Document("$cond", List.of(presentExpr(field), 1, 0)));
    }

    private static Document addToSetIfPresent(String field) {
        return new Document("$addToSet", new Document("$cond",
                List.of(presentExpr(field), "$" + field, "$$REMOVE")));
    }

    private static Document presentExpr(String field) {
        return new Document("$and", List.of(
                new Document("$ne", Arrays.asList("$" + field, null)),
                new Document("$ne", List.of("$" + field, ""))));
    }

    /** p95 aproximado desde buckets ordenados de menor a mayor lag. */
    private static double approximateP95(List<Document> buckets, long lagCount) {
        if (buckets.isEmpty() || lagCount <= 0) {
            return 0.0;
        }
        List<Document> sorted = buckets.stream()
                .sorted(Comparator.comparingDouble(d -> bucketMin(d)))
                .toList();
        long threshold = (long) Math.ceil(lagCount * 0.95);
        long cumulative = 0;
        for (Document bucket : sorted) {
            cumulative += getLong(bucket, "count");
            if (cumulative >= threshold) {
                return bucketMax(bucket);
            }
        }
        return bucketMax(sorted.get(sorted.size() - 1));
    }

    private static double bucketMin(Document bucket) {
        Object id = bucket.get("_id");
        if (id instanceof Document range) {
            return ((Number) range.getOrDefault("min", 0.0)).doubleValue();
        }
        return ((Number) bucket.getOrDefault("min", 0.0)).doubleValue();
    }

    private static double bucketMax(Document bucket) {
        Object id = bucket.get("_id");
        if (id instanceof Document range) {
            return ((Number) range.getOrDefault("max", 0.0)).doubleValue();
        }
        return ((Number) bucket.getOrDefault("max", 0.0)).doubleValue();
    }

    /** Clasificación preliminar de identidad a partir de actor.type. */
    private static String identityBucket(String actorType) {
        if (actorType == null) {
            return "UNKNOWN";
        }
        String t = actorType.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) {
            return "UNKNOWN";
        }
        return switch (t) {
            case "USER", "HUMAN", "PERSON", "EMPLOYEE" -> "HUMAN";
            case "SERVICE", "SYSTEM", "APPLICATION", "APP", "API", "BOT", "INTEGRATION" -> "SERVICE";
            case "DEVICE", "WORKSTATION", "MACHINE", "TERMINAL", "KIOSK", "SENSOR" -> "DEVICE";
            default -> "UNKNOWN";
        };
    }

    private static double pct(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return round1(part * 100.0 / total);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static long getLong(Document doc, String key) {
        Object v = doc.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONTENEDORES INTERNOS DE RESULTADOS
    // ─────────────────────────────────────────────────────────────────────────

    private static final class IdentityFacet {
        long total;
        long actorIdPresent;
        int actorIdDistinct;
        long usernamePresent;
        int usernameDistinct;
        long fullNamePresent;
        int fullNameDistinct;
        long typePresent;
        int typeDistinct;
        List<ValueCountDto> typeDistribution = List.of();
        Map<String, Long> identityCounts = Map.of();
        List<Document> conflictsTop = List.of();
        long conflictedUsernames;
    }

    private static final class SessionFacet {
        long total;
        long sessionIdPresent;
        int sessionIdDistinct;
        long authResultPresent;
        List<ValueCountDto> authResultDistribution = List.of();
        List<ValueCountDto> authEventTypes = List.of();
    }

    private static final class TemporalFacet {
        long totalEvents;
        List<DailyCountDto> dailyVolume = List.of();
        long lagCount;
        double lagAvgMin;
        double lagMinMin;
        double lagP95Min;
        long eventsWithoutEventTime;
        long futureEvents;
        long duplicateGroups;
        long duplicateExtraEvents;
    }

    private static final class GlobalBounds {
        Instant firstEventTime;
        Instant lastEventTime;
    }
}
