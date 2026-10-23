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
 * Verifica el filtrado por identidad de {@link LogEventService#search}:
 * - {@code username} → igualdad exacta sobre {@code actor.username} (contrato
 *   que consume el módulo Analítica de Usuarios del frontend).
 * - {@code actorId} → igualdad exacta sobre {@code actor.id} (contrato previo).
 *
 * Casos: username existente/inexistente, actorId existente, aislamiento por
 * tenant y por sistema, paginación sobre el resultado filtrado y consulta
 * general sin filtro de identidad (no debe romperse).
 *
 * MongoTemplate se simula con un mini-evaluador de queries sobre documentos en
 * memoria, de modo que las pruebas ejercitan el Criteria real del servicio.
 */
class LogEventServiceUsernameFilterTest {

    private static final String COLLECTION = "log_events";
    private static final String SYSTEM = "TRUSTVALUE";
    private static final ObjectId TENANT = new ObjectId();
    private static final ObjectId OTHER_TENANT = new ObjectId();

    private MongoTemplate mongoTemplate;
    private LogEventService service;
    private Authentication auth;

    // esfe: 3 eventos en el tenant/sistema (varias horas para probar orden).
    // yael: 2 eventos en el mismo tenant/sistema → la consulta por esfe NO
    //       debe devolver ninguno (es la mezcla reportada en producción).
    // esfe en OTRO tenant y en OTRO sistema → el aislamiento debe excluirlos.
    private final List<LogEvent> seed = List.of(
            userEvent(TENANT, SYSTEM, "u-esfe", "esfe", "Esfe User",  Instant.parse("2026-10-20T10:00:00Z")),
            userEvent(TENANT, SYSTEM, "u-esfe", "esfe", "Esfe User",  Instant.parse("2026-10-20T11:00:00Z")),
            userEvent(TENANT, SYSTEM, "u-esfe", "esfe", "Esfe User",  Instant.parse("2026-10-20T12:00:00Z")),
            userEvent(TENANT, SYSTEM, "u-yael", "yael", "Yael Lopez", Instant.parse("2026-10-20T10:30:00Z")),
            userEvent(TENANT, SYSTEM, "u-yael", "yael", "Yael Lopez", Instant.parse("2026-10-20T11:30:00Z")),
            userEvent(OTHER_TENANT, SYSTEM, "u-esfe", "esfe", "Esfe User", Instant.parse("2026-10-20T10:15:00Z")),
            userEvent(TENANT, "OTRO_SISTEMA", "u-esfe", "esfe", "Esfe User", Instant.parse("2026-10-20T10:20:00Z"))
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

    // ── 1. Username existente → solo sus eventos ─────────────────────────────

    @Test
    void existingUsernameReturnsOnlyItsEvents() {
        Page<LogEvent> page = searchByUsername("esfe");

        assertEquals(3, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
        assertTrue(page.getContent().stream().allMatch(e ->
                e.getActor() != null && "esfe".equals(e.getActor().getUsername())));
        // Ningún evento de yael (la mezcla reportada).
        assertTrue(page.getContent().stream().noneMatch(e ->
                "yael".equals(e.getActor() != null ? e.getActor().getUsername() : null)));
    }

    // ── 2. Username inexistente → página vacía con totales en cero ───────────

    @Test
    void unknownUsernameReturnsEmptyPage() {
        Page<LogEvent> page = searchByUsername("no-existe");

        assertEquals(0, page.getTotalElements());
        assertEquals(0, page.getTotalPages());
        assertTrue(page.getContent().isEmpty());
    }

    // ── 3. Actor ID existente → contrato previo sigue funcionando ────────────

    @Test
    void existingActorIdReturnsOnlyItsEvents() {
        Page<LogEvent> page = service.search(auth, SYSTEM, null, null, null, null, null,
                null, null, null, "u-yael", null, null, null, null, null, 0, 100, null, "DESC");

        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(e ->
                e.getActor() != null && "u-yael".equals(e.getActor().getId())
                        && "yael".equals(e.getActor().getUsername())));
    }

    // ── 4. Tenant incorrecto → los eventos de esfe de otro tenant no aparecen ─

    @Test
    void otherTenantEventsAreNeverReturned() {
        Page<LogEvent> page = searchByUsername("esfe");

        assertTrue(page.getContent().stream().allMatch(e -> TENANT.equals(e.getTenantId())));
        // En toda la semilla hay 4 eventos "esfe"; solo 3 son de ESTE tenant.
        assertEquals(3, page.getTotalElements());
    }

    // ── 5. Sistema incorrecto → no se cruzan sistemas ────────────────────────

    @Test
    void otherSystemEventsAreNeverReturned() {
        Page<LogEvent> page = searchByUsername("esfe");

        assertTrue(page.getContent().stream().allMatch(e -> SYSTEM.equals(e.getSystem())));
        assertEquals(3, page.getTotalElements());
    }

    // ── 6. Paginación sobre el resultado filtrado ────────────────────────────

    @Test
    void paginationIsAppliedAfterFiltering() {
        Page<LogEvent> p0 = service.search(auth, SYSTEM, null, null, null, null, null,
                null, null, null, null, "esfe", null, null, null, null, 0, 2, null, "DESC");
        assertEquals(2, p0.getContent().size());
        assertEquals(3, p0.getTotalElements());
        assertEquals(2, p0.getTotalPages());

        Page<LogEvent> p1 = service.search(auth, SYSTEM, null, null, null, null, null,
                null, null, null, null, "esfe", null, null, null, null, 1, 2, null, "DESC");
        assertEquals(1, p1.getContent().size());
        assertEquals(3, p1.getTotalElements());
        assertNotEquals(p0.getContent().get(0).getId(), p1.getContent().get(0).getId());
        assertTrue(p1.getContent().stream().allMatch(e ->
                "esfe".equals(e.getActor().getUsername())));
    }

    // ── 7. Múltiples usuarios: consulta general sin username no se rompe ─────

    @Test
    void generalQueryWithoutIdentityFilterStillWorks() {
        Page<LogEvent> page = service.search(auth, SYSTEM, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, 0, 100, null, "DESC");

        // 5 eventos del tenant+ sistema: 3 de esfe + 2 de yael.
        assertEquals(5, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(e -> "esfe".equals(e.getActor().getUsername())));
        assertTrue(page.getContent().stream().anyMatch(e -> "yael".equals(e.getActor().getUsername())));
    }

    // ── 8. El Criteria real usa actor.username con igualdad exacta ───────────

    @Test
    void usernameFilterUsesExactEqualityOnActorUsername() {
        org.mockito.ArgumentCaptor<Query> captor = org.mockito.ArgumentCaptor.forClass(Query.class);

        searchByUsername("esfe");

        org.mockito.Mockito.verify(mongoTemplate).find(captor.capture(), eq(LogEvent.class), eq(COLLECTION));
        String json = captor.getValue().getQueryObject().toJson();

        assertTrue(json.contains("actor.username"), json);
        assertFalse(json.contains("$regex"), "el filtro de username no debe usar regex: " + json);
        assertTrue(json.contains("tenant_id"), "tenant obligatorio: " + json);
        assertTrue(json.contains("system"), "system obligatorio: " + json);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Page<LogEvent> searchByUsername(String username) {
        return service.search(auth, SYSTEM, null, null, null, null, null, null, null, null,
                null, username, null, null, null, null, 0, 100, null, "DESC");
    }

    private static LogEvent userEvent(ObjectId tenant, String system,
                                      String actorId, String username, String fullName,
                                      Instant time) {
        return LogEvent.builder()
                .id(new ObjectId())
                .tenantId(tenant)
                .system(system)
                .caseId("CAS-1")
                .eventTime(time)
                .eventType("USER_ACTION")
                .actor(new LogEvent.Actor(actorId, "USER", username, fullName))
                .build();
    }

    /** Convierte el evento al documento tal como se persiste en Mongo. */
    private static Document toDoc(LogEvent e) {
        Document doc = new Document();
        doc.put("tenant_id", e.getTenantId());
        doc.put("system", e.getSystem());
        doc.put("caseId", e.getCaseId());
        doc.put("eventTime", e.getEventTime());
        doc.put("eventType", e.getEventType());
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
