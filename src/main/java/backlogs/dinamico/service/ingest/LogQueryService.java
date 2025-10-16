// backlogs/dinamico/service/ingest/LogQueryService.java
package backlogs.dinamico.service.ingest;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LogQueryService {

  private final MongoTemplate mongoTemplate;
  private final LogEventIndexManager indexManager;

  public Page<Document> search(
      Instant from,
      Instant to,
      String severity,
      String eventTypeCode,
      String q,
      String traceId,
      String sessionId,
      String deviceId,
      Pageable pageable
  ) {
    String collection = indexManager.resolveCollectionName();

    Query query = new Query();
    List<Criteria> and = new ArrayList<>();

    // Rango de fechas
    if (from != null || to != null) {
      Criteria c = Criteria.where("event_at");
      if (from != null) c = c.gte(Date.from(from));
      if (to != null)   c = c.lte(Date.from(to));
      and.add(c);
    }

    // Filtros directos
    if (StringUtils.hasText(severity))      and.add(Criteria.where("severity").is(severity));
    if (StringUtils.hasText(eventTypeCode)) and.add(Criteria.where("event_type_code").is(eventTypeCode));
    if (StringUtils.hasText(traceId))       and.add(Criteria.where("trace_id").is(traceId));
    if (StringUtils.hasText(sessionId))     and.add(Criteria.where("session_id").is(sessionId));
    if (StringUtils.hasText(deviceId))      and.add(Criteria.where("device_id").is(deviceId));

    // Búsqueda de texto simple en varios campos
    if (StringUtils.hasText(q)) {
      String rx = Pattern.quote(q);
      and.add(new Criteria().orOperator(
          Criteria.where("event_type_code").regex(rx, "i"),
          Criteria.where("severity").regex(rx, "i"),
          Criteria.where("error_code").regex(rx, "i"),
          Criteria.where("username").regex(rx, "i"),
          Criteria.where("source.host").regex(rx, "i"),
          Criteria.where("payload").regex(rx, "i")
      ));
    }

    if (!and.isEmpty()) {
      query.addCriteria(new Criteria().andOperator(and.toArray(new Criteria[0])));
    }

    // Orden por defecto si no viene del Pageable
    if (pageable.getSort().isUnsorted()) {
      query.with(Sort.by(Sort.Order.desc("event_at")));
    }
    query.with(pageable);

    long total = mongoTemplate.count(query, collection);
    List<Document> rows = mongoTemplate.find(query, Document.class, collection);
    return new PageImpl<>(rows, pageable, total);
  }

  public Document getById(ObjectId id) {
    String collection = indexManager.resolveCollectionName();
    Document doc = mongoTemplate.findById(id, Document.class, collection);
    if (doc == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return doc;
  }
}
