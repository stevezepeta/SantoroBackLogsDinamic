package backlogs.dinamico.repository.log;

import backlogs.dinamico.api.dto.analytics.UserQuickAuditResponse;
import backlogs.dinamico.tenant.TenantContext;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
public class LogEventRepositoryImpl implements LogEventRepositoryCustom {

    private static final String COLLECTION = "log_events";

    private final MongoTemplate mongoTemplate;

    public LogEventRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<UserQuickAuditResponse> quickAuditUsers(String query, int limit) {
        ObjectId tenantId = TenantContext.requireTenantId();
        String regex = Pattern.quote(query.trim());
        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        Aggregation search = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("tenant_id").is(tenantId)
                        .orOperator(
                                Criteria.where("actor.username").regex(regex, "i"),
                                Criteria.where("actor.fullName").regex(regex, "i"))),
                Aggregation.sort(Sort.Direction.DESC, "eventTime"),
                Aggregation.group("actor.username")
                        .first("actor.fullName").as("fullName")
                        .max("eventTime").as("lastSeen")
                        .first("eventType").as("lastEventType")
                        .first("outcome").as("lastOutcome")
                        .first("remoteConnection.sourceIp").as("lastIp")
                        .first("meta.device").as("lastDevice"),
                Aggregation.sort(Sort.Direction.DESC, "lastSeen"),
                Aggregation.limit(limit));

        List<QuickAuditRow> rows = mongoTemplate
                .aggregate(search, COLLECTION, QuickAuditRow.class)
                .getMappedResults();

        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> usernames = rows.stream().map(QuickAuditRow::getId).toList();

        Aggregation todayCounts = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("tenant_id").is(tenantId)
                        .and("eventTime").gte(startOfToday)
                        .and("actor.username").in(usernames)),
                Aggregation.group("actor.username").count().as("total"));

        Map<String, Long> totals = mongoTemplate
                .aggregate(todayCounts, COLLECTION, UserActivityCount.class)
                .getMappedResults()
                .stream()
                .collect(Collectors.toMap(UserActivityCount::getId, UserActivityCount::getTotal));

        return rows.stream()
                .map(row -> {
                    long totalToday = totals.getOrDefault(row.getId(), 0L);
                    return new UserQuickAuditResponse(
                            row.getId(),
                            row.getFullName(),
                            totalToday > 0,
                            totalToday,
                            row.getLastSeen(),
                            row.getLastDevice(),
                            row.getLastIp(),
                            row.getLastEventType(),
                            row.getLastOutcome());
                })
                .toList();
    }

    @Data
    private static class QuickAuditRow {
        private String id;
        private String fullName;
        private Instant lastSeen;
        private String lastEventType;
        private String lastOutcome;
        private String lastIp;
        private String lastDevice;
    }

    @Data
    private static class UserActivityCount {
        private String id;
        private Long total;
    }
}