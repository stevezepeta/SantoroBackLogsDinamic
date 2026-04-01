package backlogs.dinamico.service.dashboard;

import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PassportEventService {

    private static final String COLLECTION = "log_events";

    private final MongoTemplate mongoTemplate;

    /**
     * Search “pasaportes” pero leyendo log_events (schema universal).
     *
     * Mapeos:
     * - operationType -> eventType
     * - officeId -> location.locationId
     * - userId -> actor.actorId
     * - channel -> payload.channel (si lo estás mandando así)
     * - passportNumber/personId -> payload.passportNumber / payload.personId (si aplica)
     * - requestId -> correlation.requestId
     * - reasonCode -> reason.code
     */
    public Page<LogEvent> searchPassportEvents(
            String system,
            Instant from,
            Instant to,
            String caseId,
            String passportNumber,
            String personId,
            String requestId,
            String status,
            String eventType,
            String officeId,
            String userId,
            String channel,
            String messageContains,
            String reasonCode,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        List<Criteria> c = new ArrayList<>();
        c.add(Criteria.where("tenantId").is(tenantId));
        c.add(Criteria.where("system").is(system));

        if (from != null) c.add(Criteria.where("eventTime").gte(from));
        if (to != null)   c.add(Criteria.where("eventTime").lte(to));

        if (StringUtils.hasText(caseId)) c.add(Criteria.where("caseId").is(caseId));
        if (StringUtils.hasText(status)) c.add(Criteria.where("status").is(status));
        if (StringUtils.hasText(eventType)) c.add(Criteria.where("eventType").is(eventType));

        if (StringUtils.hasText(officeId)) c.add(Criteria.where("location.locationId").is(officeId));
        if (StringUtils.hasText(userId)) c.add(Criteria.where("actor.actorId").is(userId));

        if (StringUtils.hasText(channel)) c.add(Criteria.where("payload.channel").is(channel));
        if (StringUtils.hasText(reasonCode)) c.add(Criteria.where("reason.code").is(reasonCode));

        if (StringUtils.hasText(passportNumber)) c.add(Criteria.where("payload.passportNumber").is(passportNumber));
        if (StringUtils.hasText(personId)) c.add(Criteria.where("payload.personId").is(personId));
        if (StringUtils.hasText(requestId)) c.add(Criteria.where("correlation.requestId").is(requestId));

        if (StringUtils.hasText(messageContains)) {
            Pattern pattern = Pattern.compile(".*" + Pattern.quote(messageContains) + ".*", Pattern.CASE_INSENSITIVE);
            c.add(Criteria.where("message").regex(pattern));
        }

        Criteria criteria = new Criteria().andOperator(c.toArray(new Criteria[0]));
        Query q = new Query(criteria);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "eventTime";
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        q.with(pageable);

        List<LogEvent> items = mongoTemplate.find(q, LogEvent.class, COLLECTION);

        long total = mongoTemplate.count(
                Query.of(q).limit(-1).skip(-1),
                COLLECTION
        );

        return new PageImpl<>(items, pageable, total);
    }

    /**
     * Obtiene detalle pero garantizando tenant actual.
     */
    public LogEvent getLogByIdForCurrentTenant(ObjectId id) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        LogEvent ev = mongoTemplate.findById(id, LogEvent.class, COLLECTION);
        if (ev == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "log_not_found");

        // Seguridad: evita que un tenant lea de otro tenant aunque conozca el id
        if (ev.getTenantId() == null || !tenantId.equals(ev.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "log_not_found");
        }
        return ev;
    }
}
