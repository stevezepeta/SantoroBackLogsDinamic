package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analiza los mensajes del eventType dominante del periodo
 * y genera un resumen inteligente via Claude API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaDeepAnalysisService {

    private static final String CLAUDE_API_URL =
            "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final int    MAX_MESSAGES_SAMPLE = 50;

    @Value("${eva.openai.api-key:}")
    private String openAiApiKey;

    private final MongoTemplate mongoTemplate;

    // ── DTO resultado ─────────────────────────────────────────────────────────

    public record DeepAnalysisResult(
            String dominantEventType,  // "AUTH_LOGIN"
            long   dominantCount,      // 13410
            String aiSummary,          // resumen generado por IA
            String aiSuggestions       // sugerencias específicas generadas por IA
    ) {}

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Encuentra el eventType con más logs en el periodo,
     * toma una muestra de mensajes y genera análisis con IA.
     */
    public DeepAnalysisResult analyze(
            ObjectId tenantId,
            String   system,
            Instant  from,
            Instant  to,
            DailyManagerSummaryDto summary
    ) {
        try {
            // 1. Encontrar el eventType dominante
            DominantEvent dominant = findDominantEventType(tenantId, system, from, to);
            if (dominant == null) return null;

            // 2. Obtener muestra de mensajes de ese eventType
            List<String> messages = fetchMessageSample(
                    tenantId, system, dominant.eventType(), from, to);
            if (messages.isEmpty()) return null;

            // 3. Llamar a Claude API
            String[] analysis = callOpenAiApi(dominant.eventType(), dominant.count(), messages, summary);

            return new DeepAnalysisResult(
                    dominant.eventType(),
                    dominant.count(),
                    analysis[0],   // resumen
                    analysis[1]    // sugerencias
            );
        } catch (Exception e) {
            log.error("[EvaDeepAnalysis] Error generando análisis: {}", e.getMessage());
            return null;
        }
    }

    // ── Paso 1: encontrar eventType dominante ─────────────────────────────────

    private record DominantEvent(String eventType, long count) {}

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
                "log_events",
                org.bson.Document.class
        );

        org.bson.Document doc = results.getUniqueMappedResult();
        if (doc == null) return null;

        String eventType = doc.getString("_id");
        long   count     = ((Number) doc.get("count")).longValue();
        return new DominantEvent(eventType, count);
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

        // Proyectar solo message y meta.ip para reducir payload
        ProjectionOperation project = Aggregation.project("message", "meta", "outcome", "actor");
        MatchOperation      match   = Aggregation.match(
                new Criteria().andOperator(cs.toArray(new Criteria[0])));
        LimitOperation      limit   = Aggregation.limit(MAX_MESSAGES_SAMPLE);

        AggregationResults<org.bson.Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(match, project, limit),
                "log_events",
                org.bson.Document.class
        );

        return results.getMappedResults().stream()
                .map(doc -> {
                    String msg = doc.getString("message");
                    // Agregar IP de origen si está disponible en meta
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

    // ── Paso 3: llamar a Claude API ───────────────────────────────────────────
    private String[] callOpenAiApi(
            String eventType,
            long   count,
            List<String> messages,
            DailyManagerSummaryDto summary   // ← NUEVO
    ) throws Exception {

        if (!org.springframework.util.StringUtils.hasText(openAiApiKey)) {
            log.warn("[EvaDeepAnalysis] OpenAI API key no configurada — análisis IA omitido");
            return new String[]{ null, null };
        }

        // ── Métricas agregadas del periodo ────────────────────────────────────
        long   totalEventos  = summary != null ? summary.total : count;
        double errorRate     = summary != null ? summary.errorRate * 100 : 0;
        long   totalFailure  = 0;
        long   totalSuccess  = 0;

        if (summary != null && summary.topOutcomeRange != null) {
            for (var item : summary.topOutcomeRange) {
                if ("FAILURE".equals(item.name))  totalFailure = item.count;
                if ("SUCCESS".equals(item.name))  totalSuccess = item.count;
            }
        }

        double pctEvento = totalEventos > 0
                ? ((double) count / totalEventos * 100) : 0;

        String metricsBlock = String.format("""
            MÉTRICAS DEL PERIODO:
            - Total eventos del sistema: %d
            - Error rate general: %.2f%%
            - Evento dominante: %s (%d ocurrencias = %.1f%% del total)
            - Outcomes globales → FAILURE: %d | SUCCESS: %d
            - Status del sistema: %s
            """,
                totalEventos, errorRate,
                eventType, count, pctEvento,
                totalFailure, totalSuccess,
                summary != null ? summary.status : "UNKNOWN"
        );

        // ── Muestra de mensajes ───────────────────────────────────────────────
        String messagesText = messages.stream()
                .limit(MAX_MESSAGES_SAMPLE)
                .map(m -> "- " + truncate(m, 300))
                .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
            Eres un analista experto en seguridad y operaciones IT especializado en Windows Server.
            
            %s
            
            MUESTRA DE MENSAJES DEL EVENTO DOMINANTE (%d de %d):
            %s
            
            Con base en las métricas y los mensajes anteriores, responde en el siguiente formato JSON exacto:
            {
              "resumen": "párrafo de 3-4 oraciones explicando: cuál es el problema principal, qué lo está causando, si hay IPs/usuarios/patrones que se repiten, y qué tan grave es la situación con datos concretos",
              "sugerencias": "exactamente 5 acciones concretas separadas por salto de línea \\n, cada una comenzando con un número (1. 2. 3. etc), con comandos o pasos específicos si aplica. NO uses markdown ni asteriscos."
            }
            
            Reglas:
            - Usa los datos reales de las métricas (IPs, cuentas, error rate).
            - Si detectas una IP o usuario que se repite, menciónalo explícitamente.
            - Las sugerencias deben ser ACCIONABLES e INMEDIATAS, no genéricas.
            - Responde SOLO el JSON válido, sin texto extra, sin markdown.
            """,
                metricsBlock,
                messages.size(), count,
                messagesText
        );

        String requestBody = String.format("""
            {
              "model": "gpt-4o-mini",
              "temperature": 0.2,
              "messages": [
                {
                  "role": "system",
                  "content": "Eres Eva, analista de seguridad IT. Respondes siempre en JSON válido sin markdown."
                },
                {
                  "role": "user",
                  "content": %s
                }
              ]
            }
            """,
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(userPrompt)
        );

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
            log.error("[EvaDeepAnalysis] OpenAI error {}: {}", response.statusCode(), response.body());
            return new String[]{ null, null };
        }

        return parseOpenAiResponse(response.body());
    }

    private String[] parseOpenAiResponse(String responseBody) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseBody);

            // OpenAI: choices[0].message.content
            String text = root.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            // Limpiar posibles backticks de markdown
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();

            com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(text);
            String resumen     = json.path("resumen").asText("");
            String sugerencias = json.path("sugerencias").asText("");

            return new String[]{ resumen, sugerencias };

        } catch (Exception e) {
            log.error("[EvaDeepAnalysis] Error parseando respuesta de OpenAI: {}", e.getMessage());
            return new String[]{ null, null };
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}