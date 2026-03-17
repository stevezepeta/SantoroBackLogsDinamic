package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.SystemCatalogItemDto;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiCatalogService {

    private final MongoTemplate mongoTemplate;

    @Value("${multitenant.log-collection:log_events}")
    private String logCollection;

    private static final String F_TENANT = "tenant_id";
    private static final String F_SYS = "system";

    public List<SystemCatalogItemDto> listSystems(ObjectId tenantId, String q, int limit) {
        if (tenantId == null) return List.of();

        int safeLimit = Math.min(Math.max(limit, 1), 200);

        Criteria c = Criteria.where(F_TENANT).is(tenantId)
                .and(F_SYS).exists(true)
                .ne(null)
                .ne("");

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(c),
                Aggregation.group(F_SYS).count().as("count"),
                Aggregation.project()
                        .and("_id").as("name")
                        .and("count").as("count")
                        .andExclude("_id"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "count").and(Sort.by(Sort.Direction.ASC, "name"))),
                Aggregation.limit(safeLimit)
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();

        List<SystemCatalogItemDto> out = new ArrayList<>();
        String query = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        for (Document row : rows) {
            String name = Objects.toString(row.get("name"), "").trim();
            long count = toLong(row.get("count"));

            if (!StringUtils.hasText(name)) continue;

            if (query != null && !name.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            out.add(new SystemCatalogItemDto(name, count));
        }

        return out;
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }
}