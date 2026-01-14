package backlogs.dinamico.service.dashboard;

import backlogs.dinamico.api.dto.logs.LogTimelineResponse;
import backlogs.dinamico.service.logs.LogEventService;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PassportTimelineService {

    private static final String COLLECTION = "log_events";

    private final MongoTemplate mongoTemplate;
    private final LogEventService logEventService;

    public LogTimelineResponse timelinePassport(
            String system,
            String caseId,
            String passportNumber,
            String personId,
            String requestId,
            Instant from,
            Instant to
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        String resolvedCaseId = resolveCaseId(system, caseId, passportNumber, personId, requestId, from, to);
        if (!StringUtils.hasText(resolvedCaseId)) {
            // timeline vacío
            return new LogTimelineResponse(
                    new LogTimelineResponse.Header(system, null),
                    List.of()
            );
        }

        return logEventService.timeline(system, resolvedCaseId, from, to);
    }

    private String resolveCaseId(
            String system,
            String caseId,
            String passportNumber,
            String personId,
            String requestId,
            Instant from,
            Instant to
    ) {
        if (StringUtils.hasText(caseId)) return caseId;

        // Si no te mandan caseId, intentamos resolverlo con passportNumber/personId/requestId
        List<Criteria> base = new ArrayList<>();
        base.add(Criteria.where("tenantId").is(TenantContext.getTenantId()));
        base.add(Criteria.where("system").is(system));

        if (from != null) base.add(Criteria.where("eventTime").gte(from));
        if (to != null) base.add(Criteria.where("eventTime").lte(to));

        List<Criteria> keys = new ArrayList<>();
        if (StringUtils.hasText(passportNumber)) keys.add(Criteria.where("payload.passportNumber").is(passportNumber));
        if (StringUtils.hasText(personId)) keys.add(Criteria.where("payload.personId").is(personId));
        if (StringUtils.hasText(requestId)) keys.add(Criteria.where("correlation.requestId").is(requestId));

        if (keys.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debe enviar caseId o alguno de: passportNumber, personId, requestId"
            );
        }

        Criteria keyCriteria = (keys.size() == 1)
                ? keys.get(0)
                : new Criteria().orOperator(keys.toArray(new Criteria[0]));

        base.add(keyCriteria);

        Criteria finalCriteria = new Criteria().andOperator(base.toArray(new Criteria[0]));
        Query q = new Query(finalCriteria);
        q.fields().include("caseId");
        q.limit(1);

        Document doc = mongoTemplate.findOne(q, Document.class, COLLECTION);
        if (doc == null) return null;

        Object cid = doc.get("caseId");
        return cid != null ? cid.toString() : null;
    }
}
