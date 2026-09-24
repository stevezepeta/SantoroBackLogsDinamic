package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.analytics.AttendanceAnomaliesResponse;
import backlogs.dinamico.api.dto.analytics.GhostUserDto;
import backlogs.dinamico.api.dto.analytics.IncompleteShiftDto;
import backlogs.dinamico.api.dto.analytics.OrphanClosureDto;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

/**
 * Engine de detección de anomalías de asistencia.
 *
 * <p>Procesa la colección {@code log_events} para detectar:</p>
 * <ul>
 *   <li><b>ORPHAN_CLOSURE</b>: cierre de jornada sin entrada previa el mismo día.</li>
 *   <li><b>INCOMPLETE_SHIFT</b>: inicio de jornada sin salida después de 12 horas.</li>
 *   <li><b>GHOST_USER</b>: usuario activo sin logs en las últimas 48 horas.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceAnomalyService {

    private static final String COLLECTION = "log_events";
    private static final String HINT_INDEX = "idx_tenant_system_time_v2";
    private static final int CURSOR_BATCH_SIZE = 1000;
    private static final String START_TYPE = "INICIAR_ASISTENCIA";
    private static final String END_TYPE = "FINALIZAR_ASISTENCIA";
    private static final String REGISTER_TYPE = "REGISTRAR_ASISTENCIA";
    private static final Set<String> ATTENDANCE_EVENT_TYPES = Set.of(START_TYPE, END_TYPE, REGISTER_TYPE);
    private static final String DEFAULT_SYSTEM = "TRUSTVALUE";
    private static final long INCOMPLETE_SHIFT_HOURS = 12L;
    private static final long GHOST_HOURS = 48L;

    private static final Set<String> DASHBOARD_MARKERS = Set.of(
            "ADMIN", "DASHBOARD", "ORG ADMIN", "SYSTEM MANAGER", "AUDITOR", "SUPPORT TI",
            "ESAU ABIMAEL", "ALAN ORTEGA"
    );

    private final MongoTemplate mongoTemplate;

    /**
     * Detecta anomalías de asistencia para el sistema y fecha indicados.
     *
     * @param system sistema a analizar; si es nulo o vacío se usa "TRUSTVALUE"
     * @param date   fecha a analizar; si es nula se usa hoy (hora local del servidor)
     */
    public AttendanceAnomaliesResponse detect(String system, LocalDate date) {
        ObjectId tenantId = TenantContext.requireTenantId();
        String effectiveSystem = StringUtils.hasText(system) ? system : DEFAULT_SYSTEM;
        LocalDate effectiveDate = date != null ? date : LocalDate.now();

        ZoneId zone = ZoneId.systemDefault();
        Instant dayStart = effectiveDate.atStartOfDay(zone).toInstant().truncatedTo(ChronoUnit.SECONDS);
        Instant dayEnd = effectiveDate.plusDays(1).atStartOfDay(zone).toInstant().truncatedTo(ChronoUnit.SECONDS);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        boolean supportsAttendance;
        List<OrphanClosureDto> orphanClosures;
        List<IncompleteShiftDto> incompleteShifts;
        List<GhostUserDto> ghostUsers;

        try {
            supportsAttendance = checkSystemSupportsAttendance(tenantId, effectiveSystem);

            if (supportsAttendance) {
                // ── 1) Eventos de asistencia del día ordenados cronológicamente ──
                List<Document> attendanceEvents = fetchAttendanceEvents(tenantId, effectiveSystem, dayStart, dayEnd);

                // ── 2) Aplicar reglas A y B por usuario ──
                Map<String, List<Document>> eventsByUser = groupByUser(attendanceEvents);
                orphanClosures = new ArrayList<>();
                incompleteShifts = new ArrayList<>();

                for (Map.Entry<String, List<Document>> entry : eventsByUser.entrySet()) {
                    scanUserEvents(entry.getValue(), orphanClosures, incompleteShifts, now);
                }
            } else {
                orphanClosures = List.of();
                incompleteShifts = List.of();
            }

            // ── 3) Usuarios fantasmas (sin logs en las últimas 48h en el sistema seleccionado) ──
            ghostUsers = detectGhostUsers(tenantId, effectiveSystem, now);
        } catch (Exception e) {
            log.warn("[detect] Query de anomalías de asistencia excedió el tiempo límite o falló: {}", e.getMessage());
            return new AttendanceAnomaliesResponse(
                    effectiveSystem,
                    effectiveDate.toString(),
                    false,
                    0,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        int totalAnomalies = orphanClosures.size() + incompleteShifts.size() + ghostUsers.size();

        return new AttendanceAnomaliesResponse(
                effectiveSystem,
                effectiveDate.toString(),
                supportsAttendance,
                totalAnomalies,
                orphanClosures,
                incompleteShifts,
                ghostUsers
        );
    }

    /**
     * Determina si el sistema tiene eventos de asistencia registrados.
     */
    private boolean checkSystemSupportsAttendance(ObjectId tenantId, String system) {
        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").regex("^" + Pattern.quote(system) + "$", "i")
                .and("eventType").in(ATTENDANCE_EVENT_TYPES);

        Query query = new Query(criteria).limit(1).withHint(HINT_INDEX);
        return mongoTemplate.exists(query, COLLECTION);
    }

    private List<Document> fetchAttendanceEvents(ObjectId tenantId, String system, Instant from, Instant to) {
        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").regex("^" + java.util.regex.Pattern.quote(system) + "$", "i")
                .and("eventTime").gte(Date.from(from)).lt(Date.from(to))
                .and("eventType").in(START_TYPE, END_TYPE);

        Query query = new Query(criteria)
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "eventTime"))
                .withHint(HINT_INDEX);

        return mongoTemplate.find(query, Document.class, COLLECTION);
    }

    private Map<String, List<Document>> groupByUser(List<Document> events) {
        Map<String, List<Document>> map = new LinkedHashMap<>();
        for (Document doc : events) {
            String key = userKey(doc);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(doc);
        }
        // Cada lista ya viene ordenada por eventTime ASC porque la query principal lo ordena.
        return map;
    }

    private void scanUserEvents(
            List<Document> events,
            List<OrphanClosureDto> orphanClosures,
            List<IncompleteShiftDto> incompleteShifts,
            Instant now) {

        Document pendingStart = null;

        for (Document event : events) {
            String type = event.getString("eventType");
            if (START_TYPE.equalsIgnoreCase(type)) {
                // Si había un inicio pendiente sin cierre, se considera incompleto
                if (pendingStart != null && isOverdue(pendingStart, now)) {
                    incompleteShifts.add(buildIncompleteShift(pendingStart, now));
                }
                pendingStart = event;
            } else if (END_TYPE.equalsIgnoreCase(type)) {
                if (pendingStart != null) {
                    // Cierre emparejado con el inicio pendiente
                    pendingStart = null;
                } else {
                    // Cierre sin entrada previa
                    orphanClosures.add(buildOrphanClosure(event));
                }
            }
        }

        // Inicio pendiente al finalizar el día
        if (pendingStart != null && isOverdue(pendingStart, now)) {
            incompleteShifts.add(buildIncompleteShift(pendingStart, now));
        }
    }

    private boolean isOverdue(Document startEvent, Instant now) {
        Instant startTime = toInstant(startEvent.get("eventTime"));
        return startTime != null && startTime.plus(INCOMPLETE_SHIFT_HOURS, ChronoUnit.HOURS).isBefore(now);
    }

    private OrphanClosureDto buildOrphanClosure(Document event) {
        String username = usernameOf(event);
        String fullName = fullNameOf(event);
        Instant eventTime = toInstant(event.get("eventTime"));
        String device = extractDevice(event);

        return new OrphanClosureDto(
                username,
                fullName,
                eventTime,
                "Intento de cierre de jornada sin registro de entrada previo",
                device,
                "CRITICAL"
        );
    }

    private IncompleteShiftDto buildIncompleteShift(Document startEvent, Instant now) {
        String username = usernameOf(startEvent);
        String fullName = fullNameOf(startEvent);
        Instant startTime = toInstant(startEvent.get("eventTime"));
        double hoursElapsed = startTime != null
                ? Math.round(ChronoUnit.MINUTES.between(startTime, now) / 60.0 * 10.0) / 10.0
                : 0.0;

        return new IncompleteShiftDto(
                username,
                fullName,
                startTime,
                hoursElapsed,
                "Jornada iniciada hace " + hoursElapsed + "h sin marcado de salida",
                "WARNING"
        );
    }

    private List<GhostUserDto> detectGhostUsers(ObjectId tenantId, String system, Instant now) {
        Instant since = now.minus(GHOST_HOURS, ChronoUnit.HOURS);

        // Actores distintos con registros históricos en el sistema seleccionado
        Aggregation historicalAgg = newAggregation(
                match(Criteria.where("tenant_id").is(tenantId)
                        .and("system").regex("^" + Pattern.quote(system) + "$", "i")
                        .and("actor.username").exists(true).ne(null)),
                group("actor.username")
                        .first("actor.fullName").as("fullName")
                        .max("eventTime").as("lastSeen")
                        .first("actor.role").as("role")
        ).withOptions(buildOptions(HINT_INDEX));

        List<Document> historicalActors = mongoTemplate.aggregate(historicalAgg, COLLECTION, Document.class)
                .getMappedResults();

        // Actores distintos vistos en el sistema seleccionado durante la ventana de ghost
        Aggregation recentAgg = newAggregation(
                match(Criteria.where("tenant_id").is(tenantId)
                        .and("system").regex("^" + Pattern.quote(system) + "$", "i")
                        .and("eventTime").gte(Date.from(since))
                        .and("actor.username").exists(true).ne(null)),
                group("actor.username")
        ).withOptions(buildOptions(HINT_INDEX));

        List<Document> recentActors = mongoTemplate.aggregate(recentAgg, COLLECTION, Document.class)
                .getMappedResults();

        Set<String> recentUsernames = recentActors.stream()
                .map(d -> d.getString("_id"))
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toLowerCase())
                .collect(Collectors.toSet());

        List<GhostUserDto> ghosts = new ArrayList<>();
        for (Document actor : historicalActors) {
            String username = actor.getString("_id");
            if (!StringUtils.hasText(username)) {
                continue;
            }

            String fullName = actor.getString("fullName");
            String role = actor.getString("role");
            Instant lastSeen = toInstant(actor.get("lastSeen"));

            // Excluir actores administrativos globales o de dashboard
            if (isDashboardActor(username, fullName, role)) {
                continue;
            }

            String normalizedUsername = username.trim().toLowerCase();
            if (!recentUsernames.contains(normalizedUsername)) {
                ghosts.add(new GhostUserDto(
                        username,
                        fullName,
                        lastSeen,
                        "Usuario inactivo en " + system + " en las últimas " + GHOST_HOURS + "h",
                        "INFO"
                ));
            }
        }

        return ghosts;
    }

    private boolean isDashboardActor(String username, String fullName, String role) {
        return isDashboardValue(role)
                || isDashboardValue(username)
                || isDashboardValue(fullName);
    }

    private boolean isDashboardValue(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toUpperCase().replace('_', ' ');
        return DASHBOARD_MARKERS.contains(normalized);
    }

    private String userKey(Document event) {
        Document actor = event.get("actor", Document.class);
        if (actor == null) {
            return "";
        }
        String username = actor.getString("username");
        String fullName = actor.getString("fullName");
        if (StringUtils.hasText(username)) {
            return username.trim().toLowerCase();
        }
        if (StringUtils.hasText(fullName)) {
            return fullName.trim().toLowerCase();
        }
        String id = actor.getString("id");
        return id != null ? id.trim().toLowerCase() : "";
    }

    private String usernameOf(Document event) {
        Document actor = event.get("actor", Document.class);
        return actor != null ? actor.getString("username") : null;
    }

    private String fullNameOf(Document event) {
        Document actor = event.get("actor", Document.class);
        return actor != null ? actor.getString("fullName") : null;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Date d) {
            return d.toInstant();
        }
        if (value instanceof Instant i) {
            return i;
        }
        return null;
    }

    private String extractDevice(Document event) {
        Object device = event.get("device");
        if (device instanceof String s && StringUtils.hasText(s)) {
            return s.trim();
        }
        if (device instanceof Document d) {
            String model = d.getString("model");
            String name = d.getString("name");
            String code = d.getString("code");
            return StringUtils.hasText(model) ? model.trim()
                    : StringUtils.hasText(name) ? name.trim()
                    : StringUtils.hasText(code) ? code.trim() : null;
        }

        Object systemDevice = nested(event, "systemAndDevices", "device");
        Object deviceInfoModel = nested(event, "deviceInfo", "model");
        Object userAgent = nested(event, "requestInfo", "userAgent");
        Object metaDevice = nested(event, "meta", "device");

        return firstNonBlank(
                asString(systemDevice),
                asString(deviceInfoModel),
                asString(userAgent),
                asString(metaDevice)
        );
    }

    private static Object nested(Document root, String parent, String child) {
        if (root == null) {
            return null;
        }
        Object p = root.get(parent);
        if (p instanceof Document d) {
            return d.get(child);
        }
        return null;
    }

    private static String asString(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (StringUtils.hasText(c)) {
                return c;
            }
        }
        return null;
    }

    private AggregationOptions buildOptions(String hint) {
        return AggregationOptions.builder()
                .cursorBatchSize(CURSOR_BATCH_SIZE)
                .allowDiskUse(true)
                .hint(hint)
                .maxTime(Duration.ofMillis(3000))
                .build();
    }

}
