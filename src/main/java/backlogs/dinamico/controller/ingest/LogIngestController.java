package backlogs.dinamico.controller.ingest;

import backlogs.dinamico.api.ingest.dto.LogIngestRequest;
import backlogs.dinamico.service.ingest.LogIngestService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class LogIngestController {

  private final LogIngestService service;

  @PostMapping("/logs")
  public ResponseEntity<?> ingest(@RequestBody LogIngestRequest body) {
    ObjectId id = service.ingest(body);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(
        java.util.Map.of("status", "accepted", "id", id.toHexString())
    );
  }
}
