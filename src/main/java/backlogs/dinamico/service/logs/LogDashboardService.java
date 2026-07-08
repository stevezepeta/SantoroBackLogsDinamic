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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.Arrays;
import java.util.*;

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
                                   Instant to) {

        Criteria base = buildBaseCriteria(auth, system, from, to);

        long total = countWithCriteria(base);
        if (total == 0) return emptyStats();

        return DashboardStatsDto.builder()
                .total(total)
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
                                org.springframework.data.domain.Sort.Direction.DESC, "count"))
        );

        List<Document> raw = mongoTemplate
                .aggregate(agg, COLLECTION, Document.class)
                .getMappedResults();

        List<DashboardHttpDto.HttpBucket> buckets = raw.stream().map(doc -> {
            Document id = doc.get("_id", Document.class);
            String sc     = id != null ? String.valueOf(id.getOrDefault("sc", "N/A")) : "N/A";
            String method = id != null ? String.valueOf(id.getOrDefault("method", "N/A")) : "N/A";
            long count    = ((Number) doc.getOrDefault("count", 0)).longValue();

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
    }

    /**
     * Salud de todos los sistemas del tenant en las últimas 24 horas.
     * INACTIVE = sin logs en las últimas 24h
     * HEALTHY  = errorRate < umbral WARN
     * WARN     = errorRate >= umbral WARN
     * CRIT     = errorRate >= umbral CRIT
     */
    public List<SystemHealthDto> systemsHealth(Authentication auth) {
        AuthUser user     = (AuthUser) auth.getPrincipal();
        ObjectId tenantId = user.getTenantId();

        // ── DEBUG TEMPORAL ────────────────────────────────────────────────────
        log.info("[Health] tenantId: {}", tenantId);
        log.info("[Health] isOrgWide: {}", user.isOrgWide());
        log.info("[Health] roles: {}", user.getRoles());
        // ─────────────────────────────────────────────────────────────────────

        Instant from = Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS);
        Instant to   = Instant.now();

        // Obtener todos los sistemas del tenant
        org.springframework.data.mongodb.core.query.Query q =
                new org.springframework.data.mongodb.core.query.Query(
                        Criteria.where("tenant_id").is(tenantId));
        List<String> allSystems = mongoTemplate.findDistinct(
                q, "system", "log_events", String.class);

        // ── DEBUG TEMPORAL ────────────────────────────────────────────────────
        log.info("[Health] sistemas encontrados: {}", allSystems);
        // ─────────────────────────────────────────────────────────────────────

        List<SystemHealthDto> result = new ArrayList<>();

        for (String system : allSystems) {
            if (system == null || system.isBlank()) continue;

            // Saltar sistemas a los que el usuario no tiene acceso
            boolean isAdminOrOwner = user.getRoles() != null &&
                    (user.getRoles().contains("ORG_ADMIN") ||
                            user.getRoles().contains("ORG_OWNER"));
            boolean isUnrestricted = isAdminOrOwner ||
                    (user.isOrgWide() && (user.getAllowedSystems() == null || user.getAllowedSystems().isEmpty()));

            if (!isUnrestricted) {
                List<String> allowed = user.getAllowedSystems() != null
                        ? user.getAllowedSystems() : List.of();
                if (!allowed.contains(system)) continue;
            }

            // Contar eventos de las últimas 24h para este sistema
            MatchOperation matchOp = Aggregation.match(new Criteria().andOperator(
                    Criteria.where("tenant_id").is(tenantId),
                    Criteria.where("system").is(system),
                    Criteria.where("eventTime").gte(Date.from(from)).lt(Date.from(to))
            ));

            GroupOperation groupOp = Aggregation.group()
                    .count().as("total")
                    .sum(new AggregationExpression() {
                        @Override
                        public Document toDocument(AggregationOperationContext ctx) {
                            return new Document("$cond", Arrays.asList(
                                    new Document("$eq", Arrays.asList("$outcome", "FAILURE")),
                                    1, 0));
                        }
                    }).as("failures");

            AggregationResults<Document> aggResult = mongoTemplate.aggregate(
                    Aggregation.newAggregation(matchOp, groupOp),
                    "log_events", Document.class);

            Document stats = aggResult.getUniqueMappedResult();
            long total    = stats != null ? ((Number) stats.getOrDefault("total",    0)).longValue() : 0L;
            long failures = stats != null ? ((Number) stats.getOrDefault("failures", 0)).longValue() : 0L;

            log.info("[Health] sistema={} total={} failures={}", system, total, failures);

            if (total == 0) {
                result.add(new SystemHealthDto(system, "INACTIVE", 0, 0, 0));
                continue;
            }

            double errorRate = (double) failures / total;

            // Usar los umbrales configurados para este sistema
            backlogs.dinamico.config.AlertThresholdConfig.Thresholds t =
                    alertThresholdConfig.getThresholds(system);

            String status;
            if (errorRate >= t.crit())      status = "CRIT";
            else if (errorRate >= t.warn()) status = "WARN";
            else                            status = "HEALTHY";

            result.add(new SystemHealthDto(system, status, errorRate, total, failures));
        }

        // Ordenar: CRIT primero, luego WARN, luego HEALTHY, luego INACTIVE
        result.sort(Comparator.comparingInt(s -> switch (s.status()) {
            case "CRIT"     -> 0;
            case "WARN"     -> 1;
            case "HEALTHY"  -> 2;
            default         -> 3;
        }));

        return result;
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

        // System scope
        if (StringUtils.hasText(system)) {
            String sys = system.trim().toUpperCase(Locale.ROOT);
            scopeGuard.requireSystemAccess(user, sys);
            c = c.and("system").is(sys);
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

        // Rango de fechas — default: últimos 30 días si no viene
        Instant effectiveFrom = from != null ? from : Instant.now().minusSeconds(30L * 24 * 3600);
        Instant effectiveTo   = to != null   ? to   : Instant.now();

        c = c.and("eventTime").gte(effectiveFrom).lt(effectiveTo);

        // Aplicar logFilters del VIEWER
        return LogFilterCriteria.apply(c);
    }

    private long countWithCriteria(Criteria c) {
        return mongoTemplate.count(
                new org.springframework.data.mongodb.core.query.Query(c), COLLECTION);
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

    private DashboardStatsDto emptyStats() {
        return DashboardStatsDto.builder()
                .total(0).topEventTypes(List.of()).outcomes(List.of())
                .severities(List.of()).statuses(List.of()).topTags(List.of())
                .topLocations(List.of()).topActors(List.of()).environments(List.of())
                .build();
    }
}