package backlogs.dinamico.controller.logs;

import backlogs.dinamico.service.ingest.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@CrossOrigin
public class LogQueryController {

  private final LogQueryService service;

  @GetMapping
  public Page<Document> search(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) String severity,
      @RequestParam(required = false) String eventTypeCode,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String sessionId,
      @RequestParam(required = false) String deviceId,
      Pageable pageable
  ) {
    return service.search(from, to, severity, eventTypeCode, q, traceId, sessionId, deviceId, pageable);
  }

  @GetMapping("/{id}")
  public Document getOne(@PathVariable ObjectId id) {
    return service.getById(id);
  }
}
