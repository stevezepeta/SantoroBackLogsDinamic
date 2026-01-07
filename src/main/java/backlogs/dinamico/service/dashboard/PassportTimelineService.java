package backlogs.dinamico.service.dashboard;

import backlogs.dinamico.api.dto.passport.PassportTimelineResponse;
import backlogs.dinamico.model.passport.PassportEvent;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassportTimelineService {

    private final MongoTemplate mongoTemplate;

    private static final String COLLECTION = "passport_events";
    private static final String PASSPORT_SYSTEM = "PASSPORT_PA";

    public PassportTimelineResponse getTimeline(
            String passportNumber,
            String personId,
            String requestId,
            Instant from,
            Instant to
    ) {

        Object tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant no resuelto en contexto");
        }

        List<Criteria> criteriaList = new ArrayList<>();

        criteriaList.add(Criteria.where("tenantId").is(tenantId));
        criteriaList.add(Criteria.where("system").is(PASSPORT_SYSTEM));

        List<Criteria> businessKeys = new ArrayList<>();
        if (StringUtils.hasText(passportNumber)) {
            businessKeys.add(Criteria.where("passport.passportNumber").is(passportNumber));
        }
        if (StringUtils.hasText(personId)) {
            businessKeys.add(Criteria.where("passport.personId").is(personId));
        }
        if (StringUtils.hasText(requestId)) {
            businessKeys.add(Criteria.where("meta.requestId").is(requestId));
        }

        if (businessKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe especificar al menos uno de: passportNumber, personId o requestId"
            );
        }

        if (businessKeys.size() == 1) {
            criteriaList.add(businessKeys.get(0));
        } else {
            criteriaList.add(new Criteria().orOperator(
                    businessKeys.toArray(new Criteria[0])
            ));
        }

        // Rango de fechas opcional
        if (from != null && to != null) {
            criteriaList.add(Criteria.where("eventTime").gte(from).lte(to));
        } else if (from != null) {
            criteriaList.add(Criteria.where("eventTime").gte(from));
        } else if (to != null) {
            criteriaList.add(Criteria.where("eventTime").lte(to));
        }

        Criteria finalCriteria = new Criteria().andOperator(
                criteriaList.toArray(new Criteria[0])
        );

        Query q = new Query(finalCriteria)
                .with(Sort.by(Sort.Direction.ASC, "eventTime"));

        List<PassportEvent> events =
                mongoTemplate.find(q, PassportEvent.class, COLLECTION);

        if (events.isEmpty()) {
            return new PassportTimelineResponse(null, List.of());
        }

        PassportEvent first = events.get(0);

        PassportTimelineResponse.Header header =
                new PassportTimelineResponse.Header(
                        first.getPassport() != null ? first.getPassport().getPassportNumber() : null,
                        first.getPassport() != null ? first.getPassport().getPersonId() : null,
                        first.getPassport() != null ? first.getPassport().getFullName() : null,
                        first.getPassport() != null ? first.getPassport().getNationality() : null
                );

        List<PassportTimelineResponse.Item> items = events.stream()
                .map(ev -> new PassportTimelineResponse.Item(
                        ev.getId() != null ? ev.getId().toHexString() : null,
                        ev.getEventTime(),
                        ev.getOperationType(),
                        ev.getStatus(),
                        ev.getMessage(),
                        ev.getOffice() != null ? ev.getOffice().getOfficeId() : null,
                        ev.getOffice() != null ? ev.getOffice().getOfficeName() : null,
                        ev.getChannel(),
                        ev.getUser() != null ? ev.getUser().getUserId() : null,
                        ev.getUser() != null ? ev.getUser().getUsername() : null,
                        ev.getUser() != null ? ev.getUser().getFullName() : null,
                        ev.getReason() != null ? ev.getReason().getCode() : null,
                        ev.getReason() != null ? ev.getReason().getDescription() : null,
                        ev.getSla() != null ? ev.getSla().getElapsedSeconds() : null
                ))
                .toList();

        return new PassportTimelineResponse(header, items);

    }

}
