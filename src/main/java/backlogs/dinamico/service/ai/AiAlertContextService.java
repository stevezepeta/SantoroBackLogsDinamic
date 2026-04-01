package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.ai.AiAlertRecord;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.repository.ai.AiAlertRepository;
import backlogs.dinamico.service.ai.dto.AlertContextDto;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class AiAlertContextService {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AiAlertRepository alertRepo;
    private final MongoTemplate mongoTemplate;

    @Value("${multitenant.log-collection:log_events}")
    private String logCollection;

    // Campos reales (LogEvent)
    private static final String F_TENANT = "tenant_id";
    private static final String F_TIME   = "eventTime";
    private static final String F_SEV    = "severity";
    private static final String F_SYS    = "system";
    private static final String F_MSG    = "message";
    private static final String F_TYPE   = "eventType";
    private static final String F_STATUS = "status";
    private static final String F_OUT    = "outcome";

    public AlertContextDto getContext(ObjectId tenantId, ObjectId alertId, String tz, int limit) {

        AiAlertRecord rec = alertRepo.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "alert_not_found"));

        if (!tenantId.equals(rec.getTenantId())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "alert_not_found");
        }

        ZoneId zone = safeZone(tz);

        int safeLimit = Math.min(Math.max(limit, 1), 200);

        // 1) Definir ventana del "bucket" (si existe) para obtener contexto puntual
        Instant bucketFrom;
        Instant bucketTo;

        if (rec.getBucketStart() != null) {
            bucketFrom = rec.getBucketStart();
            if ("daily".equalsIgnoreCase(rec.getGranularity())) {
                bucketTo = bucketFrom.plus(1, ChronoUnit.DAYS);
            } else {
                // default hourly
                bucketTo = bucketFrom.plus(1, ChronoUnit.HOURS);
            }
        } else if (rec.getWindowFrom() != null && rec.getWindowTo() != null) {
            // fallback: usa ventana completa
            bucketFrom = rec.getWindowFrom();
            bucketTo = rec.getWindowTo();
        } else {
            // último fallback seguro
            bucketTo = Instant.now();
            bucketFrom = bucketTo.minus(1, ChronoUnit.HOURS);
        }

        // 2) Stats del bucket
        Map<String, Long> bucketSev = aggregateCountsByField(tenantId, bucketFrom, bucketTo, F_SEV);
        long bucketTotal = bucketSev.values().stream().mapToLong(Long::longValue).sum();

        List<AlertContextDto.TopItem> topSystems   = aggregateTopByField(tenantId, bucketFrom, bucketTo, F_SYS, safeTopLimit(rec, 5));
        List<AlertContextDto.TopItem> topTypes     = aggregateTopByField(tenantId, bucketFrom, bucketTo, F_TYPE, safeTopLimit(rec, 5));
        List<AlertContextDto.TopItem> topStatus    = aggregateTopByField(tenantId, bucketFrom, bucketTo, F_STATUS, safeTopLimit(rec, 5));
        List<AlertContextDto.TopItem> topOutcome   = aggregateTopByField(tenantId, bucketFrom, bucketTo, F_OUT, safeTopLimit(rec, 5));
        List<AlertContextDto.TopError> topErrors   = aggregateTopErrors(tenantId, bucketFrom, bucketTo, 5);

        // 3) Samples (prioriza ERROR/FATAL)
        List<LogEvent> samples = fetchSamplesPreferErrors(tenantId, bucketFrom, bucketTo, safeLimit);

        // 4) Derivar correlación rápida
        Map<String, Long> topRequestIds = countTop(samples.stream()
                .map(le -> le.getCorrelation() == null ? null : le.getCorrelation().getRequestId())
                .filter(StringUtils::hasText)
                .toList(), 10);

        Map<String, Long> topCaseIds = countTop(samples.stream()
                .map(LogEvent::getCaseId)
                .filter(StringUtils::hasText)
                .toList(), 10);

        Map<String, Long> topActors = countTop(samples.stream()
                .map(le -> le.getActor() == null ? null : le.getActor().getUsername())
                .filter(StringUtils::hasText)
                .toList(), 10);

        // 5) Armar respuesta
        AlertContextDto out = new AlertContextDto();
        out.tz = zone.getId();

        out.alert = mapAlert(rec, zone);
        out.bucket = new AlertContextDto.BucketContext();
        out.bucket.from = toIso(bucketFrom);
        out.bucket.fromLocal = toLocal(bucketFrom, zone);
        out.bucket.to = toIso(bucketTo);
        out.bucket.toLocal = toLocal(bucketTo, zone);

        out.bucket.total = bucketTotal;
        out.bucket.severities = bucketSev;

        out.bucket.topSystems = topSystems;
        out.bucket.topEventTypes = topTypes;
        out.bucket.topStatus = topStatus;
        out.bucket.topOutcome = topOutcome;
        out.bucket.topErrors = topErrors;

        out.samples = samples.stream().map(le -> mapSample(le, zone)).toList();
        out.topRequestIds = topRequestIds;
        out.topCaseIds = topCaseIds;
        out.topActors = topActors;

        return out;
    }

    // -------------------- mapping --------------------

    private AlertContextDto.Alert mapAlert(AiAlertRecord r, ZoneId zone) {
        AlertContextDto.Alert a = new AlertContextDto.Alert();
        a.id = r.getId() == null ? null : r.getId().toHexString();
        a.tenantId = r.getTenantId() == null ? null : r.getTenantId().toHexString();
        a.granularity = r.getGranularity();

        a.windowFrom = toIso(r.getWindowFrom());
        a.windowFromLocal = toLocal(r.getWindowFrom(), zone);

        a.windowTo = toIso(r.getWindowTo());
        a.windowToLocal = toLocal(r.getWindowTo(), zone);

        a.bucketStart = toIso(r.getBucketStart());
        a.bucketStartLocal = toLocal(r.getBucketStart(), zone);

        a.createdAt = toIso(r.getCreatedAt());
        a.createdAtLocal = toLocal(r.getCreatedAt(), zone);

        a.status = r.getStatus();
        a.total = r.getTotal();
        a.errorRate = r.getErrorRate();

        a.severities = r.getSeverities();
        a.alerts = r.getAlerts();

        a.fingerprint = r.getFingerprint();

        a.state = r.getState();
        a.ackedAt = toIso(r.getAckedAt());
        a.ackedAtLocal = toLocal(r.getAckedAt(), zone);
        a.ackedBy = r.getAckedBy();

        a.resolvedAt = toIso(r.getResolvedAt());
        a.resolvedAtLocal = toLocal(r.getResolvedAt(), zone);
        a.resolvedBy = r.getResolvedBy();
        return a;
    }

    private AlertContextDto.LogSample mapSample(LogEvent le, ZoneId zone) {
        AlertContextDto.LogSample s = new AlertContextDto.LogSample();
        s.id = le.getId() == null ? null : le.getId().toHexString();

        s.eventTime = toIso(le.getEventTime());
        s.eventTimeLocal = toLocal(le.getEventTime(), zone);

        s.system = le.getSystem();
        s.eventType = le.getEventType();
        s.status = le.getStatus();
        s.outcome = le.getOutcome();
        s.severity = le.getSeverity();

        s.caseId = le.getCaseId();

        String msg = le.getMessage();
        s.message = (msg == null) ? null : (msg.length() > 400 ? msg.substring(0, 400) : msg);

        if (le.getCorrelation() != null) {
            s.requestId = le.getCorrelation().getRequestId();
            s.traceId = le.getCorrelation().getTraceId();
        }

        if (le.getActor() != null) {
            s.actorId = le.getActor().getId();
            s.actorUsername = le.getActor().getUsername();
            s.actorFullName = le.getActor().getFullName();
        }

        if (le.getLocation() != null) {
            s.locationId = le.getLocation().getId();
            s.locationName = le.getLocation().getName();
        }

        return s;
    }

    // -------------------- queries --------------------

    private List<LogEvent> fetchSamplesPreferErrors(ObjectId tenantId, Instant from, Instant to, int limit) {
        // 1) errores primero
        Query qErr = new Query(Criteria.where(F_TENANT).is(tenantId)
                .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                .and(F_SEV).in("ERROR", "FATAL"));
        qErr.with(Sort.by(Sort.Direction.DESC, F_TIME));
        qErr.limit(limit);

        List<LogEvent> err = mongoTemplate.find(qErr, LogEvent.class, logCollection);
        if (err.size() >= limit) return err;

        // 2) completa con cualquier severidad (excluyendo los ya traídos)
        Set<ObjectId> seen = err.stream().map(LogEvent::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        Query qAll = new Query(Criteria.where(F_TENANT).is(tenantId)
                .and(F_TIME).gte(Date.from(from)).lt(Date.from(to)));
        qAll.with(Sort.by(Sort.Direction.DESC, F_TIME));
        qAll.limit(limit * 2); // traemos más para filtrar repeats

        List<LogEvent> all = mongoTemplate.find(qAll, LogEvent.class, logCollection);

        List<LogEvent> out = new ArrayList<>(err);
        for (LogEvent le : all) {
            if (out.size() >= limit) break;
            if (le.getId() != null && seen.contains(le.getId())) continue;
            out.add(le);
        }
        return out;
    }

    private Map<String, Long> aggregateCountsByField(ObjectId tenantId, Instant from, Instant to, String field) {
        Aggregation agg = newAggregation(
                match(Criteria.where(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(field).exists(true).ne(null)
                ),
                group("$" + field).count().as("c"),
                sort(Sort.by(Sort.Direction.DESC, "c")),
                project().and("_id").as("k").and("c").as("v").andExclude("_id")
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        Map<String, Long> out = new LinkedHashMap<>();
        for (Document d : rows) {
            out.put(String.valueOf(d.get("k")), toLong(d.get("v")));
        }
        return out;
    }

    private List<AlertContextDto.TopItem> aggregateTopByField(ObjectId tenantId, Instant from, Instant to, String field, int limit) {
        Aggregation agg = newAggregation(
                match(Criteria.where(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(field).exists(true).ne(null)
                ),
                group("$" + field).count().as("c"),
                sort(Sort.by(Sort.Direction.DESC, "c")),
                limit(limit),
                project().and("_id").as("name").and("c").as("count").andExclude("_id")
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        return rows.stream()
                .map(d -> new AlertContextDto.TopItem(
                        Objects.toString(d.get("name"), ""),
                        toLong(d.get("count"))
                ))
                .toList();
    }

    private List<AlertContextDto.TopError> aggregateTopErrors(ObjectId tenantId, Instant from, Instant to, int limit) {
        Aggregation agg = newAggregation(
                match(Criteria.where(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(F_SEV).in("ERROR", "FATAL")
                        .and(F_MSG).exists(true).ne(null)
                ),
                addFields().addFieldWithValue("errKey",
                        new Document("$substrCP", List.of("$" + F_MSG, 0, 200))
                ).build(),
                group("$errKey").count().as("c"),
                sort(Sort.by(Sort.Direction.DESC, "c")),
                limit(limit),
                project().and("_id").as("key").and("c").as("count").andExclude("_id")
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        return rows.stream()
                .map(d -> new AlertContextDto.TopError(
                        Objects.toString(d.get("key"), ""),
                        toLong(d.get("count"))
                ))
                .toList();
    }

    // -------------------- utils --------------------

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private ZoneId safeZone(String tz) {
        try {
            if (!StringUtils.hasText(tz)) return ZoneId.of("America/Mexico_City");
            return ZoneId.of(tz.trim());
        } catch (Exception e) {
            return ZoneId.of("America/Mexico_City");
        }
    }

    private String toIso(Instant t) {
        return t == null ? null : t.toString();
    }

    private String toLocal(Instant t, ZoneId zone) {
        return t == null ? null : t.atZone(zone).format(ISO_OFFSET);
    }

    private Map<String, Long> countTop(List<String> values, int limit) {
        Map<String, Long> counts = values.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(x -> x, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (x, y) -> x,
                        LinkedHashMap::new
                ));
    }

    private int safeTopLimit(AiAlertRecord rec, int def) {
        return Math.min(Math.max(def, 1), 20);
    }
}
