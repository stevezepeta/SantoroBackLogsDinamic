package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.analytics.*;
import backlogs.dinamico.config.OperationalMessageCatalog;
import backlogs.dinamico.infra.cache.TimedCache;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.ai.AiAlertRecord;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.repository.ai.AiAlertRepository;
import backlogs.dinamico.security.auth.ScopeGuard;
import backlogs.dinamico.service.catalog.SystemAppService;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Servicio de analítica ejecutiva para supervisores de operaciones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutiveAnalyticsService {

    private static final String COLLECTION = "log_events";
    private static final String HINT_INDEX = "idx_tenant_system_time_v2";
    private static final int CURSOR_BATCH_SIZE = 1000;

    private final OperationalTranslationService translationService;
    private final OperationalMessageCatalog catalog;
    private final MongoTemplate mongoTemplate;
    private final ScopeGuard scopeGuard;
    private final AiAlertRepository aiAlertRepository;
    private final SystemAppService systemAppService;

    private final TimedCache<ExecutiveSummaryResponse> executiveSummaryCache =
            new TimedCache<>("executive-summary", java.time.Duration.ofSeconds(30));
    private final TimedCache<TopFrictionalEventsResponse> topFrictionalEventsCache =
            new TimedCache<>("top-frictional-events", java.time.Duration.ofSeconds(30));

    /**
     * Historial ejecutivo / timeline simplificado para supervisores.
     */
    public ExecutiveTimelineResponse executiveTimeline(
            Authentication auth,
            String system,
            String caseId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }

        if (!StringUtils.hasText(system) || !StringUtils.hasText(caseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system and caseId are required");
        }

        String systemNorm = system.trim().toUpperCase(Locale.ROOT);
        scopeGuard.requireSystemAccess(user, systemNorm);

        Instant from = fromDate != null
                ? fromDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                : null;
        Instant to = toDate != null
                ? toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : null;

        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 500) size = 500;

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));
        cs.add(Criteria.where("system").is(systemNorm));
        cs.add(Criteria.where("caseId").is(caseId.trim()));

        if (from != null || to != null) {
            Criteria time = Criteria.where("eventTime");
            if (from != null) time = time.gte(from);
            if (to != null) time = time.lt(to);
            cs.add(time);
        }

        Criteria finalC = new Criteria().andOperator(cs.toArray(new Criteria[0]));

        Query q = new Query(finalC)
                .with(Sort.by(Sort.Direction.ASC, "eventTime"))
                .skip((long) page * size)
                .limit(size + 1);

        List<LogEvent> eventsPlus;
        try {
            eventsPlus = mongoTemplate.find(q, LogEvent.class, COLLECTION);
        } catch (Exception e) {
            log.warn("[executiveTimeline] Query excedió el tiempo límite o falló: {}", e.getMessage());
            return new ExecutiveTimelineResponse(
                    new ExecutiveTimelineResponse.Header(systemNorm, caseId.trim(), null, null, from, to),
                    Collections.emptyList(),
                    new PageMeta(page, size, false)
            );
        }
        boolean hasNext = eventsPlus.size() > size;
        List<LogEvent> events = hasNext ? eventsPlus.subList(0, size) : eventsPlus;

        List<OperationalEventCard> cards = events.stream()
                .map(translationService::translate)
                .toList();

        ExecutiveTimelineResponse.Header header = new ExecutiveTimelineResponse.Header(
                systemNorm,
                caseId.trim(),
                extractActorName(cards),
                extractLocationName(cards),
                from,
                to
        );

        return new ExecutiveTimelineResponse(header, cards, new PageMeta(page, size, hasNext));
    }

    /**
     * Recupera el recorrido completo de un caso específico para visualización en mapa.
     */
    public UserJourneyResponse userJourney(
            Authentication auth,
            String caseId,
            String system
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }

        if (!StringUtils.hasText(system) || !StringUtils.hasText(caseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system and caseId are required");
        }

        String systemNorm = system.trim().toUpperCase(Locale.ROOT);
        scopeGuard.requireSystemAccess(user, systemNorm);

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));
        cs.add(Criteria.where("system").is(systemNorm));
        cs.add(Criteria.where("caseId").is(caseId.trim()));

        Query q = new Query(new Criteria().andOperator(cs.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.ASC, "eventTime"));

        List<LogEvent> events;
        try {
            events = mongoTemplate.find(q, LogEvent.class, COLLECTION);
        } catch (Exception e) {
            log.warn("[userJourney] Query excedió el tiempo límite o falló: {}", e.getMessage());
            return new UserJourneyResponse(
                    caseId.trim(),
                    systemNorm,
                    new UserJourneyResponse.JourneySummary(null, null, "UNKNOWN", 0, 0, 0, null, null, null),
                    Collections.emptyList()
            );
        }

        if (events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "journey_not_found");
        }

        List<UserJourneyResponse.JourneyStep> steps = new ArrayList<>();
        int order = 1;
        Instant previous = null;
        long successful = 0;
        long failed = 0;

        for (LogEvent event : events) {
            OperationalEventCard card = translationService.translate(event);

            if (card.getStatus() == OperationalStatus.SUCCESS) {
                successful++;
            } else if (card.getStatus() == OperationalStatus.ERROR) {
                failed++;
            }

            String duration = previous != null && event.getEventTime() != null
                    ? formatDuration(java.time.Duration.between(previous, event.getEventTime()))
                    : "0s";

            steps.add(new UserJourneyResponse.JourneyStep(
                    order++,
                    event.getEventTime(),
                    card.getStepLabel(),
                    card.getStatus(),
                    card.getTitle(),
                    card.getDescription(),
                    card.getSuggestedAction(),
                    card.getLatitude(),
                    card.getLongitude(),
                    card.getLocationName(),
                    card.isHasErrorExplanation(),
                    null, // lazy loading placeholder
                    duration
            ));

            previous = event.getEventTime();
        }

        LogEvent first = events.get(0);
        LogEvent last = events.get(events.size() - 1);

        Long durationSeconds = first.getEventTime() != null && last.getEventTime() != null
                ? java.time.Duration.between(first.getEventTime(), last.getEventTime()).getSeconds()
                : null;

        String finalStatus = computeFinalStatus(steps);

        UserJourneyResponse.JourneySummary summary = new UserJourneyResponse.JourneySummary(
                first.getEventTime(),
                last.getEventTime(),
                finalStatus,
                steps.size(),
                successful,
                failed,
                durationSeconds,
                steps.isEmpty() ? null : steps.get(0).locationName(),
                steps.isEmpty() ? null : steps.get(0).locationName()
        );

        return new UserJourneyResponse(caseId, systemNorm, summary, steps);
    }

    /**
     * Resumen ejecutivo global de salud operativa.
     */
    public ExecutiveSummaryResponse executiveSummary(
            Authentication auth,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }

        LocalDate effectiveFrom;
        LocalDate effectiveTo;
        Instant from;
        Instant to;

        if (fromDate != null && toDate != null) {
            effectiveFrom = fromDate;
            effectiveTo = toDate;
            InstantRange range = buildUtcDayRange(effectiveFrom, effectiveTo);
            from = range.from();
            to = range.to();
        } else {
            effectiveFrom = LocalDate.now().minusDays(7);
            effectiveTo = LocalDate.now();
            to = Instant.now().plus(6, ChronoUnit.HOURS);
            from = Instant.now().minus(8, ChronoUnit.DAYS);
        }

        List<String> allowedSystems = resolveAllowedSystems(user);
        if (allowedSystems.isEmpty()) {
            return emptySummary(effectiveFrom, effectiveTo);
        }

        String cacheKey = buildExecutiveSummaryKey(tenantId, effectiveFrom, effectiveTo, allowedSystems);
        return executiveSummaryCache.get(cacheKey, () -> buildExecutiveSummary(
                tenantId, effectiveFrom, effectiveTo, allowedSystems, from, to
        ));
    }

    private ExecutiveSummaryResponse buildExecutiveSummary(
            ObjectId tenantId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            List<String> allowedSystems,
            Instant from,
            Instant to
    ) {
        Map<String, SystemMetrics> metricsBySystem = aggregateSystemMetrics(tenantId, allowedSystems, from, to);
        Map<String, Instant> lastIncidents = aggregateLastIncidents(tenantId, allowedSystems, from, to);
        Map<String, String> displayNames = loadDisplayNames(tenantId, allowedSystems);

        List<ExecutiveSummaryResponse.SystemHealth> systems = new ArrayList<>();
        long totalEvents = 0;
        long totalErrors = 0;
        long totalCases = 0;

        for (String system : allowedSystems) {
            SystemMetrics m = metricsBySystem.getOrDefault(system, new SystemMetrics(0, 0, 0, null));
            double errorRate = m.total > 0 ? Math.round((m.errors * 10000.0 / m.total)) / 100.0 : 0.0;
            String status = determineHealthStatus(m.total, errorRate);

            totalEvents += m.total;
            totalErrors += m.errors;
            totalCases += m.cases;

            systems.add(ExecutiveSummaryResponse.SystemHealth.builder()
                    .system(system)
                    .systemLabel(systemLabel(system))
                    .displayName(displayNames.getOrDefault(system, systemLabel(system)))
                    .status(status)
                    .totalEvents(m.total)
                    .errorCount(m.errors)
                    .errorRate(errorRate)
                    .activeCases(m.cases)
                    .completionRate(null)
                    .lastIncident(lastIncidents.get(system))
                    .lastSeen(m.lastSeen())
                    .build());
        }

        double globalErrorRate = totalEvents > 0 ? Math.round((totalErrors * 10000.0 / totalEvents)) / 100.0 : 0.0;
        String overallHealth = determineOverallHealth(systems, globalErrorRate);

        List<ExecutiveSummaryResponse.ExecutiveAlert> alerts = fetchRecentAlerts(tenantId);
        List<TopFrictionalEventsResponse.FrictionalEvent> topFrictionalEvents =
                aggregateGlobalTopFrictionalEvents(tenantId, allowedSystems, from, to, 5, totalErrors);

        return ExecutiveSummaryResponse.builder()
                .generatedAt(Instant.now())
                .period(new ExecutiveSummaryResponse.Period(effectiveFrom, effectiveTo))
                .overallHealth(overallHealth)
                .systems(systems)
                .activeAlerts(alerts)
                .topMetrics(ExecutiveSummaryResponse.TopMetrics.builder()
                        .totalEvents(totalEvents)
                        .totalErrors(totalErrors)
                        .globalErrorRate(globalErrorRate)
                        .activeCases(totalCases)
                        .build())
                .topFrictionalEvents(topFrictionalEvents)
                .build();
    }

    private String buildExecutiveSummaryKey(
            ObjectId tenantId,
            LocalDate from,
            LocalDate to,
            List<String> allowedSystems
    ) {
        return tenantId + "|" + from + "|" + to + "|" + String.join(",", allowedSystems);
    }

    /**
     * Ranking de eventos que causan mayor fricción a los usuarios.
     * <p>Si se envían {@code from} y {@code to}, filtra por ese rango dinámico.
     * Si no, evalúa el día actual en el huso horario local.</p>
     */
    public TopFrictionalEventsResponse topFrictionalEvents(
            Authentication auth,
            Instant from,
            Instant to,
            String system,
            int limit
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }

        InstantRange range = buildCurrentDayRange(from, to);
        LocalDate effectiveDate = range.from().atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        int finalLimit = Math.max(1, Math.min(limit, 50));

        List<String> allowedSystems = resolveAllowedSystems(user);
        if (allowedSystems.isEmpty()) {
            return TopFrictionalEventsResponse.builder()
                    .date(effectiveDate)
                    .events(List.of())
                    .build();
        }

        String systemNorm = StringUtils.hasText(system)
                ? system.trim().toUpperCase(Locale.ROOT)
                : null;

        List<String> finalAllowedSystems;
        if (systemNorm != null) {
            scopeGuard.requireSystemAccess(user, systemNorm);
            if (!allowedSystems.contains(systemNorm)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "system_not_allowed");
            }
            finalAllowedSystems = List.of(systemNorm);
        } else {
            finalAllowedSystems = allowedSystems;
        }

        String cacheKey = buildTopFrictionalEventsKey(
                tenantId, range.from(), range.to(), systemNorm, finalLimit, finalAllowedSystems);
        return topFrictionalEventsCache.get(cacheKey, () -> buildTopFrictionalEvents(
                tenantId, effectiveDate, finalAllowedSystems, range.from(), range.to(), finalLimit
        ));
    }

    private TopFrictionalEventsResponse buildTopFrictionalEvents(
            ObjectId tenantId,
            LocalDate effectiveDate,
            List<String> allowedSystems,
            Instant from,
            Instant to,
            int limit
    ) {
        long totalErrorsInPeriod = countTotalErrors(tenantId, allowedSystems, from, to);

        // Si no hay errores en el rango, retornamos la estructura con lista vacía.
        if (totalErrorsInPeriod == 0) {
            return TopFrictionalEventsResponse.builder()
                    .date(effectiveDate)
                    .events(List.of())
                    .build();
        }

        List<TopFrictionalEventsResponse.FrictionalEvent> events = runFrictionalEventsAggregation(
                tenantId, allowedSystems, from, to, limit, totalErrorsInPeriod
        );

        return TopFrictionalEventsResponse.builder()
                .date(effectiveDate)
                .events(events)
                .build();
    }

    private String buildTopFrictionalEventsKey(
            ObjectId tenantId,
            Instant from,
            Instant to,
            String system,
            int limit,
            List<String> allowedSystems
    ) {
        return tenantId + "|" + from + "|" + to + "|" + system + "|" + limit + "|" + String.join(",", allowedSystems);
    }

    private InstantRange buildCurrentDayRange(Instant from, Instant to) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        Instant effectiveFrom = from != null
                ? from
                : today.atStartOfDay(zone).toInstant();
        Instant effectiveTo = to != null
                ? to
                : today.atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(zone).toInstant();

        return new InstantRange(effectiveFrom, effectiveTo);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractActorName(List<OperationalEventCard> cards) {
        return cards.stream()
                .map(OperationalEventCard::getActorName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String extractLocationName(List<OperationalEventCard> cards) {
        return cards.stream()
                .map(OperationalEventCard::getLocationName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String formatDuration(java.time.Duration duration) {
        if (duration == null) return "0s";
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes < 60) return remainingSeconds == 0 ? minutes + "m" : minutes + "m " + remainingSeconds + "s";
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return hours + "h " + remainingMinutes + "m";
    }

    private String computeFinalStatus(List<UserJourneyResponse.JourneyStep> steps) {
        if (steps.isEmpty()) return "UNKNOWN";
        boolean hasError = steps.stream().anyMatch(s -> s.status() == OperationalStatus.ERROR);
        boolean allSuccess = steps.stream().allMatch(s -> s.status() == OperationalStatus.SUCCESS);
        if (hasError) return "COMPLETADO_CON_ERRORES";
        if (allSuccess) return "COMPLETADO";
        return "EN_PROGRESO";
    }

    /**
     * Criterio amplio para identificar eventos de error independientemente del campo que lo indique.
     */
    private Criteria buildErrorCriteria() {
        return new Criteria().orOperator(
                Criteria.where("isError").is(true),
                Criteria.where("status").in("ERROR", "FAIL", "FAILURE", "KO", "CRITICAL"),
                Criteria.where("outcome").in("FAILURE", "ERROR", "FAILED"),
                Criteria.where("severity").in("ERROR", "CRITICAL", "FATAL")
        );
    }

    /**
     * Construye un rango UTC que cubre todo el día solicitado:
     * desde las 00:00:00 hasta las 23:59:59.999Z.
     */
    private InstantRange buildUtcDayRange(LocalDate date) {
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date.atTime(LocalTime.of(23, 59, 59, 999_000_000)).toInstant(ZoneOffset.UTC);
        return new InstantRange(from, to);
    }

    /**
     * Construye un rango UTC que cubre desde el inicio de fromDate hasta el final de toDate.
     */
    private InstantRange buildUtcDayRange(LocalDate fromDate, LocalDate toDate) {
        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDate.atTime(LocalTime.of(23, 59, 59, 999_000_000)).toInstant(ZoneOffset.UTC);
        return new InstantRange(from, to);
    }

    private record InstantRange(Instant from, Instant to) {
    }

    private AggregationOptions buildAggregationOptions(String hint) {
        return AggregationOptions.builder()
                .cursorBatchSize(CURSOR_BATCH_SIZE)
                .allowDiskUse(true)
                .hint(hint)
                .maxTime(Duration.ofMillis(3000))
                .build();
    }

    // ── Executive Summary Helpers ─────────────────────────────────────────────

    private List<String> resolveAllowedSystems(AuthUser user) {
        boolean isAdminOrOwner = user.getRoles() != null &&
                (user.getRoles().contains("ORG_ADMIN") || user.getRoles().contains("ORG_OWNER"));

        if (isAdminOrOwner || user.isOrgWide()) {
            if (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()) {
                // Admin sin restricción explícita: necesitamos descubrir sistemas del tenant
                return discoverSystemsForTenant(TenantContext.requireTenantId());
            }
        }

        if (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()) {
            return List.of();
        }

        return user.getAllowedSystems().stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> discoverSystemsForTenant(ObjectId tenantId) {
        Criteria criteria = Criteria.where("tenant_id").is(tenantId);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.project("system").andExclude("_id"),
                Aggregation.group("system"),
                Aggregation.project().and("_id").as("system")
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        log.info("[discoverSystemsForTenant] tenantId={} (type={})", tenantId, tenantId.getClass().getName());
        log.info("[discoverSystemsForTenant] matchJson={}", criteria.getCriteriaObject().toJson());
        log.info("[discoverSystemsForTenant] aggregation={}", aggregation);
        System.out.println("DEBUG - Aggregation Pipeline: " + aggregation.toString());

        try {
            AggregationResults<Document> results = mongoTemplate.aggregate(
                    aggregation, COLLECTION, Document.class
            );

            return results.getMappedResults().stream()
                    .map(d -> d.getString("system"))
                    .filter(StringUtils::hasText)
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.warn("[discoverSystemsForTenant] Query excedió el tiempo límite o falló: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, SystemMetrics> aggregateSystemMetrics(
            ObjectId tenantId,
            List<String> systems,
            Instant from,
            Instant to
    ) {
        Date fromDate = Date.from(from);
        Date toDate = Date.from(to);

        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
                .and("eventTime").gte(fromDate).lte(toDate);

        if (systems != null && !systems.isEmpty()) {
            criteria = criteria.and("system").in(systems);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.project("status", "caseId", "eventTime", "system").andExclude("_id"),
                Aggregation.group("system")
                        .count().as("total")
                        .sum(ConditionalOperators.when(buildErrorCriteria()).then(1).otherwise(0)).as("errors")
                        .addToSet("caseId").as("uniqueCases")
                        .max("eventTime").as("lastSeen")
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        log.info("[aggregateSystemMetrics] tenantId={} (type={}), systems={}, from={}, to={}",
                tenantId, tenantId.getClass().getName(), systems, from, to);
        log.info("[aggregateSystemMetrics] matchJson={}", criteria.getCriteriaObject().toJson());
        log.info("[aggregateSystemMetrics] aggregation={}", aggregation);
        System.out.println("DEBUG - Aggregation Pipeline: " + aggregation.toString());

        try {
            AggregationResults<Document> results = mongoTemplate.aggregate(
                    aggregation, COLLECTION, Document.class
            );

            Map<String, SystemMetrics> map = new HashMap<>();
            for (Document doc : results.getMappedResults()) {
                String system = doc.getString("_id");
                long total = doc.getInteger("total", 0);
                long errors = doc.getInteger("errors", 0);
                List<?> cases = (List<?>) doc.get("uniqueCases");
                long caseCount = cases != null ? cases.size() : 0;
                Instant lastSeen = doc.getDate("lastSeen") != null
                        ? doc.getDate("lastSeen").toInstant()
                        : null;
                if (StringUtils.hasText(system)) {
                    String key = system.trim().toUpperCase(Locale.ROOT);
                    map.merge(key, new SystemMetrics(total, errors, caseCount, lastSeen),
                            (a, b) -> new SystemMetrics(
                                    a.total() + b.total(),
                                    a.errors() + b.errors(),
                                    a.cases() + b.cases(),
                                    b.lastSeen() != null && (a.lastSeen() == null || b.lastSeen().isAfter(a.lastSeen()))
                                            ? b.lastSeen()
                                            : a.lastSeen()));
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("[aggregateSystemMetrics] Query excedió el tiempo límite o falló: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Instant> aggregateLastIncidents(
            ObjectId tenantId,
            List<String> systems,
            Instant from,
            Instant to
    ) {
        Date fromDate = Date.from(from);
        Date toDate = Date.from(to);

        Criteria criteria = new Criteria().andOperator(
                Criteria.where("tenant_id").is(tenantId)
                        .and("system").in(systems)
                        .and("eventTime").gte(fromDate).lte(toDate),
                buildErrorCriteria()
        );

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.project("eventTime").andExclude("_id"),
                Aggregation.group("system").max("eventTime").as("lastIncident")
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        log.info("[aggregateLastIncidents] tenantId={} (type={}), systems={}, from={}, to={}",
                tenantId, tenantId.getClass().getName(), systems, from, to);
        log.info("[aggregateLastIncidents] matchJson={}", criteria.getCriteriaObject().toJson());
        log.info("[aggregateLastIncidents] aggregation={}", aggregation);
        System.out.println("DEBUG - Aggregation Pipeline: " + aggregation.toString());

        try {
            AggregationResults<Document> results = mongoTemplate.aggregate(
                    aggregation, COLLECTION, Document.class
            );

            Map<String, Instant> map = new HashMap<>();
            for (Document doc : results.getMappedResults()) {
                String system = doc.getString("_id");
                Instant lastIncident = doc.getDate("lastIncident") != null
                        ? doc.getDate("lastIncident").toInstant()
                        : null;
                map.put(system, lastIncident);
            }
            return map;
        } catch (Exception e) {
            log.warn("[aggregateLastIncidents] Query excedió el tiempo límite o falló: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private long countTotalErrors(ObjectId tenantId, List<String> systems, Instant from, Instant to) {
        Date fromDate = Date.from(from);
        Date toDate = Date.from(to);

        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").in(systems)
                .and("eventTime").gte(fromDate).lte(toDate)
                .and("isError").is(true);

        log.info("[countTotalErrors] tenantId={} (type={}), systems={}, from={}, to={}",
                tenantId, tenantId.getClass().getName(), systems, from, to);
        log.info("[countTotalErrors] matchJson={}", criteria.getCriteriaObject().toJson());
        System.out.println("DEBUG - Aggregation Pipeline: " + new Query(criteria).toString());

        try {
            return mongoTemplate.count(new Query(criteria).withHint(HINT_INDEX), COLLECTION);
        } catch (Exception e) {
            log.warn("[countTotalErrors] Query excedió el tiempo límite o falló: {}", e.getMessage());
            return 0L;
        }
    }

    private List<TopFrictionalEventsResponse.FrictionalEvent> runFrictionalEventsAggregation(
            ObjectId tenantId,
            List<String> systems,
            Instant from,
            Instant to,
            int limit,
            long totalErrorsInPeriod
    ) {
        Date fromDate = Date.from(from);
        Date toDate = Date.from(to);

        Document matchStage = new Document("$match", new Document()
                .append("tenant_id", tenantId)
                .append("system", new Document("$in", systems))
                .append("eventTime", new Document("$gte", fromDate).append("$lte", toDate))
                .append("isError", true)
        );

        Document projectStageAfterMatch = new Document("$project", new Document()
                .append("eventCode", 1)
                .append("eventType", 1)
                .append("system", 1)
                .append("caseId", 1)
                .append("location.name", 1)
                .append("_id", 0)
        );

        Document groupStage = new Document("$group", new Document()
                .append("_id", new Document()
                        .append("eventKey", new Document("$ifNull", List.of("$eventCode", "$eventType")))
                        .append("system", "$system"))
                .append("occurrenceCount", new Document("$sum", 1))
                .append("affectedCases", new Document("$addToSet", "$caseId"))
                .append("topLocation", new Document("$first", "$location.name"))
        );

        Document projectStage = new Document("$project", new Document()
                .append("eventCode", "$_id.eventKey")
                .append("system", "$_id.system")
                .append("occurrenceCount", 1)
                .append("affectedCases", new Document("$size", "$affectedCases"))
                .append("topLocation", 1)
        );

        Document sortStage = new Document("$sort", new Document("affectedCases", -1));
        Document limitStage = new Document("$limit", limit);

        Aggregation aggregation = Aggregation.newAggregation(
                context -> matchStage,
                context -> projectStageAfterMatch,
                context -> groupStage,
                context -> projectStage,
                context -> sortStage,
                context -> limitStage
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        log.info("[runFrictionalEventsAggregation] tenantId={} (type={}), systems={}, from={}, to={}",
                tenantId, tenantId.getClass().getName(), systems, from, to);
        log.info("[runFrictionalEventsAggregation] pipeline={}",
                List.of(matchStage, projectStageAfterMatch, groupStage, projectStage, sortStage, limitStage));

        try {
            AggregationResults<Document> results = mongoTemplate.aggregate(
                    aggregation, COLLECTION, Document.class
            );

            List<TopFrictionalEventsResponse.FrictionalEvent> events = new ArrayList<>();
            int rank = 1;
            for (Document doc : results.getMappedResults()) {
                String eventCode = doc.getString("eventCode");
                long occurrenceCount = doc.getInteger("occurrenceCount", 0);
                long affectedCases = doc.getInteger("affectedCases", 0);
                double percentage = totalErrorsInPeriod > 0
                        ? Math.round((affectedCases * 10000.0 / totalErrorsInPeriod)) / 100.0
                        : 0.0;

                OperationalMessageCatalog.OperationalMessage message =
                        catalog.find(doc.getString("system"), eventCode, "ERROR");

                events.add(TopFrictionalEventsResponse.FrictionalEvent.builder()
                        .rank(rank++)
                        .eventCode(eventCode)
                        .title(message != null && StringUtils.hasText(message.getTitle())
                                ? message.getTitle()
                                : eventCode)
                        .description(message != null && StringUtils.hasText(message.getDescription())
                                ? message.getDescription()
                                : "Evento con fallos reportados.")
                        .occurrenceCount(occurrenceCount)
                        .affectedCases(affectedCases)
                        .percentageOfTotalErrors(percentage)
                        .trend("STABLE")
                        .trendPercentage(0.0)
                        .system(doc.getString("system"))
                        .topLocation(doc.getString("topLocation"))
                        .recommendedAction(message != null ? message.getAction() : null)
                        .build());
            }

            return events;
        } catch (Exception e) {
            log.warn("[runFrictionalEventsAggregation] Query excedió el tiempo límite o falló: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<TopFrictionalEventsResponse.FrictionalEvent> aggregateGlobalTopFrictionalEvents(
            ObjectId tenantId,
            List<String> systems,
            Instant from,
            Instant to,
            int limit,
            long totalGlobalErrors
    ) {
        if (totalGlobalErrors == 0 || systems.isEmpty()) {
            return List.of();
        }

        Date fromDate = Date.from(from);
        Date toDate = Date.from(to);

        Document matchStage = new Document("$match", new Document()
                .append("tenant_id", tenantId)
                .append("system", new Document("$in", systems))
                .append("eventTime", new Document("$gte", fromDate).append("$lte", toDate))
                .append("$or", List.of(
                        new Document("isError", true),
                        new Document("status", new Document("$in", List.of("ERROR", "FAIL", "FAILURE", "KO", "REJECTED"))),
                        new Document("outcome", new Document("$in", List.of("FAILURE", "ERROR", "FAILED"))),
                        new Document("severity", new Document("$in", List.of("ERROR", "CRITICAL", "FATAL")))
                ))
        );

        Document groupStage = new Document("$group", new Document()
                .append("_id", new Document()
                        .append("eventKey", new Document("$ifNull", List.of("$eventCode", "$eventType")))
                        .append("system", "$system"))
                .append("occurrenceCount", new Document("$sum", 1))
                .append("affectedCases", new Document("$addToSet", "$caseId"))
                .append("topLocation", new Document("$first", "$location.name"))
        );

        Document projectStage = new Document("$project", new Document()
                .append("eventCode", "$_id.eventKey")
                .append("system", "$_id.system")
                .append("occurrenceCount", 1)
                .append("affectedCases", new Document("$size", "$affectedCases"))
                .append("topLocation", 1)
        );

        Document sortStage = new Document("$sort", new Document("occurrenceCount", -1));
        Document limitStage = new Document("$limit", limit);

        Aggregation aggregation = Aggregation.newAggregation(
                context -> matchStage,
                context -> groupStage,
                context -> projectStage,
                context -> sortStage,
                context -> limitStage
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        try {
            AggregationResults<Document> results = mongoTemplate.aggregate(
                    aggregation, COLLECTION, Document.class
            );

            List<TopFrictionalEventsResponse.FrictionalEvent> events = new ArrayList<>();
            int rank = 1;
            for (Document doc : results.getMappedResults()) {
                String eventCode = doc.getString("eventCode");
                String system = doc.getString("system");
                long occurrenceCount = doc.getInteger("occurrenceCount", 0);
                long affectedCases = doc.getInteger("affectedCases", 0);
                double percentage = totalGlobalErrors > 0
                        ? Math.round((occurrenceCount * 10000.0 / totalGlobalErrors)) / 100.0
                        : 0.0;

                OperationalMessageCatalog.OperationalMessage message = catalog.find(system, eventCode, "ERROR");

                events.add(TopFrictionalEventsResponse.FrictionalEvent.builder()
                        .rank(rank++)
                        .eventCode(eventCode)
                        .title(message != null && StringUtils.hasText(message.getTitle())
                                ? message.getTitle()
                                : eventCode)
                        .description(message != null && StringUtils.hasText(message.getDescription())
                                ? message.getDescription()
                                : "Evento con fallos reportados.")
                        .occurrenceCount(occurrenceCount)
                        .affectedCases(affectedCases)
                        .percentageOfTotalErrors(percentage)
                        .trend("STABLE")
                        .trendPercentage(0.0)
                        .system(system)
                        .topLocation(doc.getString("topLocation"))
                        .recommendedAction(message != null ? message.getAction() : null)
                        .build());
            }

            return events;
        } catch (Exception e) {
            log.warn("[aggregateGlobalTopFrictionalEvents] Query excedió el tiempo límite o falló: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ExecutiveSummaryResponse.ExecutiveAlert> fetchRecentAlerts(ObjectId tenantId) {
        return aiAlertRepository.findByTenantId(tenantId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .map(this::mapAlert)
                .toList();
    }

    private ExecutiveSummaryResponse.ExecutiveAlert mapAlert(AiAlertRecord record) {
        String message = record.getTopErrorKey() != null
                ? "Alerta detectada en " + record.getTopSystem() + ": " + record.getTopErrorKey()
                : "Alerta operativa detectada";

        return ExecutiveSummaryResponse.ExecutiveAlert.builder()
                .id(record.getId() != null ? record.getId().toHexString() : null)
                .severity(record.getStatus())
                .title("Alerta " + (record.getStatus() != null ? record.getStatus() : "OK"))
                .message(message)
                .system(record.getTopSystem())
                .timestamp(record.getCreatedAt())
                .build();
    }

    private ExecutiveSummaryResponse emptySummary(LocalDate from, LocalDate to) {
        return ExecutiveSummaryResponse.builder()
                .generatedAt(Instant.now())
                .period(new ExecutiveSummaryResponse.Period(from, to))
                .overallHealth("INACTIVE")
                .systems(List.of())
                .activeAlerts(List.of())
                .topMetrics(ExecutiveSummaryResponse.TopMetrics.builder()
                        .totalEvents(0)
                        .totalErrors(0)
                        .globalErrorRate(0.0)
                        .activeCases(0)
                        .build())
                .topFrictionalEvents(List.of())
                .build();
    }

    private String determineHealthStatus(long totalEvents, double errorRate) {
        if (totalEvents == 0) return "INACTIVE";
        if (errorRate > 10.0) return "CRITICAL";
        if (errorRate > 0.0) return "WARNING";
        return "STABLE";
    }

    private String determineOverallHealth(List<ExecutiveSummaryResponse.SystemHealth> systems, double globalErrorRate) {
        if (systems.isEmpty()) return "INACTIVE";
        boolean hasCritical = systems.stream().anyMatch(s -> "CRITICAL".equals(s.getStatus()));
        boolean hasWarning = systems.stream().anyMatch(s -> "WARNING".equals(s.getStatus()));
        if (hasCritical || globalErrorRate > 5.0) return "CRITICAL";
        if (hasWarning || globalErrorRate > 0.0) return "WARNING";
        return "STABLE";
    }

    private String systemLabel(String system) {
        return switch (system) {
            case "TRUSTVALUE" -> "Control de Asistencia";
            case "CITA_GUYANA" -> "Citas Guyana";
            case "CITA_QUINTANAROO" -> "Citas Quintana Roo";
            case "TICKETS" -> "Soporte de Tickets";
            case "PASSPORT" -> "Pasaportes";
            default -> system;
        };
    }

    private Map<String, String> loadDisplayNames(ObjectId tenantId, List<String> systems) {
        try {
            return systemAppService.getDisplayNamesByCode(tenantId);
        } catch (Exception e) {
            log.warn("[loadDisplayNames] No se pudieron cargar nombres de sistema: {}", e.getMessage());
            return Map.of();
        }
    }

    private record SystemMetrics(long total, long errors, long cases, Instant lastSeen) {
    }
}
