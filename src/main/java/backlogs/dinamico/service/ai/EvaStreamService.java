package backlogs.dinamico.service.ai;

import backlogs.dinamico.api.dto.EvaStreamRequest;
import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class EvaStreamService {

    private final AiDailyManagerService dailyManagerService;

    /**
     * @param req      Parámetros de la petición
     * @param onChunk  Callback — se llama por cada fragmento de texto listo
     */
    public void streamResponse(EvaStreamRequest req, Consumer<String> onChunk) throws Exception {

        String intent = detectIntent(req.getMessage());

        switch (intent) {
            case "daily-summary" -> streamDailySummary(req, onChunk);
            case "metrics-chart" -> streamMetricsInfo(req, onChunk);
            default              -> streamDefault(req.getMessage(), onChunk);
        }
    }

    // ── daily-summary ────────────────────────────────────────────────────────

    private void streamDailySummary(EvaStreamRequest req, Consumer<String> onChunk) throws Exception {
        // tenantId ya viene resuelto del JWT — no hace falta SecurityContext aquí
        ObjectId tenantId = req.getTenantId();
        DailyManagerSummaryDto summary = dailyManagerService.buildManagerSummary(
                tenantId, req.getDays(), req.getTz(), null, null
        );

        // Construye el texto completo
        String fullText = buildSummaryNarrative(summary);

        // Emite en chunks de ~40 chars para simular escritura natural
        emitInChunks(fullText, onChunk, 40);
    }

    // ── metrics-chart ────────────────────────────────────────────────────────

    private void streamMetricsInfo(EvaStreamRequest req, Consumer<String> onChunk) throws Exception {
        String system = req.getSystem() != null ? req.getSystem() : "el sistema seleccionado";

        String text = String.format(
                "Consultando la serie histórica de **%s** con granularidad %s " +
                        "en los últimos %d días...\n\nPrepara la gráfica en el panel derecho.",
                system, req.getGranularity(), req.getDays()
        );

        emitInChunks(text, onChunk, 35);
    }

    // ── fallback ─────────────────────────────────────────────────────────────

    private void streamDefault(String message, Consumer<String> onChunk) throws Exception {
        String text = "No encontré una acción específica para: \"" + message + "\". " +
                "Puedes pedir: **resumen diario**, **alertas abiertas**, " +
                "**tendencias** o una **gráfica** por sistema.";
        emitInChunks(text, onChunk, 30);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Emite el texto en fragmentos del tamaño indicado con una pequeña
     * pausa entre cada uno para simular escritura natural.
     */
    private void emitInChunks(String text, Consumer<String> onChunk, int chunkSize) throws InterruptedException {
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + chunkSize, text.length());

            // Cortar en espacio para no partir palabras
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > i) end = lastSpace + 1;
            }

            onChunk.accept(text.substring(i, end));
            i = end;

            // Pausa corta entre chunks (20-40ms) → sensación de escritura
            Thread.sleep(25);
        }
    }

    private String detectIntent(String message) {
        if (message == null) return "unknown";
        String m = message.toLowerCase();
        if (m.contains("resumen") || m.contains("daily") || m.contains("summary")) return "daily-summary";
        if (m.contains("gráfica") || m.contains("grafica") || m.contains("chart")) return "metrics-chart";
        if (m.contains("alerta"))  return "open-alerts";
        if (m.contains("tendencia")) return "trends";
        return "unknown";
    }

    private String buildSummaryNarrative(DailyManagerSummaryDto s) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Resumen ejecutivo** del periodo ")
                .append(s.fromLocal).append(" → ").append(s.toLocal).append("\n\n");

        sb.append("Se procesaron **").append(s.total).append(" eventos** ");
        sb.append("con una tasa de error de **")
                .append(String.format("%.2f%%", s.errorRate * 100)).append("**.\n\n");

        if (s.executiveSummary != null) {
            for (String bullet : s.executiveSummary) {
                sb.append("- ").append(bullet).append("\n");
            }
        }

        if (s.risks != null && !s.risks.isEmpty()) {
            sb.append("\n**Riesgos detectados:**\n");
            for (String risk : s.risks) sb.append("- ").append(risk).append("\n");
        }

        if (s.actions != null && !s.actions.isEmpty()) {
            sb.append("\n**Acciones recomendadas:**\n");
            for (String action : s.actions) sb.append("- ").append(action).append("\n");
        }

        return sb.toString();
    }

}

