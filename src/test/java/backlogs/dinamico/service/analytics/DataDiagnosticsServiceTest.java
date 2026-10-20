package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.intelligence.CoverageReportDto;
import backlogs.dinamico.api.dto.intelligence.MetricState;
import backlogs.dinamico.tenant.TenantContext;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link DataDiagnosticsService} con MongoTemplate mockeado
 * (sin contexto Spring). El orden de llamadas al mock es determinista:
 * 0=identidad, 1=sesión/auth, 2=temporal, 3=min/max global.
 */
class DataDiagnosticsServiceTest {

    private MongoTemplate mongoTemplate;
    private IndexOperations indexOperations;
    private DataDiagnosticsService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        service = new DataDiagnosticsService(mongoTemplate);
        TenantContext.set(TenantContext.Ctx.builder()
                .tenantId(new ObjectId())
                .build());

        // Verificación de índices: por defecto el índice hint existe.
        indexOperations = mock(IndexOperations.class);
        IndexInfo hintIndex = new IndexInfo(List.of(), "idx_tenant_system_time_v2", false, false, "");
        when(indexOperations.getIndexInfo()).thenReturn(List.of(hintIndex));
        when(mongoTemplate.indexOps("log_events")).thenReturn(indexOperations);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void zeroEventsReturnsNotReadyWithoutExceptions() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> new AggregationResults<>(List.of(), new Document()));

        CoverageReportDto report = service.coverage("TRUSTVALUE", 30);

        assertNotNull(report);
        assertEquals("NOT_READY", report.getReadiness());
        assertFalse(report.getReadinessNotes().isEmpty());
        assertEquals(0L, report.getTemporal().getTotalEventsInWindow());
        assertTrue(report.getTemporal().getDailyVolume().isEmpty());
        assertNull(report.getTemporal().getFirstEventTime());
        assertNull(report.getTemporal().getLastEventTime());

        assertEquals(MetricState.NOT_AVAILABLE, report.getActorCoverage().getActorIdCoveragePct().getState());
        assertNull(report.getActorCoverage().getActorIdCoveragePct().getValue());
        assertEquals(MetricState.NOT_AVAILABLE, report.getSessionAuth().getSessionIdCoveragePct().getState());
        assertEquals(MetricState.NOT_AVAILABLE, report.getTemporal().getIngestionLagAvgMin().getState());
        assertEquals(0L, report.getIdentityConflicts().getConflictedUsernames());
        assertTrue(report.getIdentityConflicts().getConflicts().isEmpty());

        // sin eventos no hay calidad de datos que medir: conteos en cero
        assertEquals(0L, report.getDataQuality().getEventsWithoutEventTimeInWindow());
        assertEquals(0L, report.getDataQuality().getFutureEventsInWindow());
        assertEquals(0L, report.getDataQuality().getPotentialDuplicateGroups());
        assertEquals(0L, report.getDataQuality().getPotentialDuplicateExtraEvents());
    }

    @Test
    void normalizesSystemToUpperAndComputesCoveragePercentages() {
        AtomicInteger call = new AtomicInteger(0);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> {
                    int c = call.getAndIncrement();
                    if (c == 0) { // identidad
                        Document presence = new Document()
                                .append("total", 100L)
                                .append("actorIdPresent", 80L)
                                .append("actorIdSet", List.of("a1", "a2"))
                                .append("usernamePresent", 90L)
                                .append("usernameSet", List.of("u1"))
                                .append("fullNamePresent", 40L)
                                .append("fullNameSet", List.of())
                                .append("typePresent", 95L)
                                .append("typeSet", List.of("USER", "SERVICE"));
                        Document root = new Document()
                                .append("presence", List.of(presence))
                                .append("typeDist", List.of(
                                        new Document("_id", "USER").append("count", 70L),
                                        new Document("_id", "SERVICE").append("count", 25L),
                                        new Document("_id", "UNKNOWN").append("count", 5L)))
                                .append("conflictsTop", List.of(new Document()
                                        .append("username", "juan.perez")
                                        .append("distinctActorIds", 2)
                                        .append("sampleActorIds", List.of("a1", "a2"))))
                                .append("conflictsTotal", List.of(new Document("total", 1L)));
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 1) { // sesión/auth
                        Document summary = new Document()
                                .append("total", 100L)
                                .append("sessionIdPresent", 60L)
                                .append("sessionIdSet", List.of("s1", "s2", "s3"))
                                .append("authResultPresent", 30L);
                        Document root = new Document()
                                .append("summary", List.of(summary))
                                .append("authResultDist", List.of(
                                        new Document("_id", "SUCCESS").append("count", 25L)))
                                .append("authEventTypes", List.of(
                                        new Document("_id", "LOGIN").append("count", 20L),
                                        new Document("_id", "LOGOUT").append("count", 10L)));
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 2) { // temporal
                        Document windowStats = new Document("total", 100L);
                        Document lagSummary = new Document()
                                .append("count", 100L)
                                .append("avg", 5.5)
                                .append("min", 0.5);
                        Document bucket1 = new Document("_id", new Document("min", 0.0).append("max", 5.0))
                                .append("count", 90L);
                        Document bucket2 = new Document("_id", new Document("min", 5.0).append("max", 60.0))
                                .append("count", 10L);
                        Document root = new Document()
                                .append("windowStats", List.of(windowStats))
                                .append("daily", List.of(
                                        new Document("_id", "2026-10-01").append("count", 60L),
                                        new Document("_id", "2026-10-02").append("count", 40L)))
                                .append("lagSummary", List.of(lagSummary))
                                .append("lagBuckets", List.of(bucket1, bucket2));
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 3) { // global bounds
                        Date first = new Date(1600000000000L);
                        Date last = new Date(1700000000000L);
                        return new AggregationResults<>(
                                List.of(new Document("first", first).append("last", last)),
                                new Document());
                    }
                    throw new IllegalStateException("unexpected aggregation call");
                });

        CoverageReportDto report = service.coverage("trustvalue", 30);

        // normalización a UPPER
        assertEquals("TRUSTVALUE", report.getSystem());

        // porcentajes de cobertura
        assertEquals(80.0, report.getActorCoverage().getActorIdCoveragePct().getValue());
        assertEquals(2, report.getActorCoverage().getActorIdDistinct().getValue());
        assertEquals(90.0, report.getActorCoverage().getUsernameCoveragePct().getValue());
        assertEquals(40.0, report.getActorCoverage().getFullNameCoveragePct().getValue());
        assertEquals(95.0, report.getActorCoverage().getTypeCoveragePct().getValue());

        // identidad preliminar: HUMAN=70 (USER), SERVICE=25, DEVICE=0, UNKNOWN=5
        assertEquals(70L, report.getActorCoverage().getIdentityCounts().get("HUMAN"));
        assertEquals(25L, report.getActorCoverage().getIdentityCounts().get("SERVICE"));
        assertEquals(0L, report.getActorCoverage().getIdentityCounts().get("DEVICE"));
        assertEquals(5L, report.getActorCoverage().getIdentityCounts().get("UNKNOWN"));

        // conflictos
        assertEquals(1L, report.getIdentityConflicts().getConflictedUsernames());
        assertEquals(1, report.getIdentityConflicts().getConflicts().size());
        assertEquals("juan.perez", report.getIdentityConflicts().getConflicts().get(0).getUsername());
        assertEquals(2, report.getIdentityConflicts().getConflicts().get(0).getDistinctActorIds());
        assertEquals(List.of("a1", "a2"),
                report.getIdentityConflicts().getConflicts().get(0).getSampleActorIds());

        // sesiones/auth
        assertEquals(60.0, report.getSessionAuth().getSessionIdCoveragePct().getValue());
        assertEquals(3, report.getSessionAuth().getSessionIdDistinct().getValue());
        assertEquals(30.0, report.getSessionAuth().getAuthResultCoveragePct().getValue());
        assertEquals("LOGIN", report.getSessionAuth().getAuthRelatedEventTypes().get(0).getValue());

        // temporal
        assertEquals(100L, report.getTemporal().getTotalEventsInWindow());
        assertEquals(2, report.getTemporal().getDailyVolume().size());
        assertEquals(1600000000000L, report.getTemporal().getFirstEventTime().toEpochMilli());
        assertEquals(1700000000000L, report.getTemporal().getLastEventTime().toEpochMilli());
        assertEquals(MetricState.DEGRADED, report.getTemporal().getIngestionLagAvgMin().getState());
        assertEquals(5.5, report.getTemporal().getIngestionLagAvgMin().getValue());
        assertEquals(0.5, report.getTemporal().getIngestionLagMinMin().getValue());
        // p95 aproximado por buckets: umbral 95 → segundo bucket → max 60.0
        assertEquals(60.0, report.getTemporal().getIngestionLagP95Min().getValue());

        // readiness: actor.id 80% y type ausente 5% → sin caveats
        assertEquals("READY_FOR_BACKFILL", report.getReadiness());
    }

    @Test
    void lowActorCoverageYieldsCaveats() {
        AtomicInteger call = new AtomicInteger(0);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> {
                    int c = call.getAndIncrement();
                    if (c == 0) {
                        Document presence = new Document()
                                .append("total", 100L)
                                .append("actorIdPresent", 40L)
                                .append("actorIdSet", List.of("a1"))
                                .append("usernamePresent", 40L)
                                .append("usernameSet", List.of())
                                .append("fullNamePresent", 10L)
                                .append("fullNameSet", List.of())
                                .append("typePresent", 75L)
                                .append("typeSet", List.of("USER"));
                        Document root = new Document()
                                .append("presence", List.of(presence))
                                .append("typeDist", List.of(new Document("_id", "USER").append("count", 75L)))
                                .append("conflictsTop", List.of())
                                .append("conflictsTotal", List.of());
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 1) {
                        Document summary = new Document()
                                .append("total", 100L)
                                .append("sessionIdPresent", 0L)
                                .append("sessionIdSet", List.of())
                                .append("authResultPresent", 0L);
                        return new AggregationResults<>(List.of(
                                new Document().append("summary", List.of(summary))
                                        .append("authResultDist", List.of())
                                        .append("authEventTypes", List.of())),
                                new Document());
                    }
                    if (c == 2) {
                        Document root = new Document()
                                .append("windowStats", List.of(new Document("total", 100L)))
                                .append("daily", List.of())
                                .append("lagSummary", List.of())
                                .append("lagBuckets", List.of());
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 3) {
                        return new AggregationResults<>(List.of(), new Document());
                    }
                    throw new IllegalStateException("unexpected aggregation call");
                });

        CoverageReportDto report = service.coverage("TRUSTVALUE", 30);

        // actor.id 40% (<50) pero >=20, type ausente 25% (>20) → READY_WITH_CAVEATS
        assertEquals("READY_WITH_CAVEATS", report.getReadiness());
        assertTrue(report.getReadinessNotes().stream()
                .anyMatch(n -> n.startsWith("actor_id_coverage_below_50")));
        assertTrue(report.getReadinessNotes().stream()
                .anyMatch(n -> n.startsWith("actor_type_missing_above_20")));
    }

    // ── Índices requeridos ───────────────────────────────────────────────────

    @Test
    void missingRequiredIndexRejectsQuerySafelyWithExplanation() {
        when(indexOperations.getIndexInfo()).thenReturn(List.of()); // sin índice hint

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.coverage("TRUSTVALUE", 30));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("required_index_missing"), ex.getReason());
        assertTrue(ex.getReason().contains("idx_tenant_system_time_v2"), ex.getReason());

        // rechazo seguro: ninguna agregación llegó a ejecutarse
        verify(mongoTemplate, never()).aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class));
    }

    @Test
    void indexVerificationFailureRejectsQuerySafely() {
        when(indexOperations.getIndexInfo()).thenThrow(new RuntimeException("listIndexes timeout"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.coverage("TRUSTVALUE", 30));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("index_verification_failed"), ex.getReason());
        verify(mongoTemplate, never()).aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class));
    }

    // ── Aislamiento por tenant y sistema ─────────────────────────────────────

    @Test
    void scopesEveryAggregationByTenantAndSystem() {
        ObjectId tenantId = TenantContext.getTenantId();
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> new AggregationResults<>(List.of(), new Document()));

        service.coverage("trustvalue", 30);

        ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate, times(4)).aggregate(captor.capture(), eq("log_events"), eq(Document.class));

        List<Aggregation> aggregations = captor.getAllValues();
        for (int i = 0; i < 3; i++) { // identidad, sesión/auth, temporal: ventana acotada
            Document stage = aggregations.get(i).toPipeline(Aggregation.DEFAULT_CONTEXT).get(0);
            Document match = (Document) stage.get("$match");
            assertNotNull(match, "la primera etapa debe ser $match: " + stage);
            assertEquals(tenantId, match.get("tenant_id"),
                    "el $match debe fijar el tenant del contexto: " + match);
            assertEquals("TRUSTVALUE", match.get("system"),
                    "el $match debe fijar el sistema normalizado a UPPER: " + match);
            Object eventTime = match.get("eventTime");
            assertTrue(eventTime instanceof Document, "la ventana temporal debe acotar eventTime: " + match);
        }

        // límites globales: mismo aislamiento tenant+system, pero SIN acotar eventTime
        Document stage = aggregations.get(3).toPipeline(Aggregation.DEFAULT_CONTEXT).get(0);
        Document match = (Document) stage.get("$match");
        assertNotNull(match, "la primera etapa debe ser $match: " + stage);
        assertEquals(tenantId, match.get("tenant_id"));
        assertEquals("TRUSTVALUE", match.get("system"));
        assertFalse(match.containsKey("eventTime"),
                "los límites globales deben abarcar todo el historial: " + match);
    }

    @Test
    void rejectsNullSystemAndClampsWindowDays() {
        assertThrows(ResponseStatusException.class, () -> service.coverage(null, 30));
        assertThrows(ResponseStatusException.class, () -> service.coverage("  ", 30));

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> new AggregationResults<>(List.of(), new Document()));

        assertEquals(90, service.coverage("TRUSTVALUE", 500).getWindowDays());
        assertEquals(1, service.coverage("TRUSTVALUE", -5).getWindowDays());
        assertEquals(30, service.coverage("TRUSTVALUE", null).getWindowDays());
    }

    // ── Calidad de datos: legacy sin eventTime, futuros, duplicados ─────────

    @Test
    void temporalQualityCountsLegacyFutureEventsAndDuplicates() {
        AtomicInteger call = new AtomicInteger(0);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> {
                    int c = call.getAndIncrement();
                    if (c == 0) { // identidad
                        Document root = new Document()
                                .append("presence", List.of(new Document()
                                        .append("total", 10L)
                                        .append("actorIdPresent", 10L)
                                        .append("actorIdSet", List.of("a1"))
                                        .append("usernamePresent", 10L)
                                        .append("usernameSet", List.of("u1"))
                                        .append("fullNamePresent", 10L)
                                        .append("fullNameSet", List.of())
                                        .append("typePresent", 10L)
                                        .append("typeSet", List.of("USER"))))
                                .append("typeDist", List.of(new Document("_id", "USER").append("count", 10L)))
                                .append("conflictsTop", List.of())
                                .append("conflictsTotal", List.of());
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 1) { // sesión/auth
                        Document root = new Document()
                                .append("summary", List.of(new Document()
                                        .append("total", 10L)
                                        .append("sessionIdPresent", 10L)
                                        .append("sessionIdSet", List.of("s1"))
                                        .append("authResultPresent", 10L)))
                                .append("authResultDist", List.of())
                                .append("authEventTypes", List.of());
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 2) { // temporal con calidad de datos
                        Document root = new Document()
                                .append("windowStats", List.of(new Document("total", 10L)))
                                .append("daily", List.of())
                                .append("lagSummary", List.of())
                                .append("lagBuckets", List.of())
                                .append("noEventTime", List.of(new Document("n", 2L)))
                                .append("futures", List.of(new Document("n", 1L)))
                                .append("dupGroups", List.of(new Document("n", 3L)))
                                .append("dupExtra", List.of(new Document("extra", 5L)));
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 3) {
                        return new AggregationResults<>(List.of(), new Document());
                    }
                    throw new IllegalStateException("unexpected aggregation call");
                });

        CoverageReportDto report = service.coverage("TRUSTVALUE", 30);

        assertEquals(2L, report.getDataQuality().getEventsWithoutEventTimeInWindow());
        assertEquals(1L, report.getDataQuality().getFutureEventsInWindow());
        assertEquals(3L, report.getDataQuality().getPotentialDuplicateGroups());
        assertEquals(5L, report.getDataQuality().getPotentialDuplicateExtraEvents());

        assertTrue(report.getReadinessNotes().stream()
                .anyMatch(n -> n.startsWith("events_without_eventtime")));
        assertTrue(report.getReadinessNotes().stream()
                .anyMatch(n -> n.startsWith("future_events_detected")));
        assertTrue(report.getReadinessNotes().stream()
                .anyMatch(n -> n.startsWith("potential_duplicates")));
    }

    @Test
    void temporalPipelineBoundsLagToNonNegativeValues() {
        // Captura la agregación temporal y verifica que el lag excluye eventos futuros
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> new AggregationResults<>(List.of(), new Document()));

        service.coverage("TRUSTVALUE", 30);

        ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate, times(4)).aggregate(captor.capture(), eq("log_events"), eq(Document.class));

        Aggregation temporal = captor.getAllValues().get(2);
        String pipeline = temporal.toPipeline(Aggregation.DEFAULT_CONTEXT).toString();
        assertTrue(pipeline.contains("lagMinutes=Document{{$gte=0}}"),
                "el lag debe acotarse a valores no negativos (excluye eventTime futuro): " + pipeline);
        assertTrue(pipeline.contains("dupGroups"), "falta facet de duplicados: " + pipeline);
        assertTrue(pipeline.contains("noEventTime"), "falta facet de docs sin eventTime: " + pipeline);
        assertTrue(pipeline.contains("futures"), "falta facet de eventos futuros: " + pipeline);
    }

    // ── Tolerancia a fallos y límites de ejecución ───────────────────────────

    @Test
    void partialAggregationFailureStillReturnsDegradedReport() {
        AtomicInteger call = new AtomicInteger(0);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> {
                    if (call.getAndIncrement() == 2) { // temporal falla (p. ej. timeout)
                        throw new RuntimeException("aggregation exceeded maxTime");
                    }
                    return new AggregationResults<>(List.of(), new Document());
                });

        CoverageReportDto report = assertDoesNotThrow(() -> service.coverage("TRUSTVALUE", 30));

        // sin agregación temporal no hay total verificable → conservador
        assertEquals("NOT_READY", report.getReadiness());
        assertEquals(0L, report.getTemporal().getTotalEventsInWindow());
        assertEquals(MetricState.NOT_AVAILABLE, report.getTemporal().getIngestionLagAvgMin().getState());
    }

    @Test
    void aggregationsAreBoundedByTimeHintAndDiskUse() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> new AggregationResults<>(List.of(), new Document()));

        service.coverage("TRUSTVALUE", 30);

        ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate, times(4)).aggregate(captor.capture(), eq("log_events"), eq(Document.class));

        for (Aggregation aggregation : captor.getAllValues()) {
            AggregationOptions options = aggregation.getOptions();
            assertEquals(Duration.ofMillis(3000), options.getMaxTime());
            assertEquals("idx_tenant_system_time_v2", options.getHintObject().orElseThrow(),
                    "debe existir hint de índice");
            assertTrue(options.isAllowDiskUse());
            assertEquals(Integer.valueOf(1000), options.getCursorBatchSize());
        }
    }

    // ── Respuesta sin datos personales innecesarios ─────────────────────────

    @Test
    void reportDoesNotLeakUnnecessaryPersonalData() {
        AtomicInteger call = new AtomicInteger(0);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("log_events"), eq(Document.class)))
                .thenAnswer(invocation -> {
                    int c = call.getAndIncrement();
                    if (c == 0) { // identidad: fullName real y conflicto plantados
                        Document root = new Document()
                                .append("presence", List.of(new Document()
                                        .append("total", 100L)
                                        .append("actorIdPresent", 100L)
                                        .append("actorIdSet", List.of("id-1", "id-2", "id-3"))
                                        .append("usernamePresent", 100L)
                                        .append("usernameSet", List.of("juan.perez"))
                                        .append("fullNamePresent", 100L)
                                        .append("fullNameSet", List.of("Juan Pérez García"))
                                        .append("typePresent", 100L)
                                        .append("typeSet", List.of("USER"))))
                                .append("typeDist", List.of(new Document("_id", "USER").append("count", 100L)))
                                .append("conflictsTop", List.of(new Document()
                                        .append("username", "juan.perez")
                                        .append("distinctActorIds", 3)
                                        .append("sampleActorIds", List.of("id-1", "id-2", "id-3"))))
                                .append("conflictsTotal", List.of(new Document("total", 1L)));
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 1) { // sesión/auth: sessionId real plantado
                        Document root = new Document()
                                .append("summary", List.of(new Document()
                                        .append("total", 100L)
                                        .append("sessionIdPresent", 100L)
                                        .append("sessionIdSet", List.of("SESS-XYZ-123"))
                                        .append("authResultPresent", 100L)))
                                .append("authResultDist", List.of())
                                .append("authEventTypes", List.of());
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 2) { // temporal: total para que la cobertura de actor no quede vacía
                        Document root = new Document()
                                .append("windowStats", List.of(new Document("total", 100L)))
                                .append("daily", List.of())
                                .append("lagSummary", List.of())
                                .append("lagBuckets", List.of());
                        return new AggregationResults<>(List.of(root), new Document());
                    }
                    if (c == 3) {
                        return new AggregationResults<>(List.of(), new Document());
                    }
                    throw new IllegalStateException("unexpected aggregation call");
                });

        CoverageReportDto report = service.coverage("TRUSTVALUE", 30);
        String serialized = report.toString();

        // nunca se devuelven valores crudos de fullName ni sessionId, solo conteos
        assertFalse(serialized.contains("Juan Pérez García"),
                "el reporte no debe incluir fullName: " + serialized);
        assertFalse(serialized.contains("SESS-XYZ-123"),
                "el reporte no debe incluir sessionId: " + serialized);

        // el único identificador textual expuesto es el diagnóstico de conflictos,
        // acotado (top 10) y con muestras de actor.id limitadas a 3
        assertTrue(report.getIdentityConflicts().getConflicts().size() <= 10);
        for (var conflict : report.getIdentityConflicts().getConflicts()) {
            assertTrue(conflict.getSampleActorIds().size() <= 3);
        }
        // actor.type / authResult solo aparecen como distribuciones de conteo
        assertEquals(100L, report.getActorCoverage().getIdentityCounts().get("HUMAN"));
    }
}
