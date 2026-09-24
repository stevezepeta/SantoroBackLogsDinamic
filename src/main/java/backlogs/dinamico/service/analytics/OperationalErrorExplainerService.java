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
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Servicio de explicación de errores en lenguaje natural mediante IA bajo demanda.
 *
 * La generación con LLM es lazy: nunca se ejecuta durante la carga inicial de listas
 * ni en streaming de WebSockets. Solo se invoca desde el endpoint dedicado
 * POST /api/analytics/explain-error/{logId}.
 */
@Service
@RequiredArgsConstructor
public class OperationalErrorExplainerService {

    private final LogEventRepository logEventRepository;
    private final AiLlmPrettyService llmPrettyService;
    private final ScopeGuard scopeGuard;
    private final ObjectMapper objectMapper;

    /**
     * Genera una explicación operativa de un error de log.
     *
     * @param auth  autenticación del supervisor
     * @param logId identificador del log
     * @return explicación estructurada con fuente LLM o HEURISTIC
     */
    public ErrorExplanationResponse explain(Authentication auth, ObjectId logId) {
        ObjectId tenantId = TenantContext.requireTenantId();

        LogEvent event = logEventRepository.findByIdAndTenantId(logId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "log_not_found"));

        AuthUser user = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        scopeGuard.requireSystemAccess(user, event.getSystem());

        if (!needsExplanation(event)) {
            return ErrorExplanationResponse.builder()
                    .logId(logId.toHexString())
                    .system(event.getSystem())
                    .caseId(event.getCaseId())
                    .eventCode(event.getEventCode())
                    .explanation(null)
                    .generatedAt(Instant.now())
                    .source("NOT_APPLICABLE")
                    .build();
        }

        String context = buildContext(event);

        try {
            String json = llmPrettyService.explainErrorForSupervisor(context);
            ErrorExplanation explanation = objectMapper.readValue(json, ErrorExplanation.class);

            return ErrorExplanationResponse.builder()
                    .logId(logId.toHexString())
                    .system(event.getSystem())
                    .caseId(event.getCaseId())
                    .eventCode(event.getEventCode())
                    .explanation(sanitize(explanation))
                    .generatedAt(Instant.now())
                    .source("LLM")
                    .build();
        } catch (Exception e) {
            ErrorExplanation fallback = buildHeuristicExplanation(event);
            return ErrorExplanationResponse.builder()
                    .logId(logId.toHexString())
                    .system(event.getSystem())
                    .caseId(event.getCaseId())
                    .eventCode(event.getEventCode())
                    .explanation(fallback)
                    .generatedAt(Instant.now())
                    .source("HEURISTIC")
                    .build();
        }
    }

    /**
     * Determina si un evento requiere explicación de error.
     */
    private boolean needsExplanation(LogEvent event) {
        if (Boolean.TRUE.equals(event.getIsError())) {
            return true;
        }

        String status = event.getStatus() != null ? event.getStatus().trim().toUpperCase() : "";
        String outcome = event.getOutcome() != null ? event.getOutcome().trim().toUpperCase() : "";

        if (status.equals("REJECTED") || status.equals("FAILED") || status.equals("ERROR")
                || outcome.equals("ERROR") || outcome.equals("FAILED")) {
            return true;
        }

        if (event.getHttp() != null && event.getHttp().getStatusCode() != null
                && event.getHttp().getStatusCode() >= 400) {
            return true;
        }

        return false;
    }

    /**
     * Construye un contexto compacto a partir de los datos técnicos del log.
     */
    private String buildContext(LogEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("System: ").append(event.getSystem()).append("\n");
        sb.append("Evento: ").append(event.getEventType());
        if (event.getEventCode() != null) {
            sb.append(" / ").append(event.getEventCode());
        }
        sb.append("\n");

        if (event.getLocation() != null && event.getLocation().getName() != null) {
            sb.append("Sucursal: ").append(event.getLocation().getName()).append("\n");
        }

        sb.append("Estado: ").append(event.getStatus()).append("\n");
        if (event.getOutcome() != null) {
            sb.append("Outcome: ").append(event.getOutcome()).append("\n");
        }
        sb.append("Mensaje técnico: ").append(event.getMessage()).append("\n");

        if (event.getHttp() != null && event.getHttp().getStatusCode() != null) {
            sb.append("HTTP status: ").append(event.getHttp().getStatusCode()).append("\n");
        }

        if (event.getReason() != null) {
            if (event.getReason().getCode() != null) {
                sb.append("Reason code: ").append(event.getReason().getCode()).append("\n");
            }
            if (event.getReason().getDescription() != null) {
                sb.append("Reason description: ").append(event.getReason().getDescription()).append("\n");
            }
        }

        if (event.getPayload() != null && !event.getPayload().isEmpty()) {
            sb.append("Payload relevante: ").append(event.getPayload()).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Explicación de contingencia generada heurísticamente sin llamar al LLM.
     */
    private ErrorExplanation buildHeuristicExplanation(LogEvent event) {
        String message = event.getMessage() != null ? event.getMessage() : "Error técnico";
        String reason = (event.getReason() != null && event.getReason().getDescription() != null)
                ? event.getReason().getDescription()
                : null;

        String summary = "Se detectó un problema: " + message + ".";
        String likelyCause = reason != null
                ? "Causa técnica registrada: " + reason + "."
                : "La causa exacta no está detallada en los datos disponibles.";
        String businessImpact = "Este error puede afectar el registro o seguimiento de la operación del usuario.";
        String recommendedAction = "Revisar los logs relacionados y verificar conectividad/permisos del dispositivo o servicio afectado.";

        return ErrorExplanation.builder()
                .summary(summary)
                .likelyCause(likelyCause)
                .businessImpact(businessImpact)
                .recommendedAction(recommendedAction)
                .confidence("MEDIA")
                .build();
    }

    /**
     * Asegura que la explicación del LLM tenga valores por defecto si faltan campos.
     */
    private ErrorExplanation sanitize(ErrorExplanation explanation) {
        if (explanation == null) {
            return null;
        }
        if (explanation.getSummary() == null) explanation.setSummary("Sin resumen disponible.");
        if (explanation.getLikelyCause() == null) explanation.setLikelyCause("Causa no determinada.");
        if (explanation.getBusinessImpact() == null) explanation.setBusinessImpact("Impacto no determinado.");
        if (explanation.getRecommendedAction() == null) explanation.setRecommendedAction("Consultar logs técnicos.");
        if (explanation.getConfidence() == null) explanation.setConfidence("MEDIA");
        return explanation;
    }
}
