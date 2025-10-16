package backlogs.dinamico.controller.ingest;

import backlogs.dinamico.api.ingest.dto.LogIngestRequest;
import backlogs.dinamico.service.ingest.LogIngestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ingest/logs")
@RequiredArgsConstructor
public class LogIngestController {

  private final LogIngestService service;
  /** Deja que Spring Boot inyecte el ObjectMapper global (ya trae JavaTimeModule). */
  private final ObjectMapper mapper;

  @PostMapping(consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-ndjson", "*/*" })
  public ResponseEntity<?> ingest(@RequestBody byte[] bodyRaw) {
    String body = new String(bodyRaw, StandardCharsets.UTF_8).trim();
    if (body.isEmpty()) {
      return badRequest("empty body");
    }

    try {
      // 1) Parsear a lista de Map (independiente del formato de entrada)
      List<Map<String, Object>> records = parseToRecords(body);

      // 2) Normalizar claves (snake_case -> camelCase puntuales)
      records.replaceAll(this::normalizeKeys);

      // 3) Convertir a DTO y persistir
      List<ObjectId> ids = new ArrayList<>(records.size());
      for (Map<String, Object> rec : records) {
        LogIngestRequest req = mapper.convertValue(rec, LogIngestRequest.class);
        ObjectId id = service.ingest(req);   // tu servicio actual guarda de a uno
        ids.add(id);
      }

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("accepted", ids.size());
      payload.put("ids", ids.stream().map(ObjectId::toHexString).collect(Collectors.toList()));
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(payload);

    } catch (JsonProcessingException e) {
      return badRequest("Body JSON inválido o tipos incorrectos");
    } catch (Exception e) {
      Map<String, Object> err = Map.of(
          "error", "internal_error",
          "message", e.getMessage()
      );
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
  }

  // ----------------- Helpers -----------------

  /** Acepta objeto, array o NDJSON y devuelve lista de Map por evento. */
  private List<Map<String, Object>> parseToRecords(@NonNull String body) throws JsonProcessingException {
    List<Map<String, Object>> out = new ArrayList<>();

    if (body.startsWith("[")) {
      // JSON array
      ArrayNode arr = (ArrayNode) mapper.readTree(body);
      for (var node : arr) {
        out.add(mapper.convertValue(node, new TypeReference<Map<String, Object>>() {}));
      }
    } else if (body.startsWith("{")) {
      // Objeto simple
      out.add(mapper.readValue(body, new TypeReference<Map<String, Object>>() {}));
    } else {
      // NDJSON: un objeto JSON por línea
      String[] lines = body.split("\\r?\\n");
      for (String line : lines) {
        String ln = line.trim();
        if (ln.isEmpty()) continue;
        out.add(mapper.readValue(ln, new TypeReference<Map<String, Object>>() {}));
      }
    }
    return out;
  }

  /**
   * Normaliza claves puntuales para el DTO:
   * - event_at -> eventAt (Fluent Bit Json_Date_Key)
   * - severity a mayúsculas si viene en minúsculas
   */
  private Map<String, Object> normalizeKeys(Map<String, Object> m) {
    if (m == null) return Map.of();

    Map<String, Object> out = new LinkedHashMap<>(m);

    // event_at -> eventAt
    if (out.containsKey("event_at") && !out.containsKey("eventAt")) {
      out.put("eventAt", out.get("event_at"));
    }

    // Por si vienen valores en minúsculas
    Object sev = out.get("severity");
    if (sev instanceof String s && !s.isBlank()) {
      out.put("severity", s.toUpperCase(Locale.ROOT));
    }

    // Sin fallar si hay campos extra (el mapper está configurado por Spring para ignorarlos)
    return out;
  }

  private static ResponseEntity<Map<String, String>> badRequest(String message) {
    Map<String, String> err = new LinkedHashMap<>();
    err.put("error", "invalid_json");
    err.put("message", message);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
  }
}
