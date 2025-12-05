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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class LogQueryService {

    private final MongoTemplate mongo;

    // Formato de fecha
    private static final DateTimeFormatter LEGACY_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

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

        if (!StringUtils.hasText(sort))  sort  = "timestamp";
        if (!StringUtils.hasText(order)) order = "desc";

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


        // ejecutar
        List<Document> data = mongo.find(q, Document.class, "logs");
        long total = mongo.count(Query.of(q).limit(-1).skip(-1), "logs");

        // proyeccion legacy
        List<Map<String,Object>> rows = new ArrayList<>();

        for (Document d : data) {

            // id seguro
            Object idObj = d.get("_id");
            String idStr = (idObj instanceof ObjectId oid) ? oid.toHexString() : String.valueOf(idObj);

            Instant ts = null;
            Object rawTs = d.get("timestamp");
            if (rawTs instanceof Date date) {
                ts = date.toInstant();
            } else if (rawTs instanceof Instant instant) {
                ts = instant;
            }

            String dateStr = (ts != null) ? LEGACY_DT.format(ts) : null;

            String level = d.getString("level");
            String message = d.getString("message");
            String device = d.getString("device");
            String scanDevice = d.getString("scanDevice");
            String processType = d.getString("processType");
            String baseCode = d.getString("baseCode");
            String errorCode = d.getString("errorCode");
            String sessionToken = d.getString("sessionToken");

            String personId = d.getString("personId");
            String officeId = d.getString("officeId");

            // Person y Oficinas

            Map<String, Object> person = buildPerson(tenantId, personId);
            Map<String, Object> oficina = buildOffice(tenantId, officeId);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",           idStr);
            row.put("date",         dateStr);
            row.put("type",         level);
            row.put("person",       person);
            row.put("device",       device);
            row.put("scanDevice",   scanDevice);
            row.put("process",      processType);
            row.put("message",      message);
            row.put("oficina",      oficina);
            row.put("paisId",       oficina.get("paisId"));
            row.put("estadoId",     oficina.get("estadoId"));
            row.put("municipioId",  oficina.get("municipioId"));
            row.put("trackingCode", baseCode);
            row.put("errorCode",    errorCode);
            row.put("sessionToken", sessionToken);
            row.put("baseCode",     baseCode);

            rows.add(row);
        }

        return Map.of(
                "page", page,
                "size", size,
                "total", total,
                "data", rows
        );
    }

    // Mapenando por Id
    private Map<String, Object> mapLegacyFromDoc(ObjectId tenantId, Document d) {

        Object idObj = d.get("_id");
        String idStr = (idObj instanceof ObjectId oid)
                ? oid.toHexString()
                : String.valueOf(idObj);

        Instant ts = null;
        Object rawTs = d.get("timestamp");
        if (rawTs instanceof Date date) {
            ts = date.toInstant();
        } else if (rawTs instanceof Instant instant) {
            ts = instant;
        }

        String dateStr = (ts != null) ? LEGACY_DT.format(ts) : null;

        String level        = d.getString("level");
        String message      = d.getString("message");
        String device       = d.getString("device");
        String scanDevice   = d.getString("scanDevice");
        String processType  = d.getString("processType");
        String baseCode     = d.getString("baseCode");
        String errorCode    = d.getString("errorCode");
        String sessionToken = d.getString("sessionToken");

        String personId = d.getString("personId");
        String officeId = d.getString("officeId");

        // Person y Oficinas
        Map<String, Object> person = buildPerson(tenantId, personId);
        Map<String, Object> oficina = buildOffice(tenantId, officeId);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id",           idStr);
        row.put("date",         dateStr);
        row.put("type",         level);
        row.put("person",       person);
        row.put("device",       device);
        row.put("scanDevice",   scanDevice);
        row.put("process",      processType);
        row.put("message",      message);
        row.put("oficina",      oficina);
        row.put("paisId",       oficina.get("paisId"));
        row.put("estadoId",     oficina.get("estadoId"));
        row.put("municipioId",  oficina.get("municipioId"));
        row.put("trackingCode", baseCode);
        row.put("errorCode",    errorCode);
        row.put("sessionToken", sessionToken);
        row.put("baseCode",     baseCode);

        return row;
    }


    // HELPER: Person y Oficina
    private Map<String, Object> buildPerson(ObjectId tenantId, String personId) {

        Map<String, Object> person = new LinkedHashMap<>();

        if (!StringUtils.hasText(personId)) {
            person.put("id",              null);
            person.put("curp",            null);
            person.put("nombres",         null);
            person.put("primerApellido",  null);
            person.put("segundoApellido", null);
            person.put("sexo",            null);
            person.put("nacionalidad",    null);
            person.put("fechaNacimiento", null);
            person.put("direccion",       null);

            return person;
        }

        Criteria c = Criteria.where("tenant_id").is(tenantId);
        if (personId.matches("^[0-9a-fA-F]{24}$")) {
            c = c.and("_id").is(new ObjectId(personId));
        } else {
            c = c.and("curp").is(personId);
        }

        Query qp = new Query(c);
        Document personDoc = mongo.findOne(qp, Document.class, "persons");

        if (personDoc == null) {
            person.put("id",              personId);
            person.put("curp",            null);
            person.put("nombres",         null);
            person.put("primerApellido",  null);
            person.put("segundoApellido", null);
            person.put("sexo",            null);
            person.put("nacionalidad",    null);
            person.put("fechaNacimiento", null);
            person.put("direccion",       null);

            return person;
        }

        Object pid = personDoc.get("_id");
        String pidStr = (pid instanceof ObjectId oid)
                ? oid.toHexString()
                : String.valueOf(pid);

        person.put("id",       pidStr);
        person.put("curp",     personDoc.getString("curp"));
        // Tu modelo sólo tiene "name" completo
        person.put("nombres",  personDoc.getString("name"));
        person.put("primerApellido",  null);
        person.put("segundoApellido", null);
        person.put("sexo",            null);
        person.put("nacionalidad",    null);
        person.put("fechaNacimiento", null);
        person.put("direccion",       null);

        return person;
    }

    private Map<String, Object> buildOffice(ObjectId tenantId, String officeId) {

        Map<String, Object> oficina = new LinkedHashMap<>();

        if (!StringUtils.hasText(officeId)) {
            oficina.put("id",          null);
            oficina.put("nombre",      null);
            oficina.put("direccion",   null);
            oficina.put("paisId",      null);
            oficina.put("estadoId",    null);
            oficina.put("municipioId", null);

            return oficina;
        }

        Criteria c = Criteria.where("tenant_id").is(tenantId);

        if (officeId.matches("^[0-9a-fA-F]{24}$")) {
            c = c.and("_id").is(new ObjectId(officeId));
        } else {
            c = c.and("name").is(officeId);
        }

        Query qp = new Query(c);
        Document officeDoc = mongo.findOne(qp, Document.class, "offices");

        if (officeDoc == null) {
            oficina.put("id",          officeId);
            oficina.put("nombre",      null);
            oficina.put("direccion",   null);
            oficina.put("paisId",      null);
            oficina.put("estadoId",    null);
            oficina.put("municipioId", null);

            return oficina;
        }

        Object oid = officeDoc.get("_id");
        String oidStr = (oid instanceof ObjectId x)
                ? x.toHexString()
                :String.valueOf(oid);

        oficina.put("id",        oidStr);
        oficina.put("nombre",    officeDoc.getString("name"));
        oficina.put("direccion", officeDoc.getString("address"));
        oficina.put("paisId",    officeDoc.get("country_id"));
        oficina.put("estadoId",  officeDoc.get("state_id"));
        oficina.put("municipioId", officeDoc.get("municipality_id"));

        return oficina;

    }


    public Map<String, Object> getOne(ObjectId tenantId, ObjectId id) {

        Query q = new Query(Criteria.where("_id").is(id).and("tenantId").is(tenantId));
        Document d = mongo.findOne(q, Document.class, "logs");

        if (d == null) {
            throw new NoSuchElementException("log_not_found");
        }

        return mapLegacyFromDoc(tenantId, d);
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
