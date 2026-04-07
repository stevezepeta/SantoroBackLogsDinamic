package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaDeepAnalysisService {

    private static final int MAX_MESSAGES_SAMPLE = 50;

    @Value("${eva.openai.api-key:}")
    private String openAiApiKey;

    private final MongoTemplate mongoTemplate;

    // ── DTO resultado ─────────────────────────────────────────────────────────

    public record DeepAnalysisResult(
            String dominantEventType,
            long   dominantCount,
            String aiSummary,
            String aiSuggestions
    ) {}

    private record DominantEvent(String eventType, long count) {}

    // ── Entry points ──────────────────────────────────────────────────────────

    /**
     * Usado desde AiDailyManagerLlmController — recibe DailyManagerSummaryDto
     */
    public DeepAnalysisResult analyze(
            ObjectId tenantId, String system,
            Instant from, Instant to,
            DailyManagerSummaryDto summary
    ) {
        try {
            DominantEvent dominant = findDominantEventType(tenantId, system, from, to);
            if (dominant == null) return null;

            List<String> messages = fetchMessageSample(
                    tenantId, system, dominant.eventType(), from, to);
            if (messages.isEmpty()) return null;

            String metricsBlock = buildMetricsBlockFromSummary(dominant, summary);
            String[] analysis   = callOpenAiApi(dominant.eventType(), dominant.count(),
                    messages, metricsBlock);

            return new DeepAnalysisResult(
                    dominant.eventType(), dominant.count(),
                    analysis[0], analysis[1]);

        } catch (Exception e) {
            log.error("[EvaDeepAnalysis] Error en analyze: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Usado desde AlertSchedulerService — recibe SummaryInsightsDto
     */
    public DeepAnalysisResult analyzeFromInsights(
            ObjectId tenantId, String system,
            Instant from, Instant to,
            SummaryInsightsDto insights
    ) {
        try {
            DominantEvent dominant = findDominantEventType(tenantId, system, from, to);
            if (dominant == null) return null;

            List<String> messages = fetchMessageSample(
                    tenantId, system, dominant.eventType(), from, to);
            if (messages.isEmpty()) return null;

            String metricsBlock = buildMetricsBlockFromInsights(dominant, insights);
            String[] analysis   = callOpenAiApi(dominant.eventType(), dominant.count(),
                    messages, metricsBlock);

            return new DeepAnalysisResult(
                    dominant.eventType(), dominant.count(),
                    analysis[0], analysis[1]);

        } catch (Exception e) {
            log.error("[EvaDeepAnalysis] Error en analyzeFromInsights: {}", e.getMessage());
            return null;
        }
    }

    // ── Builders de metricsBlock ──────────────────────────────────────────────

    private String buildMetricsBlockFromSummary(
            DominantEvent dominant, DailyManagerSummaryDto summary) {

        if (summary == null) {
            return String.format(
                    "METRICAS DEL PERIODO:\n- Evento dominante: %s (%d ocurrencias)\n",
                    dominant.eventType(), dominant.count());
        }

        long totalFailure = 0, totalSuccess = 0;
        if (summary.topOutcomeRange != null) {
            for (var item : summary.topOutcomeRange) {
                if ("FAILURE".equals(item.name)) totalFailure = item.count;
                if ("SUCCESS".equals(item.name)) totalSuccess = item.count;
            }
        }
        double pctEvento = summary.total > 0
                ? ((double) dominant.count() / summary.total * 100) : 0;

        return String.format(
                "METRICAS DEL PERIODO:\n"
                        + "- Total eventos del sistema: %d\n"
                        + "- Error rate general: %.2f%%\n"
                        + "- Evento dominante: %s (%d ocurrencias = %.1f%% del total)\n"
                        + "- Outcomes globales: FAILURE: %d | SUCCESS: %d\n"
                        + "- Status del sistema: %s\n",
                summary.total, summary.errorRate * 100,
                dominant.eventType(), dominant.count(), pctEvento,
                totalFailure, totalSuccess,
                summary.status != null ? summary.status : "UNKNOWN");
    }

    private String buildMetricsBlockFromInsights(
            DominantEvent dominant, SummaryInsightsDto insights) {

        if (insights == null) {
            return String.format(
                    "METRICAS DEL PERIODO:\n- Evento dominante: %s (%d ocurrencias)\n",
                    dominant.eventType(), dominant.count());
        }

        long totalFailure = 0, totalSuccess = 0;
        if (insights.topOutcomeRange != null) {
            for (var item : insights.topOutcomeRange) {
                if ("FAILURE".equals(item.name)) totalFailure = item.count;
                if ("SUCCESS".equals(item.name)) totalSuccess = item.count;
            }
        }
        double pctEvento = insights.total > 0
                ? ((double) dominant.count() / insights.total * 100) : 0;

        return String.format(
                "METRICAS DEL PERIODO (DATOS REALES):\n"
                        + "- Total eventos del sistema: %d\n"
                        + "- Error rate general: %.2f%%\n"
                        + "- Evento dominante: %s (%d ocurrencias = %.1f%% del total)\n"
                        + "- Outcomes globales: FAILURE: %d | SUCCESS: %d\n"
                        + "- Status del sistema: %s\n",
                insights.total, insights.errorRate * 100,
                dominant.eventType(), dominant.count(), pctEvento,
                totalFailure, totalSuccess,
                insights.status != null ? insights.status : "UNKNOWN");
    }

    // ── Paso 1: encontrar eventType dominante ─────────────────────────────────

    private DominantEvent findDominantEventType(
            ObjectId tenantId, String system, Instant from, Instant to) {

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));
        if (system != null) cs.add(Criteria.where("system").is(system));
        if (from   != null) cs.add(Criteria.where("eventTime").gte(from));
        if (to     != null) cs.add(Criteria.where("eventTime").lt(to));

        MatchOperation match = Aggregation.match(
                new Criteria().andOperator(cs.toArray(new Criteria[0])));
        GroupOperation group = Aggregation.group("eventType").count().as("count");
        SortOperation  sort  = Aggregation.sort(
                org.springframework.data.domain.Sort.Direction.DESC, "count");
        LimitOperation limit = Aggregation.limit(1);

        AggregationResults<org.bson.Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(match, group, sort, limit),
                "log_events", org.bson.Document.class);

        org.bson.Document doc = results.getUniqueMappedResult();
        if (doc == null) return null;

        return new DominantEvent(
                doc.getString("_id"),
                ((Number) doc.get("count")).longValue());
    }

    // ── Paso 2: muestra de mensajes ───────────────────────────────────────────

    private List<String> fetchMessageSample(
            ObjectId tenantId, String system, String eventType,
            Instant from, Instant to) {

        List<Criteria> cs = new ArrayList<>();
        cs.add(Criteria.where("tenant_id").is(tenantId));
        cs.add(Criteria.where("eventType").is(eventType));
        if (system != null) cs.add(Criteria.where("system").is(system));
        if (from   != null) cs.add(Criteria.where("eventTime").gte(from));
        if (to     != null) cs.add(Criteria.where("eventTime").lt(to));

        ProjectionOperation project = Aggregation.project("message", "meta", "outcome", "actor");
        MatchOperation      match   = Aggregation.match(
                new Criteria().andOperator(cs.toArray(new Criteria[0])));
        LimitOperation      limit   = Aggregation.limit(MAX_MESSAGES_SAMPLE);

        AggregationResults<org.bson.Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(match, project, limit),
                "log_events", org.bson.Document.class);

        return results.getMappedResults().stream()
                .map(doc -> {
                    String msg = doc.getString("message");
                    Object metaObj = doc.get("meta");
                    if (metaObj instanceof org.bson.Document meta) {
                        String ip = meta.getString("ip");
                        if (ip != null) msg = "[IP:" + ip + "] " + msg;
                    }
                    return msg;
                })
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    // ── Paso 3: llamar a OpenAI API ───────────────────────────────────────────

    private String[] callOpenAiApi(
            String eventType, long count,
            List<String> messages,
            String metricsBlock) throws Exception {

        if (!StringUtils.hasText(openAiApiKey)) {
            log.warn("[EvaDeepAnalysis] OpenAI API key no configurada — analisis IA omitido");
            return new String[]{ null, null };
        }

        String messagesText = messages.stream()
                .limit(MAX_MESSAGES_SAMPLE)
                .map(m -> "- " + truncate(m, 300))
                .collect(Collectors.joining("\n"));

        String userPrompt = String.format(
                "Eres un analista experto en seguridad y operaciones IT especializado en Windows Server.\n\n"
                        + "%s\n\n"
                        + "MUESTRA DE MENSAJES DEL EVENTO DOMINANTE (%d de %d):\n"
                        + "%s\n\n"
                        + "Con base en las metricas y los mensajes anteriores, responde en el siguiente formato JSON exacto:\n"
                        + "{\n"
                        + "  \"resumen\": \"parrafo de 3-4 oraciones explicando: cual es el problema principal, "
                        + "que lo esta causando, si hay IPs/usuarios/patrones que se repiten, "
                        + "y que tan grave es la situacion con datos concretos\",\n"
                        + "  \"sugerencias\": \"exactamente 5 acciones concretas separadas por salto de linea \\n, "
                        + "cada una comenzando con un numero (1. 2. 3. etc), "
                        + "con comandos o pasos especificos si aplica. NO uses markdown ni asteriscos.\"\n"
                        + "}\n\n"
                        + "Reglas:\n"
                        + "- Usa los datos reales de las metricas (IPs, cuentas, error rate).\n"
                        + "- Si detectas una IP o usuario que se repite, menciónalo explicitamente.\n"
                        + "- Las sugerencias deben ser ACCIONABLES e INMEDIATAS, no genericas.\n"
                        + "- Responde SOLO el JSON valido, sin texto extra, sin markdown.\n",
                metricsBlock, messages.size(), count, messagesText);

        String requestBody = String.format(
                "{\n"
                        + "  \"model\": \"gpt-4o-mini\",\n"
                        + "  \"temperature\": 0.2,\n"
                        + "  \"messages\": [\n"
                        + "    { \"role\": \"system\", \"content\": \"Eres Eva, analista de seguridad IT. "
                        + "Respondes siempre en JSON valido sin markdown.\" },\n"
                        + "    { \"role\": \"user\", \"content\": %s }\n"
                        + "  ]\n"
                        + "}",
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(userPrompt));

        HttpClient  client  = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[EvaDeepAnalysis] OpenAI error {}: {}",
                    response.statusCode(), response.body());
            return new String[]{ null, null };
        }

        return parseOpenAiResponse(response.body());
    }

    // ── Parsear respuesta ─────────────────────────────────────────────────────

    private String[] parseOpenAiResponse(String responseBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseBody);

            String text = root.path("choices").get(0)
                    .path("message").path("content").asText();

            text = text.replaceAll("```json", "").replaceAll("```", "").trim();

            com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(text);
            return new String[]{
                    json.path("resumen").asText(""),
                    json.path("sugerencias").asText("")
            };

        } catch (Exception e) {
            log.error("[EvaDeepAnalysis] Error parseando respuesta OpenAI: {}", e.getMessage());
            return new String[]{ null, null };
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}