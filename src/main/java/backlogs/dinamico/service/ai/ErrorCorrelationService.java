package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.ErrorCorrelationDto;
import backlogs.dinamico.service.ai.dto.ErrorCorrelationReq;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class ErrorCorrelationService {

    private final MongoTemplate mongoTemplate;

    @Value("${multitenant.log-collection:log_events}")
    private String logCollection;

    private static final String DEFAULT_TZ = "America/Mexico_City";
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    // clustering
    private static final double CLUSTER_SIM_THRESHOLD = 0.62;

    // ventana default si no mandan from/to
    private static final int DEFAULT_HOURS = 24;
    private static final int MAX_HOURS = 24 * 31;

    // Campos (mongo)
    private static final String F_TENANT = "tenant_id";
    private static final String F_TIME   = "eventTime";
    private static final String F_SYS    = "system";
    private static final String F_TYPE   = "eventType";
    private static final String F_STATUS = "status";
    private static final String F_OUT    = "outcome";
    private static final String F_SEV    = "severity";
    private static final String F_MSG    = "message";
    private static final String F_MSG_KEY = "messageKey";
    private static final String F_IS_ERROR = "isError";

    private static final String F_REQ_ID = "correlation.requestId";
    private static final String F_TRACE  = "correlation.traceId";
    private static final String F_CASE   = "caseId";
    private static final String F_ACT_USR = "actor.username";
    private static final String F_ACT_NAME = "actor.fullName";
    private static final String F_LOC_NAME = "location.name";

    // normalización tokens
    private static final Pattern UUID = Pattern.compile("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX24 = Pattern.compile("\\b[0-9a-f]{24}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUM = Pattern.compile("\\b\\d+\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w._%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}\\b");
    private static final Pattern PATH_N = Pattern.compile("\\{n\\}|/\\d+\\b");

    public ErrorCorrelationDto correlateWithEvidence(ObjectId tenantId, ErrorCorrelationReq req) {
        ZoneId zone = safeZone(req == null ? null : req.tz);

        // --------- rango ----------
        Instant now = Instant.now();
        Instant to = (req != null && req.to != null) ? req.to : now;
        Instant from = (req != null && req.from != null) ? req.from : to.minus(DEFAULT_HOURS, ChronoUnit.HOURS);

        if (from.isAfter(to)) {
            Instant tmp = from; from = to; to = tmp;
        }

        long hrs = ChronoUnit.HOURS.between(from, to);
        if (hrs > MAX_HOURS) {
            from = to.minus(MAX_HOURS, ChronoUnit.HOURS);
        }

        // --------- input ----------
        List<String> in = (req == null || req.errors == null) ? List.of() : req.errors;
        List<String> cleaned = in.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();

        int samplesPerCluster = (req != null && req.samplesPerCluster != null)
                ? Math.min(Math.max(req.samplesPerCluster, 1), 30)
                : 10;

        String system = (req != null && StringUtils.hasText(req.system)) ? req.system.trim() : null;

        ErrorCorrelationDto out = new ErrorCorrelationDto();
        out.tz = zone.getId();
        out.from = from.toString();
        out.to = to.toString();
        out.inputCount = cleaned.size();

        if (cleaned.isEmpty()) {
            out.clusters = List.of();
            out.hypotheses = List.of(newHyp("UNKNOWN","LOW","No se recibieron errores para correlacionar.", List.of("errors[] vacío")));
            out.nextSteps = List.of("Envía 5 strings (ideal: messageKey).");
            out.suggestedFilters = safeMap(
                    "from", out.from,
                    "to", out.to,
                    "system", system
            );
            return out;
        }

        // 1) clustering
        List<ClusterTmp> clusters = cluster(cleaned);

        // 2) enriquecer cada cluster con Mongo
        List<ErrorCorrelationDto.Cluster> dtoClusters = new ArrayList<>();
        for (ClusterTmp c : clusters) {
            dtoClusters.add(enrichCluster(tenantId, c, from, to, zone, system, samplesPerCluster));
        }

        // 3) hipótesis globales (mejoradas con evidencia de clusters)
        List<ErrorCorrelationDto.Hypothesis> hyps = buildHypotheses(cleaned, dtoClusters);

        // 4) next steps + filtros sugeridos globales
        out.clusters = dtoClusters;
        out.hypotheses = hyps;

        out.nextSteps = List.of(
                "1) Tomar el cluster más grande (C1) y validar si coincide con deploy/config reciente.",
                "2) Abrir 5-10 samples del cluster y correlacionar por requestId/traceId/caseId.",
                "3) Si hay DB/AUTH/RateLimit: asignar al owner correcto (API/DB/Auth).",
                "4) Crear ticket por cluster (título = representative) y adjuntar evidencia (topRequestIds/topErrors/samples)."
        );

        out.suggestedFilters = safeMap(
                "from", out.from,
                "to", out.to,
                "system", system,
                "clusterHint", snippet(cleaned.get(0), 120)
        );

        return out;
    }

    // -------------------- cluster enrichment --------------------
    private ErrorCorrelationDto.Cluster enrichCluster(
            ObjectId tenantId,
            ClusterTmp c,
            Instant from,
            Instant to,
            ZoneId zone,
            String system,
            int samplesLimit
    ) {
        ErrorCorrelationDto.Cluster d = new ErrorCorrelationDto.Cluster();
        d.clusterId = c.id;
        d.representative = snippet(c.representative, 180);
        d.members = c.members;
        d.similarityAvg = c.sims.isEmpty()
                ? 1.0
                : c.sims.stream().mapToDouble(x -> x).average().orElse(1.0);

        // preferimos messageKey exacto
        List<String> keys = (c.members == null ? List.<String>of() : c.members).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(20)
                .toList();

        Criteria base = Criteria.where(F_TENANT).is(tenantId)
                .and(F_TIME).gte(Date.from(from)).lt(Date.from(to));

        if (StringUtils.hasText(system)) {
            base = new Criteria().andOperator(base, Criteria.where(F_SYS).is(system));
        }

        // regla "error" robusta (por si no existe isError en logs viejos)
        Criteria isError = new Criteria().orOperator(
                Criteria.where(F_IS_ERROR).is(true),
                Criteria.where(F_SEV).in("ERROR", "FATAL"),
                Criteria.where(F_OUT).is("FAILURE"),
                Criteria.where(F_STATUS).in("REJECTED", "ERROR"),
                Criteria.where(F_OUT).regex("ERR", "i")
        );

        // Armamos el Criteria final
        Criteria finalCriteria = null;
        long matched = 0L;

        if (!keys.isEmpty()) {
            Criteria matchByKey = new Criteria().andOperator(
                    Criteria.where(F_MSG_KEY).in(keys),
                    Criteria.where(F_MSG_KEY).exists(true),
                    Criteria.where(F_MSG_KEY).ne(null),
                    Criteria.where(F_MSG_KEY).ne("")
            );

            finalCriteria = new Criteria().andOperator(base, isError, matchByKey);
            matched = mongoTemplate.count(new Query(finalCriteria), logCollection);
        }

        // si no matcheó por messageKey (por ejemplo logs viejos), fallback por message contains
        if (matched == 0) {
            String rep = snippet(c.representative, 80);
            if (StringUtils.hasText(rep)) {
                Pattern p = Pattern.compile(Pattern.quote(rep), Pattern.CASE_INSENSITIVE);

                Criteria fallback = new Criteria().andOperator(
                        base,
                        isError,
                        new Criteria().orOperator(
                                Criteria.where(F_MSG).regex(p),
                                Criteria.where(F_MSG_KEY).regex(p)
                        )
                );
                finalCriteria = fallback;
                matched = mongoTemplate.count(new Query(finalCriteria), logCollection);
            } else {
                // ultimo recurso
                finalCriteria = new Criteria().andOperator(base, isError);
                matched = mongoTemplate.count(new Query(finalCriteria), logCollection);
            }
        }

        d.matched = matched;

        // tops (por cluster)
        d.topRequestIds = aggregateTopCounts(finalCriteria, 5,
                "correlation.traceId", "requestId", "meta.requestId", "payload.requestId");

        d.topTraceIds = aggregateTopCounts(finalCriteria, 5,
                "correlation.traceId", "traceId", "meta.traceId", "payload.traceId");

        d.topCaseIds = aggregateTopCounts(finalCriteria, 5,
                "caseId", "payload.caseId", "meta.caseId");

        d.topActors = aggregateTopCounts(finalCriteria, 5,
                "actor.username", "actor.id", "payload.actor");

        // samples
        d.samples = fetchSamples(finalCriteria, zone, samplesLimit);

        // filtros sugeridos listos
        String topReq = firstKey(d.topRequestIds);
        String topCase = firstKey(d.topCaseIds);
        String topActor = firstKey(d.topActors);

        d.suggestedFilters = safeMap(
                "system", system,
                "from", from.toString(),
                "to", to.toString(),
                "messageKey", keys.isEmpty() ? null : keys.get(0),
                "requestId", topReq,
                "caseId", topCase,
                "actorUsername", topActor,
                "isError", true
        );

        return d;
    }

    private List<ErrorCorrelationDto.Sample> fetchSamples(Criteria criteria, ZoneId zone, int limit) {
        Query q = new Query(criteria);
        q.with(Sort.by(Sort.Direction.DESC, F_TIME));
        q.limit(Math.min(Math.max(limit, 1), 30));

        q.fields()
                .include("_id")
                .include(F_TIME)
                .include(F_SYS)
                .include(F_TYPE)
                .include(F_STATUS)
                .include(F_OUT)
                .include(F_SEV)
                .include(F_MSG)
                .include(F_MSG_KEY)
                .include(F_REQ_ID)
                .include(F_TRACE)
                .include(F_CASE)
                .include(F_ACT_USR)
                .include(F_ACT_NAME)
                .include(F_LOC_NAME);

        List<Document> docs = mongoTemplate.find(q, Document.class, logCollection);
        List<ErrorCorrelationDto.Sample> out = new ArrayList<>();

        for (Document d : docs) {
            ErrorCorrelationDto.Sample s = new ErrorCorrelationDto.Sample();
            Object id = d.get("_id");
            s.id = (id != null) ? id.toString() : null;

            Date dt = d.getDate(F_TIME);
            if (dt != null) {
                Instant it = dt.toInstant();
                s.eventTime = it.toString();
                s.eventTimeLocal = it.atZone(zone).format(ISO_OFFSET);
            }

            s.system = d.getString(F_SYS);
            s.eventType = d.getString(F_TYPE);
            s.status = d.getString(F_STATUS);
            s.outcome = d.getString(F_OUT);
            s.severity = d.getString(F_SEV);

            s.message = d.getString(F_MSG);
            s.messageKey = d.getString(F_MSG_KEY);

            s.requestId = getPath(d, F_REQ_ID);
            s.traceId = getPath(d, F_TRACE);
            s.caseId = d.getString(F_CASE);

            s.actorUsername = getPath(d, F_ACT_USR);
            s.actorFullName = getPath(d, F_ACT_NAME);
            s.locationName = getPath(d, F_LOC_NAME);

            out.add(s);
        }

        return out;
    }

    private Map<String, Long> aggregateTopCounts(Criteria baseCriteria, int limit, String... fieldPaths) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        // coalesce: toma el primer valor no-null
        Document expr = null;
        for (int i = fieldPaths.length - 1; i >= 0; i--) {
            String p = fieldPaths[1];
            if (!StringUtils.hasText(p)) continue;

            Object fallback = (expr == null) ? "" : expr;
            expr = new Document("$ifNull", List.of("$" + p, fallback));
        }
        if (expr == null) expr = new Document("$literal", "");

        Aggregation agg = newAggregation(
                match(baseCriteria),

                addFields().addFieldWithValue("k", expr).build(),
                match(Criteria.where("k").ne(null).ne("")),

                group("k").count().as("c"),
                sort(Sort.by(Sort.Direction.DESC, "c")),
                limit(safeLimit),

                project()
                        .and("_id.k").as("k")
                        .and("c").as("c")
                        .andExclude("_id")
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();

        Map<String, Long> out = new LinkedHashMap<>();
        for (Document r : rows) {
            out.put(Objects.toString(r.get("k"), ""), toLong(r.get("c")));
        }
        return out;
    }

    // ---------------- clustering (mismo enfoque) ----------------

    private List<ClusterTmp> cluster(List<String> errors) {
        List<Item> items = new ArrayList<>();
        for (String e : errors) items.add(new Item(e, tokens(norm(e))));

        List<ClusterTmp> clusters = new ArrayList<>();

        for (Item it : items) {
            ClusterTmp best = null;
            double bestSim = 0;

            for (ClusterTmp c : clusters) {
                double sim = jaccard(it.tokens, c.representativeTokens);
                if (sim > bestSim) { bestSim = sim; best = c; }
            }

            if (best == null || bestSim < CLUSTER_SIM_THRESHOLD) {
                ClusterTmp c = new ClusterTmp();
                c.members = new ArrayList<>();
                c.members.add(it.raw);
                c.representative = it.raw;
                c.representativeTokens = it.tokens;
                c.sims = new ArrayList<>();
                clusters.add(c);
            } else {
                best.members.add(it.raw);
                best.sims.add(bestSim);
            }
        }

        clusters.sort((a, b) -> Integer.compare(b.members.size(), a.members.size()));
        int i = 1;
        for (ClusterTmp c : clusters) c.id = "C" + (i++);

        return clusters;
    }

    // ---------------- hypotheses ----------------

    private List<ErrorCorrelationDto.Hypothesis> buildHypotheses(List<String> errors, List<ErrorCorrelationDto.Cluster> clusters) {
        String joined = String.join("\n", errors).toLowerCase(Locale.ROOT);
        List<ErrorCorrelationDto.Hypothesis> out = new ArrayList<>();

        if (joined.contains("jdbc") || joined.contains("sql") || joined.contains("query did not return") || joined.contains("constraint") || joined.contains("duplicate key") || joined.contains("unknown column")) {
            out.add(newHyp("DB", "HIGH",
                    "Probable error de base de datos (consulta no única / columna faltante / constraint).",
                    evidenceFromClusters("DB patterns detectados", clusters)));
        }

        if (joined.contains("unauthorized") || joined.contains("forbidden") || joined.contains("invalid token") || joined.contains("credenciales")) {
            out.add(newHyp("AUTH", "MED",
                    "Probable problema de autenticación/autorización (credenciales/token/permisos).",
                    evidenceFromClusters("AUTH patterns detectados", clusters)));
        }

        if (joined.contains("too_many_requests") || joined.contains("429") || joined.contains("rate limit") || joined.contains("wait_before")) {
            out.add(newHyp("RATE_LIMIT", "MED",
                    "Probable limitación por rate-limit o reintentos agresivos.",
                    evidenceFromClusters("RATE_LIMIT patterns detectados", clusters)));
        }

        if (joined.contains("not_found") || joined.contains("no existe") || joined.contains("no encontrado")) {
            out.add(newHyp("NOT_FOUND", "MED",
                    "Probable inconsistencia de datos / recurso inexistente / ids inválidos en el flujo.",
                    evidenceFromClusters("NOT_FOUND patterns detectados", clusters)));
        }

        if (out.isEmpty()) {
            out.add(newHyp("UNKNOWN", "LOW",
                    "No hay patrones claros. Usar evidencia por cluster (requestId/traceId/caseId) para aislar causa.",
                    evidenceFromClusters("Sin patrones fuertes", clusters)));
        }

        return out;
    }

    private List<String> evidenceFromClusters(String header, List<ErrorCorrelationDto.Cluster> clusters) {
        List<String> ev = new ArrayList<>();
        ev.add(header);
        if (clusters != null) {
            for (var c : clusters) {
                if (c == null) continue;
                ev.add(c.clusterId + ": matched=" + c.matched +
                        ", topRequestId=" + firstKey(c.topRequestIds) +
                        ", topCaseId=" + firstKey(c.topCaseIds));
            }
        }
        return ev;
    }

    private ErrorCorrelationDto.Hypothesis newHyp(String type, String conf, String summary, List<String> evidence) {
        ErrorCorrelationDto.Hypothesis h = new ErrorCorrelationDto.Hypothesis();
        h.type = type;
        h.confidence = conf;
        h.summary = summary;
        h.evidence = evidence;
        return h;
    }

    // ---------------- utils ----------------

    private static String norm(String s) {
        String x = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        x = UUID.matcher(x).replaceAll("{uuid}");
        x = HEX24.matcher(x).replaceAll("{oid}");
        x = EMAIL.matcher(x).replaceAll("{email}");
        x = PATH_N.matcher(x).replaceAll("{n}");
        x = NUM.matcher(x).replaceAll("{n}");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    private static Set<String> tokens(String s) {
        if (!StringUtils.hasText(s)) return Set.of();
        String[] parts = s.split("[\\s:/,;()\\[\\]{}\"']+");
        Set<String> out = new HashSet<>();
        for (String p : parts) {
            if (!StringUtils.hasText(p)) continue;
            if (p.length() < 3) continue;
            out.add(p);
        }
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int inter = 0;
        for (String x : a) if (b.contains(x)) inter++;
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0.0 : ((double) inter / (double) union);
    }

    private static String snippet(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { return 0L; }
    }

    private static String firstKey(Map<String, Long> m) {
        if (m == null || m.isEmpty()) return null;
        return m.keySet().iterator().next();
    }

    private ZoneId safeZone(String tz) {
        if (!StringUtils.hasText(tz)) return ZoneId.of(DEFAULT_TZ);
        try { return ZoneId.of(tz.trim()); }
        catch (Exception e) { return ZoneId.of(DEFAULT_TZ); }
    }

    private static Map<String, Object> safeMap(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (!(k instanceof String ks)) continue;
            if (v == null) continue; // clave para evitar NPE
            m.put(ks, v);
        }
        return m;
    }

    // obtener nested path en Document (dot notation)
    private static String getPath(Document d, String path) {
        if (d == null || path == null) return null;
        if (!path.contains(".")) return d.getString(path);
        String[] parts = path.split("\\.");
        Object cur = d;
        for (String p : parts) {
            if (!(cur instanceof Document cd)) return null;
            cur = cd.get(p);
        }
        return (cur == null) ? null : String.valueOf(cur);
    }

    private Criteria nonEmpty(String fieldPath) {
        return new Criteria().andOperator(
                Criteria.where(fieldPath).exists(true),
                Criteria.where(fieldPath).ne(null),
                Criteria.where(fieldPath).ne("")
        );
    }

    // ---------------- internal classes ----------------

    private static class Item {
        final String raw;
        final Set<String> tokens;
        Item(String raw, Set<String> tokens) { this.raw = raw; this.tokens = tokens; }
    }

    private static class ClusterTmp {
        String id;
        String representative;
        Set<String> representativeTokens;
        List<String> members;
        List<Double> sims;
    }
}