package backlogs.dinamico.service.logs;

import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.infra.ws.DashboardNotifier;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.repository.log.LogEventRepository;
import backlogs.dinamico.security.auth.ScopeGuard;
import backlogs.dinamico.tenant.TenantContext;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica el filtrado por {@code deviceId} de {@link LogEventService#search}:
 * coincidencia EXACTA contra {@code meta.deviceId}, {@code actor.id} o
 * {@code actor.fullName} (las identidades legítimas de un mismo equipo),
 * sin mezclar dispositivos distintos ni otros tenants/sistemas, con paginación
 * y conteo coherentes sobre la consulta filtrada.
 *
 * MongoTemplate se simula con un mini-evaluador de queries sobre documentos en
 * memoria, de modo que las pruebas ejercitan el Criteria real que construye el
 * servicio (igualdad exacta, $or, tenant y sistema obligatorios).
 */
class LogEventServiceDeviceFilterTest {

    private static final String COLLECTION = "log_events";
    private static final String SYSTEM = "WINDOWS_SERVER_PRINCIPAL";
    private static final ObjectId TENANT = new ObjectId();
    private static final ObjectId OTHER_TENANT = new ObjectId();

    private MongoTemplate mongoTemplate;
    private LogEventService service;
    private Authentication auth;

    // Semilla: misma máquina reporta actor.fullName="WIN-MHRBTECSCC7" (identidad
    // del registro/marcador) y meta.deviceId="WIN-MHRBTECSCC7-8" (hostname +
    // último octeto de IP, lo genera el agente); actor.id repite el valor de
    // meta.deviceId, como se observó en producción. evC1 es la trampa de
    // prefijo: misma raíz "-9" pero es otra máquina (identidades distintas).
    // evF1 sólo lleva actor.id (sin meta ni fullName): agentes que no envían meta.
    private final List<LogEvent> seed = List.of(
            event(TENANT, SYSTEM, "WIN-MHRBTECSCC7", "WIN-MHRBTECSCC7-8", Instant.parse("2026-10-14T10:00:00Z")),
            event(TENANT, SYSTEM, "WIN-MHRBTECSCC7", "WIN-MHRBTECSCC7-8", Instant.parse("2026-10-14T11:00:00Z")),
            event(TENANT, SYSTEM, "WIN-SRVDB01", "WIN-SRVDB01-12", Instant.parse("2026-10-14T09:00:00Z")),
            event(TENANT, SYSTEM, "WIN-SRV02", "WIN-MHRBTECSCC7-9", Instant.parse("2026-10-14T08:00:00Z")),
            event(OTHER_TENANT, SYSTEM, "WIN-MHRBTECSCC7", "WIN-MHRBTECSCC7-8", Instant.parse("2026-10-14T10:30:00Z")),
            event(TENANT, "OTRO_SISTEMA", "WIN-MHRBTECSCC7", "WIN-MHRBTECSCC7-8", Instant.parse("2026-10-14T10:15:00Z")),
            actorIdOnlyEvent(TENANT, SYSTEM, "WIN-ONLY-ID-77", Instant.parse("2026-10-14T07:00:00Z"))
    );

    @BeforeEach
    void setUp() {
        LogEventRepository repo = mock(LogEventRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        ScopeGuard scopeGuard = mock(ScopeGuard.class);
        DashboardNotifier dashboardNotifier = mock(DashboardNotifier.class);
        EventTypeNormalizer eventTypeNormalizer = mock(EventTypeNormalizer.class);
        DeviceRegistryService deviceRegistryService = mock(DeviceRegistryService.class);

        service = new LogEventService(repo, mongoTemplate, scopeGuard,
                dashboardNotifier, eventTypeNormalizer, deviceRegistryService);

        AuthUser user = new AuthUser(new ObjectId(), "admin@test.com", "Admin",
                TENANT, List.of("ORG_ADMIN"), List.of(), true, List.of());
        auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);

        TenantContext.setTenantId(TENANT);

        // find: filtra la semilla con el Criteria real, ordena eventTime DESC
        // y aplica skip/limit de la Query.
        when(mongoTemplate.find(any(Query.class), eq(LogEvent.class), eq(COLLECTION)))
                .thenAnswer(inv -> {
                    Query q = inv.getArgument(0);
                    return seed.stream()
                            .filter(e -> matches(q.getQueryObject(), toDoc(e)))
                            .sorted(Comparator.comparing(LogEvent::getEventTime).reversed())
                            .skip(Math.max(0, q.getSkip()))
                            .limit(q.getLimit() > 0 ? q.getLimit() : Long.MAX_VALUE)
                            .toList();
                });

        // count: evalúa el mismo Criteria sobre toda la semilla (sin paginación)
        when(mongoTemplate.count(any(Query.class), eq(COLLECTION)))
                .thenAnswer(inv -> {
                    Query q = inv.getArgument(0);
                    return seed.stream()
                            .filter(e -> matches(q.getQueryObject(), toDoc(e)))
                            .count();
                });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── 1. El marcador del registro (actor.fullName) resuelve sus eventos ─────

    @Test
    void markerDeviceIdMatchesItsOwnEventsOnly() {
        Page<LogEvent> page = search("WIN-MHRBTECSCC7");

        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
        assertTrue(page.getContent().stream().allMatch(e ->
                "WIN-MHRBTECSCC7-8".equals(meta(e, "deviceId"))));
    }

    // ── 2. La identidad de los eventos (meta.deviceId) también resuelve ───────

    @Test
    void metaDeviceIdMatchesSameEventsAndRespectsTenant() {
        Page<LogEvent> page = search("WIN-MHRBTECSCC7-8");

        // Incluye los 2 eventos del tenant; excluye el del OTRO tenant aunque
        // tenga el mismo meta.deviceId.
        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(e -> TENANT.equals(e.getTenantId())));
    }

    // ── 3. Dispositivos distintos no se mezclan (ni por prefijo) ─────────────

    @Test
    void distinctDevicesDoNotMix() {
        Page<LogEvent> srvDb = search("WIN-SRVDB01");
        assertEquals(1, srvDb.getTotalElements());
        assertEquals("WIN-SRVDB01-12", meta(srvDb.getContent().get(0), "deviceId"));

        // Trampa de prefijo: "WIN-MHRBTECSCC7-9" es otra máquina (WIN-SRV02).
        // Consultar "WIN-MHRBTECSCC7" no debe devolver sus eventos.
        Page<LogEvent> marker = search("WIN-MHRBTECSCC7");
        assertTrue(marker.getContent().stream().noneMatch(e ->
                "WIN-SRV02".equals(e.getActor() != null ? e.getActor().getFullName() : null)));
        assertTrue(marker.getContent().stream().noneMatch(e ->
                "WIN-MHRBTECSCC7-9".equals(meta(e, "deviceId"))));
    }

    // ── 4. Identificador inexistente → cero resultados ───────────────────────

    @Test
    void unknownDeviceIdReturnsZero() {
        Page<LogEvent> page = search("NO-EXISTE-99999");
        assertEquals(0, page.getTotalElements());
        assertTrue(page.getContent().isEmpty());
    }

    // ── 5. Paginación preserva el filtro y el total es el filtrado ───────────

    @Test
    void paginationPreservesFilterAndTotalIsFiltered() {
        Page<LogEvent> p0 = service.search(auth, SYSTEM, null, null, null, null, null,
                null, null, null, null, null, null, null, "WIN-MHRBTECSCC7", null, 0, 1, null, "DESC");
        assertEquals(1, p0.getContent().size());
        assertEquals(2, p0.getTotalElements());
        assertEquals(2, p0.getTotalPages());

        Page<LogEvent> p1 = service.search(auth, SYSTEM, null, null, null, null, null,
                null, null, null, null, null, null, null, "WIN-MHRBTECSCC7", null, 1, 1, null, "DESC");
        assertEquals(1, p1.getContent().size());
        assertEquals(2, p1.getTotalElements());
        assertNotEquals(p0.getContent().get(0).getId(), p1.getContent().get(0).getId());
    }

    // ── 6. Tenant y sistema siguen siendo obligatorios ───────────────────────

    @Test
    void tenantAndSystemFiltersRemainMandatory() {
        // Sin deviceId: devuelve todo el sistema del tenant (5 eventos de la
        // semilla), sin cruzar fronteras de tenant ni de sistema.
        Page<LogEvent> all = search(null);
        assertEquals(5, all.getTotalElements());
        assertTrue(all.getContent().stream().allMatch(e ->
                TENANT.equals(e.getTenantId()) && SYSTEM.equals(e.getSystem())));

        // deviceId de otra máquina + mismo tenant → no mezcla con "OTRO_SISTEMA"
        Page<LogEvent> page = search("WIN-MHRBTECSCC7");
        assertTrue(page.getContent().stream().noneMatch(e -> "OTRO_SISTEMA".equals(e.getSystem())));
    }

    // ── 7. El filtro usa igualdad exacta, nunca regex/prefijo ────────────────

    @Test
    void deviceFilterUsesExactEqualityNotRegex() {
        org.mockito.ArgumentCaptor<Query> captor = org.mockito.ArgumentCaptor.forClass(Query.class);

        search("WIN-MHRBTECSCC7");

        org.mockito.Mockito.verify(mongoTemplate).find(captor.capture(), eq(LogEvent.class), eq(COLLECTION));
        String json = captor.getValue().getQueryObject().toJson();

        assertTrue(json.contains("$or"), "se espera $or entre las identidades del dispositivo: " + json);
        assertTrue(json.contains("meta.deviceId"), json);
        assertTrue(json.contains("actor.fullName"), json);
        assertTrue(json.contains("actor.id"), json);
        assertFalse(json.contains("$regex"), "el filtro de dispositivo no debe usar regex: " + json);
        assertTrue(json.contains("tenant_id"), "tenant obligatorio: " + json);
        assertTrue(json.contains("system"), "system obligatorio: " + json);
    }

    // ── 8. Escenario reportado desde producción (equivalencia de identidades) ─

    @Test
    void markerQueryReturnsEventsWhoseMetaDeviceIdCarriesIpSuffix() {
        // Reproduce el caso real: deviceId=WIN-MHRBTECSCC7 (marcador del registro)
        // devuelve eventos cuyo meta.deviceId/actor.id son WIN-MHRBTECSCC7-8,
        // porque ambas identidades pertenecen al MISMO equipo (el agente añade
        // el último octeto de la IP a meta/actor.id; el registro usa el hostname).
        Page<LogEvent> byMarker = search("WIN-MHRBTECSCC7");
        assertEquals(2, byMarker.getTotalElements());
        assertTrue(byMarker.getContent().stream().allMatch(e ->
                "WIN-MHRBTECSCC7-8".equals(meta(e, "deviceId"))
                        && "WIN-MHRBTECSCC7-8".equals(e.getActor().getId())
                        && "WIN-MHRBTECSCC7".equals(e.getActor().getFullName())));

        // Y viceversa: consultar con la identidad del evento devuelve los mismos.
        Page<LogEvent> byMeta = search("WIN-MHRBTECSCC7-8");
        assertEquals(2, byMeta.getTotalElements());

        // Evento con SOLO actor.id (sin meta ni fullName) también resuelve.
        Page<LogEvent> byActorIdOnly = search("WIN-ONLY-ID-77");
        assertEquals(1, byActorIdOnly.getTotalElements());
        assertNull(byActorIdOnly.getContent().get(0).getMeta());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Page<LogEvent> search(String deviceId) {
        return service.search(auth, SYSTEM, null, null, null, null, null, null, null, null,
                null, null, null, null, deviceId, null, 0, 100, null, "DESC");
    }

    private static LogEvent event(ObjectId tenant, String system, String host, String metaDeviceId, Instant time) {
        return LogEvent.builder()
                .id(new ObjectId())
                .tenantId(tenant)
                .system(system)
                .caseId("CAS-1")
                .eventTime(time)
                .eventType("SERVICE_STATE_CHANGE")
                // Como el evento real observado en producción: actor.id repite el
                // meta.deviceId y actor.fullName lleva el hostname del marcador.
                .actor(new LogEvent.Actor(metaDeviceId, "SERVER", null, host))
                .meta(Map.of(
                        "sourceApp", "datalogs-agent",
                        "deviceId", metaDeviceId,
                        "ip", "192.168.100.8"))
                .build();
    }

    private static LogEvent actorIdOnlyEvent(ObjectId tenant, String system, String actorId, Instant time) {
        return LogEvent.builder()
                .id(new ObjectId())
                .tenantId(tenant)
                .system(system)
                .caseId("CAS-1")
                .eventTime(time)
                .eventType("SERVICE_STATE_CHANGE")
                .actor(new LogEvent.Actor(actorId, "SERVER", null, null))
                .build();
    }

    private static String meta(LogEvent e, String key) {
        Object v = e.getMeta() != null ? e.getMeta().get(key) : null;
        return v != null ? v.toString() : null;
    }

    /** Convierte el evento al documento tal como se persiste en Mongo. */
    private static Document toDoc(LogEvent e) {
        Document doc = new Document();
        doc.put("tenant_id", e.getTenantId());
        doc.put("system", e.getSystem());
        doc.put("caseId", e.getCaseId());
        doc.put("eventTime", e.getEventTime());
        doc.put("eventType", e.getEventType());
        doc.put("eventCode", e.getEventCode());
        doc.put("status", e.getStatus());
        doc.put("outcome", e.getOutcome());
        doc.put("severity", e.getSeverity());
        doc.put("message", e.getMessage());
        if (e.getActor() != null) {
            Document actor = new Document();
            actor.put("id", e.getActor().getId());
            actor.put("type", e.getActor().getType());
            actor.put("username", e.getActor().getUsername());
            actor.put("fullName", e.getActor().getFullName());
            doc.put("actor", actor);
        }
        if (e.getMeta() != null) doc.put("meta", new Document(e.getMeta()));
        return doc;
    }

    /** Mini-evaluador: aplica un query object de Mongo sobre un documento. */
    private static boolean matches(Document query, Document doc) {
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            String key = entry.getKey();
            Object condition = entry.getValue();
            if ("$and".equals(key)) {
                List<?> list = (List<?>) condition;
                for (Object sub : list) {
                    if (!matches((Document) sub, doc)) return false;
                }
            } else if ("$or".equals(key)) {
                List<?> list = (List<?>) condition;
                boolean any = false;
                for (Object sub : list) {
                    if (matches((Document) sub, doc)) { any = true; break; }
                }
                if (!any) return false;
            } else if (!fieldMatches(key, condition, doc)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean fieldMatches(String path, Object condition, Document doc) {
        Object value = valueAt(doc, path);
        if (condition instanceof Document cond) {
            for (Map.Entry<String, Object> op : cond.entrySet()) {
                switch (op.getKey()) {
                    case "$eq" -> { if (!Objects.equals(value, op.getValue())) return false; }
                    case "$in" -> { if (!((List<?>) op.getValue()).contains(value)) return false; }
                    case "$gte" -> { if (value == null || ((Comparable) value).compareTo(op.getValue()) < 0) return false; }
                    case "$lt"  -> { if (value == null || ((Comparable) value).compareTo(op.getValue()) >= 0) return false; }
                    case "$regex" -> {
                        int flags = cond.containsKey("$options") && op.getValue() != null
                                && "i".equals(cond.get("$options")) ? Pattern.CASE_INSENSITIVE : 0;
                        if (value == null || !Pattern.compile(op.getValue().toString(), flags)
                                .matcher(value.toString()).matches()) return false;
                    }
                    default -> throw new IllegalArgumentException("Operador no soportado en test: " + op.getKey());
                }
            }
            return true;
        }
        return Objects.equals(value, condition);
    }

    private static Object valueAt(Document doc, String path) {
        Object current = doc;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Document d)) return null;
            current = d.get(part);
        }
        return current;
    }
}
