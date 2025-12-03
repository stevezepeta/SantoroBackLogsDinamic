package backlogs.dinamico.service.logs;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class LogQueryService {

    private final MongoTemplate mongo;

    private Criteria base(ObjectId tenantId) {
        return Criteria.where("tenantId").is(tenantId);
    }

    private void addIf(Map<String, String> filters, Criteria c, String key, String field) {
        String v = (filters == null) ? null : filters.get(key);
        if (v != null && !v.isBlank()) c.and(field).is(v);
    }

    public Map<String, Object> list(ObjectId tenantId, Map<String, String> filters,
                                    Instant from, Instant to,
                                    int page, int size, String sort, String order) {

        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 200;

        if (!StringUtils.hasText(sort))  sort  = "timestamp"; // default
        if (!StringUtils.hasText(order)) order = "desc";       // default

        if (filters == null) filters = java.util.Collections.emptyMap();

        Criteria c = base(tenantId);

        if (from != null || to != null) {
            Criteria time = Criteria.where("timestamp");
            if (from != null) time.gte(from);
            if (to != null) time.lte(to);

            c.andOperator(time);
        }

        // filtros exactos
        addIf(filters, c, "level",        "level");
        addIf(filters, c, "processType",  "processType");
        addIf(filters, c, "device",       "device");
        addIf(filters, c, "scanDevice",   "scanDevice");
        addIf(filters, c, "scanType",     "scanType");
        addIf(filters, c, "officeId",     "officeId");
        addIf(filters, c, "personId",     "personId");
        addIf(filters, c, "baseCode",     "baseCode");
        addIf(filters, c, "errorCode",    "errorCode");
        addIf(filters, c, "sessionToken", "sessionToken");
        addIf(filters, c, "system",       "system");
        addIf(filters, c, "environment",  "environment");

        Query q = new Query(c);

        // Busqueda por texto simple en el message
        String qtext = filters.get("q");
        if (StringUtils.hasText(qtext)) {
            q.addCriteria(Criteria.where("message").regex(qtext, "i"));
        }

        // order
        Sort.Direction dir = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        q.with(Sort.by(dir, sort));
        q.with(PageRequest.of(page - 1, size));

        // paginado
//        PageRequest pr = PageRequest.of(page - 1, size);
//        q.with(pr);

        // ejecutar
        List<Document> data = mongo.find(q, Document.class, "logs");
        long total = mongo.count(Query.of(q).limit(-1).skip(-1), "logs");

        // proyeccion para el grid
        List<Map<String,Object>> rows = new ArrayList<>();

        for (Document d : data) {
            Map<String, Object> row = new java.util.HashMap<>();

            // _id seguro a String
            Object _id = d.get("_id");
            String idStr = (_id instanceof org.bson.types.ObjectId)
                    ? ((org.bson.types.ObjectId)_id).toHexString()
                    : String.valueOf(_id);

            row.put("id", idStr);
            row.put("timestamp", d.get("timestamp"));          // puede ser null: OK
            row.put("level", d.get("level"));                  // puede ser null: OK
            row.put("message", d.getString("message"));        // puede ser null: OK
            row.put("processType", d.getString("processType"));// puede ser null: OK
            row.put("device", d.getString("device"));          // puede ser null: OK
            row.put("errorCode", d.getString("errorCode"));    // puede ser null: OK
            row.put("sessionToken", d.getString("sessionToken")); // puede ser null: OK

            rows.add(row);

        }

        return Map.of(
                "page", page,
                "size", size,
                "total", total,
                "data", rows
        );
    }

    public Map<String, Object> getOne(ObjectId tenantId, ObjectId id) {

        Query q = new Query(Criteria.where("_id").is(id).and("tenantId").is(tenantId));
        Document d = mongo.findOne(q, Document.class, "logs");

        if (d == null) throw new NoSuchElementException("log_not_found");
        // convertir _id a string
        d.put("id", d.getObjectId("_id").toHexString());
        d.remove("_id");
        return d;
    }

    public Map<String, Object> summary(ObjectId tenantId, Instant from, Instant to) {

        Criteria c = base(tenantId);
        if (from != null || to != null) {
            Criteria time = Criteria.where("timestamp");
            if (from != null) time.gte(from);
            if (to != null) time.lte(to);

            c.andOperator(time);
        }

        MatchOperation match = match(c);

        // total
        CountOperation total = Aggregation.count().as("total");

        // por nivel
        GroupOperation byLevel = group("level").count().as("count");
        SortOperation sortLevel = sort(Sort.Direction.DESC, "count");
        Aggregation aggLevel = newAggregation(match, byLevel, sortLevel);
        List<Document> levels = mongo.aggregate(aggLevel, "logs", Document.class).getMappedResults();

        // top processType
        GroupOperation byProc = group("processType").count().as("count");
        Aggregation aggProc = newAggregation(match, byProc, sort(Sort.Direction.DESC, "count"), limit(10));
        List<Document> procs = mongo.aggregate(aggProc, "logs", Document.class).getMappedResults();

        // top errorCode
        GroupOperation byErr = group("errorCode").count().as("count");
        Aggregation aggErr = newAggregation(match,
                match(Criteria.where("errorCode").ne(null)),
                byErr, sort(Sort.Direction.DESC, "count"), limit(10));
        List<Document> errors = mongo.aggregate(aggErr, "logs", Document.class).getMappedResults();

        // total simple
        long totalCount = mongo.count(new Query(c), "logs");

        return Map.of(
                "total", totalCount,
                "byLevel", levels,
                "toProcessType", procs,
                "topErrorCode", errors
        );

    }

    public Map<String,Object> timeline(ObjectId tenantId, Instant from, Instant to, String bucket, String level) {
        Criteria c = base(tenantId);
        if (from != null || to != null) {
            Criteria time = Criteria.where("timestamp");
            if (from != null) time.gte(from);
            if (to   != null) time.lte(to);
            c.andOperator(time);
        }
        if (level != null && !level.isBlank()) c.and("level").is(level);

        MatchOperation match = Aggregation.match(c);

        // Usa $dateTrunc si tu Mongo >= 5; si no, cambia por $dateToString con formato.
        String unit = switch (bucket == null ? "hour" : bucket.toLowerCase()) {
            case "minute" -> "minute";
            case "day"    -> "day";
            default       -> "hour";
        };

        ProjectionOperation projectTs = Aggregation.project()
                .and(DateOperators.DateTrunc.truncateValueOf("timestamp").to(unit)).as("ts");

        GroupOperation group = Aggregation.group("ts").count().as("count");

        SortOperation sortOp = Aggregation.sort(Sort.Direction.ASC, "ts");
        ProjectionOperation out = Aggregation.project("ts", "count");


        Aggregation agg = Aggregation.newAggregation(match, projectTs, group, out, sortOp);
        List<org.bson.Document> points = mongo.aggregate(agg, "logs", org.bson.Document.class).getMappedResults();

        return Map.of("bucket", unit, "points", points);
    }

}
