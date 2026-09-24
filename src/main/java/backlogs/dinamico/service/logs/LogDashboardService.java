package backlogs.dinamico.service.logs;

import backlogs.dinamico.api.dto.logs.*;
import backlogs.dinamico.config.AlertThresholdConfig;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.infra.security.LogFilterCriteria;
import backlogs.dinamico.security.auth.ScopeGuard;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogDashboardService {

    private static final String COLLECTION = "log_events";
    private static final int TOP_N = 20;

    private final AlertThresholdConfig alertThresholdConfig;
    private final MongoTemplate mongoTemplate;
    private final ScopeGuard    scopeGuard;

    // ── STATS ─────────────────────────────────────────────────────────────────

    public DashboardStatsDto stats(Authentication auth,
                                   String system,
                                   Instant from,
                                   Instant to,
                                   boolean allRange) {

        // Métrica operativa: día en curso completo (00:00 UTC) si no llegan fechas.
        // Modo ALL: sin filtro de fecha (historial completo).
        InstantRange range = allRange ? null : resolveOperationalRange(from, to);
        Criteria base = buildBaseCriteria(auth, system,
                range != null ? range.from() : null,
                range != null ? range.to() : null);

        long total = countWithCriteria(base);
        long totalHistoricalLogs = countHistoricalLogs(auth, system);
        String overallHealth = StringUtils.hasText(system)
                ? computeOverallHealth(auth, system)
                : "UNKNOWN";

        if (total == 0) {
            DashboardStatsDto empty = emptyStats();
            empty.setTotalHistoricalLogs(totalHistoricalLogs);
            empty.setOverallHealth(overallHealth);
            empty.setTotalEvents(0);
            empty.setErrorCount(0);
            empty.setSuccessCount(0);
            empty.setErrorRate(0.0);
            empty.setHealthStatus("INACTIVE");
            empty.setActiveCases(0);
            return empty;
        }

        long errorCount = countErrors(base);
        long successCount = countSuccesses(base);
        long activeCases = countActiveCases(base);
        double errorRate = Math.round((errorCount * 10000.0 / total)) / 100.0;
        String healthStatus = determineRangeHealthStatus(total, errorRate);

        return DashboardStatsDto.builder()
                .total(total)
                .totalEvents(total)
                .errorCount(errorCount)
                .successCount(successCount)
                .errorRate(errorRate)
                .healthStatus(healthStatus)
                .activeCases(activeCases)
                .totalHistoricalLogs(totalHistoricalLogs)
                .overallHealth(overallHealth)
                .topEventTypes(aggregate(base, "eventType",  TOP_N, total))
                .outcomes(      aggregate(base, "outcome",   TOP_N, total))
                .severities(    aggregate(base, "severity",  TOP_N, total))
                .statuses(      aggregate(base, "status",    TOP_N, total))
                .topTags(       aggregateTags(base, TOP_N, total))
                .topLocations(  aggregate(base, "location.name", TOP_N, total))
                .topActors(     aggregate(base, "actor.username", TOP_N, total))
                .environments(  aggregate(base, "environment", TOP_N, total))
                .build();
    }

    // ── SERIES ────────────────────────────────────────────────────────────────

    public DashboardSeriesDto series(Authentication auth,
                                     String system,
                                     Instant from,
                                     Instant to) {

        // Métrica analítica: historial completo si no se especifica rango.
        Criteria base = buildBaseCriteria(auth, system, from, to);

        return DashboardSeriesDto.builder()
                .byDay(   timeSeries(base, "day"))
                .byWeek(  timeSeries(base, "week"))
                .byMonth( timeSeries(base, "month"))
                .statusOverTime(statusOverTime(base))
                .build();
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    public DashboardHttpDto http(Authentication auth,
                                 String system,
                                 Instant from,
                                 Instant to) {
        try {
            // Métrica analítica: historial completo si no se especifica rango.
            Criteria base = buildBaseCriteria(auth, system, from, to)
                    .and("http").exists(true);

            // Agrupar por statusCode + method → calcular latencia
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(base),
                    Aggregation.project()
                            .and("http.statusCode").as("sc")
                            .and("http.method").as("method")
                            .and("http.latencyMs").as("lat"),
                    Aggregation.group("sc", "method")
                            .count().as("count")
                            .push("lat").as("latencies"),
                    Aggregation.sort(
                            org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Direction.DESC, "count")),
                    Aggregation.limit(100)
            );

            List<Document> raw = mongoTemplate
                    .aggregate(agg, COLLECTION, Document.class)
                    .getMappedResults();

            List<DashboardHttpDto.HttpBucket> buckets = raw.stream().map(doc -> {
                Document id = doc.get("_id", Document.class);
                String sc = id != null ? String.valueOf(id.getOrDefault("sc", "N/A")) : "N/A";
                String method = id != null ? String.valueOf(id.getOrDefault("method", "N/A")) : "N/A";
                long count = ((Number) doc.getOrDefault("count", 0)).longValue();

                @SuppressWarnings("unchecked")
                List<Object> lats = (List<Object>) doc.get("latencies");
                double p95 = calcP95(lats);
                double avg = calcAvg(lats);

                return DashboardHttpDto.HttpBucket.builder()
                        .statusCode(sc)
                        .method(method)
                        .p95Ms(p95)
                        .avgMs(avg)
                        .count(count)
                        .build();
            }).toList();

            // Method summary
            Map<String, long[]> methodMap = new LinkedHashMap<>();
            Map<String, List<Double>> methodLat = new LinkedHashMap<>();
            for (DashboardHttpDto.HttpBucket b : buckets) {
                methodMap.computeIfAbsent(b.getMethod(), k -> new long[]{0})[0] += b.getCount();
                methodLat.computeIfAbsent(b.getMethod(), k -> new ArrayList<>()).add(b.getAvgMs());
            }

            List<DashboardHttpDto.MethodSummary> methodSummary = methodMap.entrySet().stream()
                    .map(e -> DashboardHttpDto.MethodSummary.builder()
                            .method(e.getKey())
                            .count(e.getValue()[0])
                            .avgMs(methodLat.get(e.getKey()).stream()
                                    .mapToDouble(Double::doubleValue).average().orElse(0))
                            .build())
                    .sorted(Comparator.comparingLong(DashboardHttpDto.MethodSummary::getCount).reversed())
                    .toList();

            return DashboardHttpDto.builder()
                    .latencyByStatusAndMethod(buckets)
                    .methodSummary(methodSummary)
                    .build();
        } catch (Exception e) {
            log.error("[http] Error al calcular métricas HTTP para system={} from={} to={}", system, from, to, e);
            return DashboardHttpDto.builder()
                    .latencyByStatusAndMethod(List.of())
                    .methodSummary(List.of())
                    .build();
        }
    }


    /**
     * Salud de todos los sistemas del tenant.
     * <p>Lógica unificada:</p>
     * <ul>
     *   <li>INACTIVE: sin logs en las últimas 24 h.</li>
     *   <li>CRITICAL: logs en las últimas 24 h y tasa de error &gt; 10%.</li>
     *   <li>WARNING:  logs en las últimas 24 h y tasa de error entre 1% y 10%.</li>
     *   <li>STABLE:   logs en las últimas 24 h y tasa de error &lt; 1%.</li>
     * </ul>
     */
    public List<SystemHealthDto> systemsHealth(Authentication auth) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        ObjectId tenantId = user.getTenantId();

        org.springframework.data.mongodb.core.query.Query q =
                new org.springframework.data.mongodb.core.query.Query(
                        Criteria.where("tenant_id").is(tenantId));
        List<String> allSystems = mongoTemplate.findDistinct(
                q, "system", "log_events", String.class);

        List<SystemHealthDto> result = new ArrayList<>();
        Instant now = Instant.now();

        for (String system : allSystems) {
            if (system == null || system.isBlank()) continue;

            if (!hasSystemAccess(user, system)) continue;

            HealthResult hr = computeSystemHealth(tenantId, system, now);
            result.add(new SystemHealthDto(
                    system, hr.status(), hr.errorRate(), hr.total(), hr.errors()));
        }

        result.sort(Comparator.comparingInt(s -> switch (s.status()) {
            case "CRITICAL" -> 0;
            case "WARNING"  -> 1;
            case "STABLE"   -> 2;
            default         -> 3;
        }));

        return result;
    }

    /**
     * Calcula la salud de un sistema según lastSeen y tasa de error en 24 h.
     */
    public String computeOverallHealth(Authentication auth, String system) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        ObjectId tenantId = user.getTenantId();
        if (!hasSystemAccess(user, system)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "system_not_allowed");
        }
        return computeSystemHealth(tenantId, system, Instant.now()).status();
    }

    private HealthResult computeSystemHealth(ObjectId tenantId, String system, Instant now) {
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);

        // Último log recibido (lastSeen)
        org.springframework.data.mongodb.core.query.Query lastQuery =
                new org.springframework.data.mongodb.core.query.Query(
                        Criteria.where("tenant_id").is(tenantId)
                                .and("system").regex("^" + Pattern.quote(system) + "$", "i"))
                        .with(Sort.by(Sort.Direction.DESC, "eventTime"))
                        .limit(1);
        Document lastDoc = mongoTemplate.findOne(lastQuery, Document.class, COLLECTION);
        Instant lastSeen = lastDoc != null && lastDoc.getDate("eventTime") != null
                ? lastDoc.getDate("eventTime").toInstant()
                : null;

        if (lastSeen == null || lastSeen.isBefore(cutoff)) {
            return new HealthResult("INACTIVE", 0.0, 0, 0);
        }

        // Conteo total y errores en las últimas 24 h
        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").regex("^" + Pattern.quote(system) + "$", "i")
                .and("eventTime").gte(cutoff).lte(now);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group()
                        .count().as("total")
                        .sum(new AggregationExpression() {
                            @Override
                            public Document toDocument(AggregationOperationContext ctx) {
                                return new Document("$cond", Arrays.asList(
                                        new Document("$or", List.of(
                                                new Document("$eq", Arrays.asList("$outcome", "FAILURE")),
                                                new Document("$eq", Arrays.asList("$isError", true)),
                                                new Document("$in", Arrays.asList("$status", List.of("ERROR", "FAILED", "REJECTED")))
                                        )),
                                        1, 0));
                            }
                        }).as("errors")
        );

        AggregationResults<Document> aggResult = mongoTemplate.aggregate(agg, COLLECTION, Document.class);
        Document stats = aggResult.getUniqueMappedResult();
        long total = stats != null ? ((Number) stats.getOrDefault("total", 0)).longValue() : 0L;
        long errors = stats != null ? ((Number) stats.getOrDefault("errors", 0)).longValue() : 0L;

        if (total == 0) {
            return new HealthResult("STABLE", 0.0, 0, 0);
        }

        double errorRate = (double) errors / total;
        String status;
        if (errorRate > 0.10)      status = "CRITICAL";
        else if (errorRate >= 0.01) status = "WARNING";
        else                        status = "STABLE";

        return new HealthResult(status, errorRate, total, errors);
    }

    private boolean hasSystemAccess(AuthUser user, String system) {
        boolean isAdminOrOwner = user.getRoles() != null &&
                (user.getRoles().contains("ORG_ADMIN") || user.getRoles().contains("ORG_OWNER"));
        boolean isUnrestricted = isAdminOrOwner ||
                (user.isOrgWide() && (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()));
        if (isUnrestricted) return true;

        List<String> allowed = user.getAllowedSystems() == null
                ? List.of()
                : user.getAllowedSystems();
        return allowed.stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .anyMatch(s -> s.equalsIgnoreCase(system));
    }

    private record HealthResult(String status, double errorRate, long total, long errors) {
    }


    // ── GEO ───────────────────────────────────────────────────────────────────

    public DashboardGeoDto geo(Authentication auth,
                               String system,
                               Instant from,
                               Instant to) {

        Criteria base = buildBaseCriteria(auth, system, from, to)
                .and("geo.coordinates").exists(true);

        // ══════════════════════════════════════════════════════════════════════════════
        // FILTRO DE INTEGRIDAD ESTRICTO REFORZADO:
        // Excluir logs cuyo caseId sea null, vacío, "-", o que no cumpla con:
        //   • Longitud mínima de 5 caracteres (evita tokens corruptos como "1", "ab", etc.)
        //   • Debe empezar con prefijo de sistema válido (letras mayúsculas + guion/underscore)
        // 
        // Ejemplos VÁLIDOS:   TV-12345, TKT-001, ACC_999, HID-001
        // Ejemplos INVÁLIDOS: null, "", "-", "123", "ab", "-001", "tv-001" (minúsculas)
        // ══════════════════════════════════════════════════════════════════════════════
        base = base.and("caseId").exists(true)
                .ne(null)
                .ne("")
                .ne("-")
                .regex("^[A-Z]+[-_][A-Za-z0-9]")  // Prefijo válido
                .regex("^.{5,}$");  // Longitud mínima de 5 caracteres

        // ── AGRUPACIÓN CORRECTA: POR caseId (DISPOSITIVO FÍSICO ÚNICO) ──────────
        // Cada dispositivo tiene un caseId único. La última posición GPS corresponde
        // al último log de ese dispositivo. Esto evita duplicados por nombre de usuario.
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(base),
                
                // Ordenar por tiempo DESC para tomar el más reciente de cada dispositivo
                Aggregation.sort(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "eventTime")),
                
                // Proyectar campos necesarios
                Aggregation.project()
                        .and("caseId").as("caseId")
                        .and("geo.coordinates").as("coords")
                        .and("actor.fullName").as("usuario")
                        .and("eventTime").as("ultimaConexion")
                        .and("meta.ip").as("ip"),
                
                // AGRUPAR POR caseId (cada dispositivo = un punto único en el mapa)
                Aggregation.group("caseId")
                        .first("usuario").as("usuario")
                        .first("coords").as("coordenadas")
                        .max("ultimaConexion").as("ultimaConexion")
                        .first("ip").as("ip"),
                
                Aggregation.sort(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "ultimaConexion")),
                
                Aggregation.limit(2000)  // límite para no saturar el mapa
        );

        List<Document> raw = mongoTemplate
                .aggregate(agg, COLLECTION, Document.class)
                .getMappedResults();

        List<DashboardGeoDto.GeoPoint> points = raw.stream().map(doc -> {
            @SuppressWarnings("unchecked")
            List<Double> coords = (List<Double>) doc.get("coordenadas");
            if (coords == null || coords.size() < 2) return null;
            
            String caseId = doc.getString("_id");
            String usuario = doc.getString("usuario");
            String ip = doc.getString("ip");
            
            return DashboardGeoDto.GeoPoint.builder()
                    .lon(coords.get(0))
                    .lat(coords.get(1))
                    .count(1)  // Cada dispositivo cuenta como 1 punto
                    .caseId(caseId)
                    .usuario(usuario)
                    .ip(ip)
                    .build();
        }).filter(Objects::nonNull).toList();

        long total = points.size();  // Total = número de dispositivos únicos

        return DashboardGeoDto.builder()
                .total(total)
                .points(points)
                .build();
    }

    // ── Helpers internos ──────────────────────────────────────────────────────
    private Criteria buildBaseCriteria(Authentication auth, String system,
                                       Instant from, Instant to) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        if (user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");

        Criteria c = Criteria.where("tenant_id").is(tenantId);

        // System scope — case-insensitive para alinear requests con el casing almacenado
        if (StringUtils.hasText(system)) {
            String sys = system.trim().toUpperCase(Locale.ROOT);
            scopeGuard.requireSystemAccess(user, sys);
            c = c.and("system").regex("^" + Pattern.quote(sys) + "$", "i");
        } else if (!user.isOrgWide()) {
            List<String> allowed = user.getAllowedSystems() == null ? List.of()
                    : user.getAllowedSystems().stream()
                    .filter(StringUtils::hasText)
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .distinct().toList();
            if (allowed.isEmpty())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no_systems_allowed");
            c = c.and("system").in(allowed);
        } else if (user.getAllowedSystems() != null && !user.getAllowedSystems().isEmpty()) {
            c = c.and("system").in(user.getAllowedSystems());
        }

        // Rango de fechas: solo se aplica si el llamador lo solicita explícitamente.
        // Las métricas operativas aplican su propio default (24h); las analíticas
        // consultan todo el historial cuando from/to son null.
        if (from != null || to != null) {
            Instant effectiveFrom = from != null ? from : Instant.EPOCH;
            Instant effectiveTo   = to != null   ? to   : Instant.now();
            c = c.and("eventTime").gte(effectiveFrom).lt(effectiveTo);
        }

        // Aplicar logFilters del VIEWER
        return LogFilterCriteria.apply(c);
    }

    private long countWithCriteria(Criteria c) {
        return mongoTemplate.count(
                new org.springframework.data.mongodb.core.query.Query(c), COLLECTION);
    }

    private long countHistoricalLogs(Authentication auth, String system) {
        // Conteo histórico total del sistema, sin filtro de fecha.
        Criteria base = buildBaseCriteria(auth, system, null, null);
        return countWithCriteria(base);
    }

    /** Agrupa por un campo, devuelve top N con porcentaje */
    private List<DashboardStatsDto.DistItem> aggregate(Criteria base, String field,
                                                       int limit, long total) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(base),
                Aggregation.group(field).count().as("count"),
                Aggregation.sort(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "count")),
                Aggregation.limit(limit),
                Aggregation.project("count").and("_id").as("value")
        );

        return mongoTemplate.aggregate(agg, COLLECTION, Document.class)
                .getMappedResults().stream()
                .map(doc -> {
                    String val = doc.getString("value");
                    if (val == null) val = "N/A";
                    long count = ((Number) doc.getOrDefault("count", 0)).longValue();
                    return DashboardStatsDto.DistItem.builder()
                            .value(val)
                            .count(count)
                            .pct(total > 0 ? Math.round((count * 1000.0 / total)) / 10.0 : 0)
                            .build();
                }).toList();
    }

    /** Desanida el array de tags y agrupa */
    private List<DashboardStatsDto.DistItem> aggregateTags(Criteria base, int limit, long total) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(base),
                Aggregation.unwind("tags"),
                Aggregation.group("tags").count().as("count"),
                Aggregation.sort(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "count")),
                Aggregation.limit(limit),
                Aggregation.project("count").and("_id").as("value")
        );

        return mongoTemplate.aggregate(agg, COLLECTION, Document.class)
                .getMappedResults().stream()
                .map(doc -> {
                    String val = doc.getString("value");
                    if (val == null) val = "N/A";
                    long count = ((Number) doc.getOrDefault("count", 0)).longValue();
                    return DashboardStatsDto.DistItem.builder()
                            .value(val)
                            .count(count)
                            .pct(total > 0 ? Math.round((count * 1000.0 / total)) / 10.0 : 0)
                            .build();
                }).toList();
    }

    /** Serie de tiempo por día, semana o mes */
    private List<DashboardSeriesDto.TimePoint> timeSeries(Criteria base, String granularity) {
        Document dateExpr = switch (granularity) {
            case "week"  -> new Document("$dateToString",
                    new Document("format", "Semana %V-%G").append("date", "$eventTime").append("timezone", "America/Mexico_City"));
            case "month" -> new Document("$dateToString",
                    new Document("format", "%m-%Y").append("date", "$eventTime").append("timezone", "America/Mexico_City"));
            default      -> new Document("$dateToString",
                    new Document("format", "%Y-%m-%d").append("date", "$eventTime").append("timezone", "America/Mexico_City"));
        };

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(base),
                new AggregationOperation() {
                    @Override public Document toDocument(AggregationOperationContext ctx) {
                        return new Document("$group", new Document("_id", dateExpr)
                                .append("count", new Document("$sum", 1)));
                    }
                },
                Aggregation.sort(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "_id"))
        );

        return mongoTemplate.aggregate(agg, COLLECTION, Document.class)
                .getMappedResults().stream()
                .map(doc -> DashboardSeriesDto.TimePoint.builder()
                        .date(String.valueOf(doc.getOrDefault("_id", "?")))
                        .count(((Number) doc.getOrDefault("count", 0)).longValue())
                        .build())
                .toList();
    }

    /** Status × día para la gráfica de líneas multicolor */
    private List<DashboardSeriesDto.StatusTimePoint> statusOverTime(Criteria base) {
        Document dateExpr = new Document("$dateToString",
                new Document("format", "%Y-%m-%d").append("date", "$eventTime")
                        .append("timezone", "America/Mexico_City"));

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(base),
                new AggregationOperation() {
                    @Override public Document toDocument(AggregationOperationContext ctx) {
                        return new Document("$group",
                                new Document("_id", new Document("date", dateExpr)
                                        .append("status", "$status"))
                                        .append("count", new Document("$sum", 1)));
                    }
                },
                Aggregation.sort(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "_id.date"))
        );

        return mongoTemplate.aggregate(agg, COLLECTION, Document.class)
                .getMappedResults().stream()
                .map(doc -> {
                    Document id = doc.get("_id", Document.class);
                    return DashboardSeriesDto.StatusTimePoint.builder()
                            .date(id != null   ? String.valueOf(id.getOrDefault("date", "?"))   : "?")
                            .status(id != null ? String.valueOf(id.getOrDefault("status", "N/A")) : "N/A")
                            .count(((Number) doc.getOrDefault("count", 0)).longValue())
                            .build();
                }).toList();
    }

    private double calcP95(List<Object> lats) {
        if (lats == null || lats.isEmpty()) return 0;
        List<Double> sorted = lats.stream()
                .filter(Objects::nonNull)
                .map(v -> v instanceof Number n ? n.doubleValue() : 0.0)
                .sorted().toList();
        int idx = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private double calcAvg(List<Object> lats) {
        if (lats == null || lats.isEmpty()) return 0;
        return lats.stream()
                .filter(Objects::nonNull)
                .mapToDouble(v -> v instanceof Number n ? n.doubleValue() : 0.0)
                .average().orElse(0);
    }

    // ── Range metrics helpers ─────────────────────────────────────────────────

    private long countErrors(Criteria base) {
        Criteria errorCriteria = new Criteria().andOperator(base, buildErrorCriteria());
        return countWithCriteria(errorCriteria);
    }

    private long countSuccesses(Criteria base) {
        Criteria successCriteria = new Criteria().andOperator(base, buildSuccessCriteria());
        return countWithCriteria(successCriteria);
    }

    private long countActiveCases(Criteria base) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(base),
                Aggregation.match(Criteria.where("caseId").exists(true).ne(null).ne("")),
                Aggregation.group().addToSet("caseId").as("uniqueCases")
        );
        AggregationResults<Document> result = mongoTemplate.aggregate(agg, COLLECTION, Document.class);
        Document doc = result.getUniqueMappedResult();
        if (doc == null) return 0L;
        List<?> cases = (List<?>) doc.get("uniqueCases");
        return cases != null ? cases.size() : 0L;
    }

    private Criteria buildErrorCriteria() {
        return new Criteria().orOperator(
                Criteria.where("isError").is(true),
                Criteria.where("status").in("ERROR", "FAIL", "FAILURE", "KO", "REJECTED"),
                Criteria.where("outcome").in("FAILURE", "ERROR", "FAILED"),
                Criteria.where("severity").in("ERROR", "CRITICAL", "FATAL")
        );
    }

    private Criteria buildSuccessCriteria() {
        return new Criteria().orOperator(
                Criteria.where("status").in("SUCCESS", "OK", "COMPLETED"),
                Criteria.where("outcome").in("SUCCESS", "OK")
        );
    }

    private String determineRangeHealthStatus(long totalEvents, double errorRate) {
        if (totalEvents == 0) return "INACTIVE";
        if (errorRate > 10.0) return "CRITICAL";
        if (errorRate > 0.0) return "WARNING";
        return "STABLE";
    }

    private DashboardStatsDto emptyStats() {
        return DashboardStatsDto.builder()
                .total(0).topEventTypes(List.of()).outcomes(List.of())
                .severities(List.of()).statuses(List.of()).topTags(List.of())
                .topLocations(List.of()).topActors(List.of()).environments(List.of())
                .build();
    }

    /**
     * Resuelve el rango operacional de "Hoy".
     * - Si llegan from/to explícitos, se usan tal cual (truncados a segundos).
     * - Si no llegan, se usa el día en curso completo desde 00:00:00 UTC
     *   hasta ahora + 6 h (zona horaria de los logs).
     */
    private InstantRange resolveOperationalRange(Instant from, Instant to) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        if (from != null || to != null) {
            Instant effectiveFrom = from != null ? from.truncatedTo(ChronoUnit.SECONDS) : Instant.EPOCH;
            Instant effectiveTo = to != null ? to.truncatedTo(ChronoUnit.SECONDS) : now.plus(6, ChronoUnit.HOURS);
            return new InstantRange(effectiveFrom, effectiveTo);
        }
        // Día completo en UTC
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        return new InstantRange(startOfDay, now.plus(6, ChronoUnit.HOURS));
    }

    private record InstantRange(Instant from, Instant to) {
    }
}