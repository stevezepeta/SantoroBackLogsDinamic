package backlogs.dinamico.service.ai;

import backlogs.dinamico.api.dto.DailyManagerPrettyDto;
import backlogs.dinamico.service.ai.dto.AiTicketDraftDto;
import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiLlmPrettyService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DailyManagerPrettyDto prettyDailyManager(DailyManagerSummaryDto mgr,
                                                    List<AiTicketDraftDto> drafts) {
        // Payload compacto (controla costo + reduce alucinaciones)
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("managerSummary", mgr);
        payload.put("drafts", drafts);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return fallback(mgr, "No se pudo serializar payload para LLM.");
        }

        String system = """
                Eres un asistente de ingeniería. SOLO puedes usar los datos proporcionados por el usuario.
                NO inventes métricas, NO inventes hechos, NO inventes causas con certeza.
                Si falta evidencia, dilo en 'caveats' y baja 'confidence'.
                Responde ÚNICAMENTE con JSON válido. Sin markdown. Sin texto extra.
                """;

        String user = """
                Produce un JSON con esta forma EXACTA:

                {
                  "base": null,
                  "pretty": {
                    "executiveNarrative": "string",
                    "executiveBullets": ["...max 5..."],
                    "draftEnhancements": [
                      {
                        "title": "exact draft.title",
                        "oneLineSummary": "string",
                        "likelyCauses": ["..."],
                        "impact": "string",
                        "nextSteps": ["..."],
                        "confidence": 0.0,
                        "caveats": ["..."]
                      }
                    ]
                  }
                }

                Reglas:
                - executiveBullets max 5.
                - Por cada draft recibido, regresa exactamente 1 enhancement con el MISMO title.
                - confidence:
                  * 0.2 si NO hay samples (en description o meta no hay evidencia)
                  * 0.5 si solo hay conteos/tops
                  * 0.8 si hay muestras claras
                - Si mgr.status = CRIT o WARN, prioriza acciones más urgentes.

                Datos (JSON):
                %s
                """.formatted(payloadJson);

        String raw;
        try {
            ChatClient chatClient = chatClientBuilder.build();
            raw = chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .content();
        } catch (Exception e) {
            e.printStackTrace();
            return fallback(mgr, "LLM no disponible: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        try {
            DailyManagerPrettyDto out = objectMapper.readValue(raw, DailyManagerPrettyDto.class);
            // El LLM devuelve base=null; la llenamos aquí para no duplicar texto/costo
            out.base = mgr;

            // Guardrails simples
            if (out.pretty == null) out.pretty = new DailyManagerPrettyDto.PrettyManager();
            if (out.pretty.executiveBullets != null && out.pretty.executiveBullets.size() > 5) {
                out.pretty.executiveBullets = out.pretty.executiveBullets.subList(0, 5);
            }
            return out;
        } catch (Exception e) {
            // Si el modelo devolvió texto no-JSON, fallback seguro.
            return fallback(mgr, "LLM devolvió respuesta no parseable como JSON.");
        }
    }

    private DailyManagerPrettyDto fallback(DailyManagerSummaryDto mgr, String reason) {
        DailyManagerPrettyDto out = new DailyManagerPrettyDto();
        out.base = mgr;
        out.pretty = new DailyManagerPrettyDto.PrettyManager();
        out.pretty.executiveNarrative = reason;
        out.pretty.executiveBullets = (mgr != null) ? mgr.executiveSummary : List.of();
        out.pretty.draftEnhancements = List.of();
        return out;
    }

}
