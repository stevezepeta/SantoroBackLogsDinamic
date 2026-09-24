package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.analytics.ErrorExplanation;
import backlogs.dinamico.api.dto.analytics.ErrorExplanationResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.repository.log.LogEventRepository;
import backlogs.dinamico.security.auth.ScopeGuard;
import backlogs.dinamico.service.ai.AiLlmPrettyService;
import backlogs.dinamico.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationalErrorExplainerServiceTest {

    private LogEventRepository logEventRepository;
    private AiLlmPrettyService llmPrettyService;
    private ScopeGuard scopeGuard;
    private OperationalErrorExplainerService service;

    @BeforeEach
    void setUp() {
        logEventRepository = mock(LogEventRepository.class);
        llmPrettyService = mock(AiLlmPrettyService.class);
        scopeGuard = mock(ScopeGuard.class);
        service = new OperationalErrorExplainerService(logEventRepository, llmPrettyService, scopeGuard, new ObjectMapper());

        TenantContext.set(TenantContext.Ctx.builder()
                .tenantId(new ObjectId())
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsLlmExplanationWhenAvailable() throws Exception {
        ObjectId logId = new ObjectId();
        LogEvent event = LogEvent.builder()
                .id(logId)
                .system("TRUSTVALUE")
                .caseId("CAS-001")
                .eventCode("ENVIAR_EVIDENCIAS")
                .status("REJECTED")
                .outcome("ERROR")
                .message("Timeout uploading photos")
                .isError(true)
                .build();

        when(logEventRepository.findByIdAndTenantId(logId, TenantContext.requireTenantId()))
                .thenReturn(Optional.of(event));
        doNothing().when(scopeGuard).requireSystemAccess(any(), any());

        String llmJson = """
                {
                  "summary": "No se pudieron enviar las fotos.",
                  "likelyCause": "Pérdida de conexión.",
                  "businessImpact": "La jornada quedó sin evidencia.",
                  "recommendedAction": "Reintentar con conexión estable.",
                  "confidence": "ALTA"
                }
                """;
        when(llmPrettyService.explainErrorForSupervisor(any())).thenReturn(llmJson);

        ErrorExplanationResponse response = service.explain(buildAuth(), logId);

        assertEquals("LLM", response.getSource());
        assertNotNull(response.getExplanation());
        assertEquals("No se pudieron enviar las fotos.", response.getExplanation().getSummary());
        assertEquals("ALTA", response.getExplanation().getConfidence());
    }

    @Test
    void fallsBackToHeuristicWhenLlmFails() {
        ObjectId logId = new ObjectId();
        LogEvent event = LogEvent.builder()
                .id(logId)
                .system("TRUSTVALUE")
                .caseId("CAS-002")
                .eventCode("INICIO_SESION")
                .status("REJECTED")
                .message("Invalid credentials")
                .reason(new LogEvent.ReasonInfo("AUTH_FAILURE", "Credenciales inválidas"))
                .build();

        when(logEventRepository.findByIdAndTenantId(logId, TenantContext.requireTenantId()))
                .thenReturn(Optional.of(event));
        doNothing().when(scopeGuard).requireSystemAccess(any(), any());
        when(llmPrettyService.explainErrorForSupervisor(any())).thenThrow(new RuntimeException("OpenAI timeout"));

        ErrorExplanationResponse response = service.explain(buildAuth(), logId);

        assertEquals("HEURISTIC", response.getSource());
        assertNotNull(response.getExplanation());
        assertTrue(response.getExplanation().getSummary().contains("Invalid credentials"));
    }

    @Test
    void returnsNotApplicableForSuccessfulEvent() {
        ObjectId logId = new ObjectId();
        LogEvent event = LogEvent.builder()
                .id(logId)
                .system("TRUSTVALUE")
                .caseId("CAS-003")
                .eventCode("ENVIAR_EVIDENCIAS")
                .status("SUCCESS")
                .outcome("COMPLETED")
                .message("Evidencias recibidas")
                .build();

        when(logEventRepository.findByIdAndTenantId(logId, TenantContext.requireTenantId()))
                .thenReturn(Optional.of(event));
        doNothing().when(scopeGuard).requireSystemAccess(any(), any());

        ErrorExplanationResponse response = service.explain(buildAuth(), logId);

        assertEquals("NOT_APPLICABLE", response.getSource());
        assertNull(response.getExplanation());
    }

    private Authentication buildAuth() {
        AuthUser user = new AuthUser(
                new ObjectId(),
                "supervisor@test.com",
                "Supervisor",
                TenantContext.requireTenantId(),
                List.of("SUPERVISOR"),
                List.of("PERM_LOG_READ"),
                true,
                List.of(),
                null,
                List.of()
        );
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
