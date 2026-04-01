package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.ai.AiAlertRecord;
import backlogs.dinamico.repository.ai.AiAlertRepository;
import backlogs.dinamico.service.ai.dto.AlertOperatorExplainDto;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class AiAlertOperatorExplainService {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AiAlertRepository alertRepo;
    private final MongoTemplate mongoTemplate;

    @Value("${multitenant.log-collection:log_events}")
    private String logCollection;

    // Campos reales en Mongo (tu LogEvent)
    private static final String F_TENANT = "tenant_id";
    private static final String F_TIME   = "eventTime";
    private static final String F_SYS    = "system";
    private static final String F_TYPE   = "eventType";
    private static final String F_STATUS = "status";
    private static final String F_OUT    = "outcome";
    private static final String F_SEV    = "severity";
    private static final String F_MSG    = "message";

    private static final String F_REQ_ID  = "correlation.requestId";
    private static final String F_TRACE   = "correlation.traceId";
    private static final String F_CASE    = "caseId";
    private static final String F_ACT_ID  = "actor.id";
    private static final String F_ACT_USR = "actor.username";
    private static final String F_ACT_NM  = "actor.fullName";
    private static final String F_LOC_ID  = "location.id";
    private static final String F_LOC_NM  = "location.name";

    private static final String F_IS_ERROR = "isError";
    private static final String F_MSG_KEY = "messageKey";

    // thresholds base (puedes alinearlos a SummaryInsightsService)
    private static final double ERR_WARN = 0.20;
    private static final double ERR_CRIT = 0.50;

    public AlertOperatorExplainDto explain(ObjectId tenantId, ObjectId alertId, String tz, int sampleLimit) {

        if (tenantId == null || alertId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing_ids");
        }

        ZoneId zone = safeZone(tz);

        AiAlertRecord rec = alertRepo.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "alert_not_found"));

        if (rec.getTenantId() == null || !tenantId.equals(rec.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "alert_not_found");
        }

        // ---------------------- Bucket range (alineado a hora/dia en TZ) ----------------------
        String granularity = (rec.getGranularity() != null) ? rec.getGranularity() : "hourly";

        Instant base = (rec.getBucketStart() != null) ? rec.getBucketStart()
                : (rec.getWindowFrom() != null) ? rec.getWindowFrom()
                : (rec.getWindowTo() != null) ? rec.getWindowTo().minus(1, ChronoUnit.HOURS)
                : Instant.now().minus(1, ChronoUnit.HOURS);

        ZonedDateTime baseZ = ZonedDateTime.ofInstant(base, zone);

        Instant bucketFrom;
        Instant bucketTo;

        if ("daily".equalsIgnoreCase(granularity)) {
            ZonedDateTime dayStart = baseZ.truncatedTo(ChronoUnit.DAYS);
            bucketFrom = dayStart.toInstant();
            bucketTo = dayStart.plusDays(1).toInstant();
        } else {
            ZonedDateTime hourStart = baseZ.truncatedTo(ChronoUnit.HOURS);
            bucketFrom = hourStart.toInstant();
            bucketTo = hourStart.plusHours(1).toInstant();
        }

        if (!bucketFrom.isBefore(bucketTo)) {
            bucketTo = bucketFrom.plus(1, ChronoUnit.HOURS);
        }

        // ----------------- Primary alert ----------------------
        List<SummaryInsightsDto.Alert> recAlerts =
                (rec.getAlerts() != null) ? rec.getAlerts() : List.of();

        SummaryInsightsDto.Alert primary = pickPrimary(rec.getAlerts());

        String primaryType = normalizeUpper(primary != null ? primary.type : null, "UNKNOW");
        String primaryLevel = normalizeUpper(primary != null ? primary.level : null, "INFO");

        // ---------------- Evidencia (tops + samples + correlación) ----------------
        int safeSamples = Math.min(Math.max(sampleLimit, 0), 50);

        List<AlertOperatorExplainDto.Sample> samples =
                (safeSamples == 0) ? List.of() : Optional.ofNullable(
                        fetchSamples(tenantId, bucketFrom, bucketTo, zone, safeSamples)
                ).orElse(List.of());

        Map<String, Long> topRequestIds = Optional.ofNullable(
                aggregateTopCounts(tenantId, bucketFrom, bucketTo, F_REQ_ID, 5)
        ).orElseGet(Map::of);

        Map<String, Long> topCaseIds = Optional.ofNullable(
                aggregateTopCounts(tenantId, bucketFrom, bucketTo, F_CASE, 5)
        ).orElseGet(Map::of);

        Map<String, Long> topActors = Optional.ofNullable(
                aggregateTopCounts(tenantId, bucketFrom, bucketTo, F_ACT_USR, 5)
        ).orElseGet(Map::of);

        List<AlertOperatorExplainDto.TopItem> topSystems = Optional.ofNullable(
                aggregateTopItems(tenantId, bucketFrom, bucketTo, F_SYS, 5)
        ).orElse(List.of());

        List<AlertOperatorExplainDto.TopItem> topEventTypes = Optional.ofNullable(
                aggregateTopItems(tenantId, bucketFrom, bucketTo, F_TYPE, 5)
        ).orElse(List.of());

        List<AlertOperatorExplainDto.TopError> topErrors = Optional.ofNullable(
                aggregateTopErrors(tenantId, bucketFrom, bucketTo, 5)
        ).orElse(List.of());

        // ---------------- Texto significado + 3 pasos (con defaults) ----------------
        String meaning = buildMeaning(rec, primaryType, primaryLevel,
                topSystems, topEventTypes, topErrors, topRequestIds, topCaseIds);

        String impact = buildImpact(primaryType, primaryLevel);

        String triage = buildTriageLine(rec, primaryType, primaryLevel, topSystems);

        List<AlertOperatorExplainDto.Step> steps = Optional.ofNullable(
                build3Steps(rec, primaryType, primaryLevel,
                        topSystems, topEventTypes, topErrors, topRequestIds, topCaseIds, topActors)
        ).orElse(List.of());

        // --------------------------- Respuesta --------------------------------
        AlertOperatorExplainDto out = new AlertOperatorExplainDto();
        out.tz = zone.getId();
        out.alertId = (rec.getId() != null) ? rec.getId().toHexString() : null;

        out.granularity = granularity;
        out.status = rec.getStatus();
        out.state  = rec.getState(); // puede ser null y está OK

        out.windowFrom = rec.getWindowFrom() != null ? rec.getWindowFrom().toString() : null;
        out.windowTo   = rec.getWindowTo() != null ? rec.getWindowTo().toString() : null;
        out.windowFromLocal = toLocal(rec.getWindowFrom(), zone);
        out.windowToLocal   = toLocal(rec.getWindowTo(), zone);

        out.bucketFrom = bucketFrom.toString();
        out.bucketTo   = bucketTo.toString();
        out.bucketFromLocal = toLocal(bucketFrom, zone);
        out.bucketToLocal   = toLocal(bucketTo, zone);

        out.primaryType = primaryType;
        out.primaryLevel = primaryLevel;

        out.operatorMeaning = meaning;
        out.impact = impact;
        out.triageSummary = triage;

        out.steps = steps;

        out.topSystems = topSystems;
        out.topEventTypes = topEventTypes;
        out.topErrors = topErrors;

        out.samples = samples;
        out.topRequestIds = topRequestIds;
        out.topCaseIds = topCaseIds;
        out.topActors = topActors;

        return out;
    }

    // ---------------------------- Heurísticas ----------------------------
    private SummaryInsightsDto.Alert pickPrimary(List<SummaryInsightsDto.Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) return null;

        SummaryInsightsDto.Alert best = null;
        int bestScore = Integer.MIN_VALUE;

        for (SummaryInsightsDto.Alert a : alerts) {
            if (a == null) continue;

            int levelScore = scoreLevel(a.level);      // ya es null-safe
            int typeScore  = scoreType(a.type);

            // ponderación: primero nivel, luego tipo
            int score = (levelScore * 100) + typeScore;

            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    private int scoreType(String type) {
        if (type == null) return 0;
        String t = type.trim().toUpperCase(java.util.Locale.ROOT);

        return switch (t) {
            // Los que más le importan a un operador
            case "HIGH_ERROR_RATE" -> 50;
            case "VOLUME_SPIKE"    -> 40;
            case "NO_DATA"         -> 30;
            case "SYSTEM_DOMINANCE"-> 10;
            default                -> 0;
        };
    }

    private int scoreLevelSafe(String level) {
        if (level == null) return 0;
        String l = level.trim().toUpperCase(Locale.ROOT);

        return switch (l) {
            case "CRIT", "CRITICAL", "FATAL" -> 300;
            case "ERROR" -> 250;
            case "WARN", "WARNING" -> 200;
            case "INFO" -> 100;
            case "OK", "SUCCESS" -> 10;
            default -> 0;
        };
    }

    private int scoreTypeSafe(String type) {
        if (type == null) return 0;
        String t = type.trim().toUpperCase(Locale.ROOT);

        // "Accionables"
        return switch (t) {
            case "HIGH_ERROR_RATE" -> 300;
            case "NO_DATA" -> 200;
            case "SPIKE" -> 190;
            case "SYSTEM_DOMINANCE" -> 50;
            default -> 0;
        };

    }

    private int scoreLevel(String level) {
        if (level == null) return 0;

        String l = level.trim().toUpperCase(Locale.ROOT);

        return switch (l) {
            case "CRIT", "CRITICAL" -> 3;
            case "WARN", "WARNING"  -> 2;
            case "INFO"            -> 1;
            default                -> 0;
        };
    }

    private String buildTriageLine(AiAlertRecord rec, String type, String level, List<AlertOperatorExplainDto.TopItem> topSystems) {
        String sys = (!topSystems.isEmpty()) ? topSystems.get(0).name : "N/D";
        return "Tipo=" + type + " (" + level + "), status=" + rec.getStatus() + ", granularity=" + rec.getGranularity() + ", topSystem=" + sys + ".";
    }

    private String buildImpact(String type, String level) {
        return switch (type) {
            case "HIGH_ERROR_RATE" -> ("CRIT".equalsIgnoreCase(level))
                    ? "Usuarios/operación pueden estar bloqueados (muchos fallos). Riesgo de incidente en producción."
                    : "Hay un incremento de fallos. Puede degradar experiencia o provocar reintentos/colas.";
            case "NO_DATA" -> "Ceguera operativa: no hay logs. No se puede monitorear ni auditar eventos del sistema.";
            case "VOLUME_SPIKE" -> "Posible tormenta de logs/reintentos/loop. Puede saturar almacenamiento y ocultar errores reales.";
            case "SYSTEM_DOMINANCE" -> "Un sistema concentra el tráfico. Puede ser normal o indicar que otros sistemas dejaron de reportar.";
            default -> "Impacto depende del patrón observado en logs y correlaciones.";
        };
    }

    private String buildMeaning(
            AiAlertRecord rec,
            String type,
            String level,
            List<AlertOperatorExplainDto.TopItem> topSystems,
            List<AlertOperatorExplainDto.TopItem> topEventTypes,
            List<AlertOperatorExplainDto.TopError> topErrors,
            Map<String, Long> topRequestIds,
            Map<String, Long> topCaseIds
    ) {
        String sys = (!topSystems.isEmpty()) ? topSystems.get(0).name : "N/D";
        String evt = (!topEventTypes.isEmpty()) ? topEventTypes.get(0).name : "N/D";
        String err = (!topErrors.isEmpty()) ? safeSnippet(topErrors.get(0).key) : null;

        return switch (type) {
            case "HIGH_ERROR_RATE" -> {
                double er = rec.getErrorRate();
                String risk =
                        (er >= ERR_CRIT) ? "CRÍTICO" :
                                (er >= ERR_WARN) ? "ALTO" : "MODERADO";

                StringBuilder sb = new StringBuilder();
                sb.append("Se detectó una tasa elevada de errores (errorRate=")
                        .append(String.format(Locale.ROOT, "%.2f%%", er * 100.0))
                        .append("). Esto indica que una parte relevante de los eventos en el rango están fallando. ")
                        .append("Riesgo: ").append(risk).append(". ");
                sb.append("Sistema más involucrado: ").append(sys).append(". Evento más común: ").append(evt).append(". ");

                if (err != null) sb.append("Error representativo: \"").append(err).append("\". ");
                if (!topRequestIds.isEmpty()) sb.append("Hay correlación por requestId (posible cadena de fallos). ");
                if (!topCaseIds.isEmpty()) sb.append("Hay correlación por caseId (posible caso puntual o flujo específico). ");

                yield sb.toString().trim();
            }
            case "NO_DATA" -> "No hay eventos en el rango. Para un operador significa que el monitoreo quedó ciego: o el sistema fuente no está enviando logs, o el pipeline (Fluent Bit/ingesta/API-key) falló, o el rango/tz no corresponde.";
            case "VOLUME_SPIKE" -> "Hubo un pico anormal de volumen vs promedio. Para un operador suele significar reintentos masivos, un loop de logs o un proceso fuera de control. Se debe identificar el sistema/evento dominante y verificar duplicidad.";
            case "SYSTEM_DOMINANCE" -> "Un solo sistema domina el tráfico en el rango. Si no es esperado, puede indicar que otros sistemas dejaron de reportar o que el sistema dominante entró en modo de spam/reintentos.";
            default -> "Alerta operativa detectada. Se requiere revisar evidencia (tops, muestras y correlación) para confirmar alcance y causa probable.";
        };
    }

    private List<AlertOperatorExplainDto.Step> build3Steps(
            AiAlertRecord rec,
            String type,
            String level,
            List<AlertOperatorExplainDto.TopItem> topSystems,
            List<AlertOperatorExplainDto.TopItem> topEventTypes,
            List<AlertOperatorExplainDto.TopError> topErrors,
            Map<String, Long> topRequestIds,
            Map<String, Long> topCaseIds,
            Map<String, Long> topActors
    ) {
        // Normaliza nulls para evitar NPE por .isEmpty()
        topSystems = (topSystems == null) ? List.of() : topSystems;
        topEventTypes = (topEventTypes == null) ? List.of() : topEventTypes;
        topErrors = (topErrors == null) ? List.of() : topErrors;

        topRequestIds = (topRequestIds == null) ? Map.of() : topRequestIds;
        topCaseIds = (topCaseIds == null) ? Map.of() : topCaseIds;
        topActors = (topActors == null) ? Map.of() : topActors;

        String sys = (!topSystems.isEmpty() && topSystems.get(0) != null) ? topSystems.get(0).name : null;
        String evt = (!topEventTypes.isEmpty() && topEventTypes.get(0) != null) ? topEventTypes.get(0).name : null;

        // sugerencias de filtros para UI o búsquedas (tu front puede usar esto)
        Map<String, Object> baseFilters = new LinkedHashMap<>();
        if (sys != null) baseFilters.put("system", sys);
        if (evt != null) baseFilters.put("eventType", evt);

        // OJO: LinkedHashMap sí permite null, esto NO truena
        baseFilters.put("from", rec.getWindowFrom() != null ? rec.getWindowFrom().toString() : null);
        baseFilters.put("to", rec.getWindowTo() != null ? rec.getWindowTo().toString() : null);

        // Top keys seguros
        String topReq = topRequestIds.keySet().stream().findFirst().orElse(null);
        String topCase = topCaseIds.keySet().stream().findFirst().orElse(null);
        String topActor = topActors.keySet().stream().findFirst().orElse(null);
        String topErr = (!topErrors.isEmpty() && topErrors.get(0) != null) ? safeSnippet(topErrors.get(0).key) : null;

        // switch en null truena, así que lo blindamos
        String safeType = (type == null) ? "UNKNOWN" : type;

        return switch (safeType) {

            case "HIGH_ERROR_RATE" -> List.of(
                    new AlertOperatorExplainDto.Step(
                            "1) Confirmar y delimitar (scope)",
                            List.of(
                                    "Revisar topErrors (¿es 1 error dominante o varios?)",
                                    "Ver si el fallo se concentra en un system/eventType",
                                    "Si hay requestId dominante, seguir la traza por requestId/traceId"
                            ),
                            mergeNonNull(baseFilters, safeMeta(
                                    "severity", List.of("ERROR", "FATAL"),
                                    "requestId", topReq,
                                    "caseId", topCase
                            ))
                    ),
                    new AlertOperatorExplainDto.Step(
                            "2) Correlacionar y encontrar causa probable",
                            List.of(
                                    "Abrir 5-10 muestras del bucket y validar patrón (mismo mensaje/código/outcome)",
                                    "Validar si el error coincide con cambios recientes (deploy/config/credenciales)",
                                    "Si hay actor dominante, validar si es un flujo específico o usuario en pruebas"
                            ),
                            mergeNonNull(baseFilters, safeMeta(
                                    "messageContains", topErr,
                                    "actorUsername", topActor
                            ))
                    ),
                    new AlertOperatorExplainDto.Step(
                            "3) Mitigar / escalar con evidencia",
                            List.of(
                                    "Si CRIT: escalar incidente y abrir ticket con evidencia (topErrors + muestras + requestId/caseId)",
                                    "Si WARN: crear ticket preventivo y monitorear 1-2 horas",
                                    "ACK la alerta cuando un humano toma ownership; RESOLVE cuando ya no se reproduce y hay verificación"
                            ),
                            mergeNonNull(baseFilters, safeMeta(
                                    "recommendedAction", ("CRIT".equalsIgnoreCase(level) ? "INCIDENT" : "TICKET"),
                                    "topRequestId", topReq,
                                    "topCaseId", topCase
                            ))
                    )
            );

            case "NO_DATA" -> List.of(
                    new AlertOperatorExplainDto.Step(
                            "1) Validar si es tema de rango o tz",
                            List.of(
                                    "Confirmar tz y ventana (from/to) usada por la alerta",
                                    "Probar resumen hourly de la última hora y daily del último día completo",
                                    "Buscar manualmente 1 log reciente del tenant"
                            ),
                            mergeNonNull(baseFilters, safeMeta("check", "RANGE_TZ"))
                    ),
                    new AlertOperatorExplainDto.Step(
                            "2) Verificar pipeline/ingesta",
                            List.of(
                                    "Revisar que el sistema fuente esté enviando (Elyctis / servicios)",
                                    "Validar API key / rate-limit / errores 401-429",
                                    "Revisar Fluent Bit / collector / conectividad a Mongo"
                            ),
                            mergeNonNull(baseFilters, safeMeta("check", "INGESTION_PIPELINE"))
                    ),
                    new AlertOperatorExplainDto.Step(
                            "3) Escalar y dejar monitoreo",
                            List.of(
                                    "Abrir ticket: 'No Data' con hora exacta del corte",
                                    "Añadir verificación: healthcheck + alerta si pasan X minutos sin logs",
                                    "RESOLVE solo cuando vuelva el flujo y se confirme con logs reales"
                            ),
                            mergeNonNull(baseFilters, safeMeta("recommendedAction", "INCIDENT_OR_TICKET"))
                    )
            );

            case "VOLUME_SPIKE" -> List.of(
                    new AlertOperatorExplainDto.Step(
                            "1) Identificar quién generó el pico",
                            List.of(
                                    "Ver topSystems/topEventTypes del bucket",
                                    "Confirmar si era esperado (batch, migración, reintentos)",
                                    "Revisar si hay 1 requestId dominando (loop/retry)"
                            ),
                            mergeNonNull(baseFilters, safeMeta("requestId", topReq))
                    ),
                    new AlertOperatorExplainDto.Step(
                            "2) Buscar duplicidad / loops",
                            List.of(
                                    "Comparar mensajes repetidos",
                                    "Ver si la misma traza (traceId) aparece repetida",
                                    "Revisar integraciones duplicadas o reintentos sin backoff"
                            ),
                            mergeNonNull(baseFilters, safeMeta("check", "DUPLICATION_LOOP"))
                    ),
                    new AlertOperatorExplainDto.Step(
                            "3) Mitigar",
                            List.of(
                                    "Aplicar throttling / rate-limit / dedupe si procede",
                                    "Escalar a equipo dueño del system dominante",
                                    "Monitorear que baje el volumen en la siguiente hora"
                            ),
                            mergeNonNull(baseFilters, safeMeta("recommendedAction", "THROTTLE_OR_FIX"))
                    )
            );

            default -> List.of(
                    new AlertOperatorExplainDto.Step(
                            "1) Revisar evidencia del bucket",
                            List.of("Revisar tops, muestras y correlación por requestId/caseId."),
                            baseFilters
                    ),
                    new AlertOperatorExplainDto.Step(
                            "2) Correlacionar",
                            List.of("Identificar patrón repetido y validar si corresponde a cambio reciente."),
                            baseFilters
                    ),
                    new AlertOperatorExplainDto.Step(
                            "3) Actuar",
                            List.of("Abrir ticket/incidente con evidencia y hacer ACK/RESOLVE según el caso."),
                            baseFilters
                    )
            );
        };
    }

    /** Crea un map ignorando keys inválidas y VALORES NULL (evita NPE de Map.of). */
    private static Map<String, Object> safeMeta(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (!(k instanceof String key)) continue;
            if (v == null) continue;
            m.put(key, v);
        }
        return m;
    }

    /** Merge que NO mete valores null del mapa extra. */
    private static Map<String, Object> mergeNonNull(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (base != null) out.putAll(base);
        if (extra != null) {
            extra.forEach((k, v) -> {
                if (k != null && v != null) out.put(k, v);
            });
        }
        return out;
    }


    // ---------------------------- Evidencia Mongo ----------------------------

    private List<AlertOperatorExplainDto.Sample> fetchSamples(ObjectId tenantId, Instant from, Instant to, ZoneId zone, int limit) {
        Query q = new Query(Criteria.where(F_TENANT).is(tenantId)
                .and(F_TIME).gte(Date.from(from)).lt(Date.from(to)));
        q.with(Sort.by(Sort.Direction.DESC, F_TIME));
        q.limit(Math.min(Math.max(limit, 1), 50));

        // Traemos docs “raw” y mapeamos solo campos clave
        List<Document> docs = mongoTemplate.find(q, Document.class, logCollection);

        List<AlertOperatorExplainDto.Sample> out = new ArrayList<>();
        for (Document d : docs) {
            AlertOperatorExplainDto.Sample s = new AlertOperatorExplainDto.Sample();
            ObjectId id = d.getObjectId("_id");
            s.id = (id != null) ? id.toHexString() : Objects.toString(d.get("id"), null);

            Date et = d.getDate(F_TIME);
            if (et != null) {
                s.eventTime = et.toInstant().toString();
                s.eventTimeLocal = toLocal(et.toInstant(), zone);
            }

            s.system = d.getString(F_SYS);
            s.eventType = d.getString(F_TYPE);
            s.status = d.getString(F_STATUS);
            s.outcome = d.getString(F_OUT);
            s.severity = d.getString(F_SEV);
            s.message = safeSnippet(d.getString(F_MSG));

            Document corr = d.get("correlation", Document.class);
            if (corr != null) {
                s.requestId = corr.getString("requestId");
                s.traceId = corr.getString("traceId");
            }

            s.caseId = d.getString(F_CASE);

            Document actor = d.get("actor", Document.class);
            if (actor != null) {
                s.actorId = actor.getString("id");
                s.actorUsername = actor.getString("username");
                s.actorFullName = actor.getString("fullName");
            }

            Document loc = d.get("location", Document.class);
            if (loc != null) {
                s.locationId = loc.getString("id");
                s.locationName = loc.getString("name");
            }

            out.add(s);
        }
        return out;
    }

    private Map<String, Long> aggregateTopCounts(
            ObjectId tenantId, Instant from, Instant to, String fieldPath, int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        Aggregation agg = newAggregation(
                match(Criteria.where(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(fieldPath).exists(true).ne(null)
                ),
                group(fieldPath).count().as("c"),
                sort(Sort.by(Sort.Direction.DESC, "c")),
                limit(safeLimit),
                project()
                        .and("_id").as("k")      // <- aquí es "_id"
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


    private static class Window {
        final Instant from;
        final Instant to;
        Window(Instant from, Instant to) {
            this.from = from;
            this.to = to;
        }
    }

    private Window resolveExplainWindow(AiAlertRecord rec) {
        Instant base = rec.getBucketStart() != null ? rec.getBucketStart() : rec.getWindowFrom();
        if (base == null) return new Window(null, null);

        Instant end;
        if ("hourly".equalsIgnoreCase(rec.getGranularity())) {
            end = base.plus(1, ChronoUnit.HOURS);
        } else if ("daily".equalsIgnoreCase(rec.getGranularity())) {
            end = base.plus(1, ChronoUnit.DAYS);
        } else {
            end = rec.getWindowTo();
        }

        if (end == null) end = base.plus(1, ChronoUnit.HOURS);
        return new Window(base, end);
    }

    private List<AlertOperatorExplainDto.TopItem> aggregateTopItems(
            ObjectId tenantId, Instant from, Instant to, String fieldPath, int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        Aggregation agg = newAggregation(
                match(Criteria.where(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(fieldPath).exists(true).ne(null)
                ),
                group(fieldPath).count().as("c"),
                sort(Sort.by(Sort.Direction.DESC, "c")),
                limit(safeLimit),
                project()
                        .and("_id").as("name")     // <- OJO: aquí es "_id", NO "_id.k"
                        .and("c").as("count")
                        .andExclude("_id")
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        List<AlertOperatorExplainDto.TopItem> out = new ArrayList<>();
        for (Document r : rows) {
            out.add(new AlertOperatorExplainDto.TopItem(
                    Objects.toString(r.get("name"), ""),
                    toLong(r.get("count"))
            ));
        }
        return out;
    }

    private List<AlertOperatorExplainDto.TopError> aggregateTopErrors(
            ObjectId tenantId, Instant from, Instant to, int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        Aggregation agg = newAggregation(
                match(Criteria.where(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(F_IS_ERROR).is(true)
                        .and(F_MSG_KEY).exists(true).ne(null).ne("")
                ),

                group(F_MSG_KEY).count().as("c"),
                sort(Sort.by(Sort.Direction.DESC, "c")),
                limit(safeLimit),

                project()
                        .and("_id").as("key")
                        .and("c").as("count")
                        .andExclude("_id")
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        List<AlertOperatorExplainDto.TopError> out = new ArrayList<>();

        for (Document r : rows) {
            out.add(new AlertOperatorExplainDto.TopError(
                    Objects.toString(r.get("key"), ""),
                    toLong(r.get("count"))
            ));
        }
        return out;
    }


    // ---------------------------- helpers ----------------------------
    private ZoneId safeZone(String tz) {
        try {
            if (!StringUtils.hasText(tz)) return ZoneId.of("America/Mexico_City");
            return ZoneId.of(tz.trim());
        } catch (Exception e) {
            return ZoneId.of("America/Mexico_City");
        }
    }

    private String toLocal(Instant instant, ZoneId zone) {
        return (instant == null) ? null : instant.atZone(zone).format(ISO_OFFSET);
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private String safeSnippet(String s) {
        if (s == null) return null;
        String x = s.replaceAll("\\s+", " ").trim();
        return x.length() <= 220 ? x : x.substring(0, 220) + "...";
    }

    private Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (a != null) out.putAll(a);
        if (b != null) out.putAll(b);
        return out;
    }

    private String normalizeUpper(String v, String def) {
        if (v == null) return def;
        String s = v.trim();

        return s.isEmpty() ? def : s.toUpperCase(Locale.ROOT);
    }

    private static String firstTopName(List<AlertOperatorExplainDto.TopItem> items) {
        if (items == null || items.isEmpty() || items.get(0) == null) return null;
        return items.get(0).name;
    }

    private static String firstKey(Map<String, Long> m) {
        if (m == null || m.isEmpty()) return null;
        return m.keySet().iterator().next();
    }


}
