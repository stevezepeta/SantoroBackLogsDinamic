package backlogs.dinamico.service.dashboard;

import backlogs.dinamico.api.dto.passport.PassportSummaryResponse;
import backlogs.dinamico.api.dto.passport.PassportsByOfficeItem;
import backlogs.dinamico.api.dto.passport.PassportsByTypeItem;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassportOverviewService {

    private final MongoTemplate mongoTemplate;

    private static final String COLLECTION = "passport_events";

    // Sistema específico de Panamá
    private static final String PASSPORT_SYSTEM = "PASSPORT_PA";

    // Helpers para fechas por defecto
    public static Instant defaultFrom() {
        return LocalDate.now(ZoneOffset.UTC)
                .minusDays(30)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    public static Instant defaultTo() {
        return LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    /*
     * Resumen general de pasaportes en un rango de fechas
     */
    public PassportSummaryResponse getSummary(
            Instant from,
            Instant to,
            String officeId,
            String userId,
            String channel,
            String operationType,
            String status
    ) {

        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant no resuelto en contexto");
        }

        Criteria baseCriteria = Criteria.where("tenantId").is(tenantId)
                .and("system").is(PASSPORT_SYSTEM)
                .and("eventTime").gte(from).lte(to);

        if (StringUtils.hasText(officeId)) {
            baseCriteria = baseCriteria.and("office.officeId").is(officeId);
        }

        if (StringUtils.hasText(userId)) {
            baseCriteria = baseCriteria.and("user.userId").is(userId);
        }

        if (StringUtils.hasText(channel)) {
            baseCriteria = baseCriteria.and("channel").is(channel);
        }

        if (StringUtils.hasText(operationType)) {
            baseCriteria = baseCriteria.and("operationType").is(operationType);
        }

        if (StringUtils.hasText(status)) {
            String normalizedStatus = normalizeStatusFilter(status);
            baseCriteria = baseCriteria.and("status").is(normalizedStatus);
        }

        // Totales por status
        Aggregation totalsAgg = newAggregation(
                match(baseCriteria),
                project("status"),
                group("status").count().as("count")
        );

        AggregationResults<Document> totalsResults =
                mongoTemplate.aggregate(totalsAgg, COLLECTION, Document.class);

        long emitidos = 0L, enTramite = 0L,  rechazados = 0L, cancelados = 0L;

        for (Document d : totalsResults.getMappedResults()) {
            String dbStatus = d.getString("_id");
            long count = getCountAsLong(d);

            if ("EMITIDO".equalsIgnoreCase(dbStatus)) {
                emitidos = count;
            } else if (isEnTramite(dbStatus)) {
                enTramite = count;
            } else if ("RECHAZADO".equalsIgnoreCase(dbStatus)) {
                rechazados = count;
            } else if ("CANCELADO".equalsIgnoreCase(dbStatus)) {
                cancelados = count;
            }
        }

        // Por dia
        Aggregation perDayAgg = newAggregation(
                match(baseCriteria),
                project("status")
                        .andExpression("{ $dateToString: { date: \"$eventTime\", format: \"%Y-%m-%d\", timezone: \"UTC\" } }")
                        .as("fecha"),
                group("fecha", "status").count().as("count"),
                sort(Sort.Direction.ASC, "_id.fecha")
        );

        AggregationResults<Document> perDayResults =
                mongoTemplate.aggregate(perDayAgg, COLLECTION, Document.class);

        // Mapear DTO
        List<PassportSummaryResponse.PorDia> perDayDtos =
                perDayResults.getMappedResults().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                d -> d.get("_id", Document.class).getString("fecha"),
                                java.util.stream.Collectors.toList()
                        ))
                        .entrySet().stream()
                        .map(e -> {
                            String fecha = e.getKey();
                            long eEmit = 0L, eEnTram = 0L, eRech = 0L, eCanc = 0L;

                            for (Document d : e.getValue()) {
                                Document id = d.get("_id", Document.class);
                                String st = id.getString("status");
                                long c = getCountAsLong(d);

                                if ("EMITIDO".equalsIgnoreCase(st)) {
                                    eEmit = c;
                                } else if (isEnTramite(st)) {
                                    eEnTram = c;
                                } else if ("RECHAZADO".equalsIgnoreCase(st)) {
                                    eRech = c;
                                } else if ("CANCELADO".equalsIgnoreCase(st)) {
                                    eCanc = c;
                                }
                            }

                            return new PassportSummaryResponse.PorDia(
                                    fecha,
                                    eEmit,
                                    eEnTram,
                                    eRech,
                                    eCanc
                            );
                        })
                        .sorted((a, b) -> a.fecha().compareTo(b.fecha()))
                        .toList();

        return new PassportSummaryResponse(
           from,
           to,
           new PassportSummaryResponse.Totales(
                   emitidos,
                   enTramite,
                   rechazados,
                   cancelados
           ),
           perDayDtos
        );
    }

    /*
     * Pasaportes por oficina en un rango de fechas
     */
    public List<PassportsByOfficeItem> getByOffice(Instant from, Instant to) {

        ObjectId tenantId = TenantContext.getTenantId();
        if(tenantId == null) {
            throw new IllegalArgumentException("Tenant no resuelto en contexto");
        }

        Criteria baseCriteria = Criteria.where("tenantId").is(tenantId)
                .and("system").is(PASSPORT_SYSTEM)
                .and("eventTime").gte(from).lte(to);

        Aggregation agg = newAggregation(
                match(baseCriteria),
                // Proyectamos status + datos de oficina
                project("status")
                        .and("office.officeId").as("officeId")
                        .and("office.officeName").as("officeName"),
                // Agrupamos por oficina + status
                group("officeId", "officeName", "status").count().as("count"),
                sort(Sort.Direction.ASC, "_id.officeName")
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(agg, COLLECTION, Document.class);

        // Agrupamos en memoria por officeId
        return results.getMappedResults().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.get("_id", Document.class).getString("officeId"),
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(e -> {
                    String officeId = e.getKey();
                    List<Document> docs = e.getValue();

                    // Tomamos officeName del primer documento
                    Document firstId = docs.get(0).get("_id", Document.class);
                    String officeName = firstId.getString("officeName");

                    long emitidos = 0L, enTramite = 0L, rechazados = 0L, cancelados = 0L;

                    for (Document d : docs) {
                        Document id = d.get("_id", Document.class);
                        String st = id.getString("status");
                        long c = getCountAsLong(d);

                        if ("EMITIDO".equalsIgnoreCase(st)) {
                            emitidos = c;
                        } else if (isEnTramite(st)) {
                            enTramite = c;
                        } else if ("RECHAZADO".equalsIgnoreCase(st)) {
                            rechazados = c;
                        } else if ("CANCELADO".equalsIgnoreCase(st)) {
                            cancelados = c;
                        }
                    }

                    return new PassportsByOfficeItem(
                            officeId,
                            officeName,
                            emitidos,
                            enTramite,
                            rechazados,
                            cancelados
                    );
                })
                // Ordenamos de mayor a menor por emitidos
                .sorted((a, b) -> Long.compare(b.emitidos(), a.emitidos()))
                .toList();
    }

    /*
     * Reportes por tipo de tramite
     */
    public List<PassportsByTypeItem> getByType(Instant from, Instant to) {

        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant no resuelto en contexto");
        }

        Criteria baseCriteria = Criteria.where("tenantId").is(tenantId)
                .and("system").is(PASSPORT_SYSTEM)
                .and("eventTime").gte(from).lte(to);

        Aggregation agg = newAggregation(
                match(baseCriteria),
                // Proyectamos operationType (tipo) + status
                project("operationType", "status"),
                // Agrupamos por tipo + status
                group("operationType", "status").count().as("count"),
                sort(Sort.Direction.ASC, "_id.operationType")
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(agg, COLLECTION, Document.class);


        // Agrupamos en memoria por tipo de trámite
        return results.getMappedResults().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.get("_id", Document.class).getString("operationType"),
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(e -> {
                    String tipo = e.getKey();
                    List<Document> docs = e.getValue();

                    long emitidos = 0L, enTramite = 0L, rechazados = 0L, cancelados = 0L;

                    for (Document d : docs) {
                        Document id = d.get("_id", Document.class);
                        String st = id.getString("status");
                        long c = getCountAsLong(d);

                        if ("EMITIDO".equalsIgnoreCase(st)) {
                            emitidos = c;
                        } else if (isEnTramite(st)) {
                            enTramite = c;
                        } else if ("RECHAZADO".equalsIgnoreCase(st)) {
                            rechazados = c;
                        } else if ("CANCELADO".equalsIgnoreCase(st)) {
                            cancelados = c;
                        }
                    }

                    return new PassportsByTypeItem(
                            tipo,
                            emitidos,
                            enTramite,
                            rechazados,
                            cancelados
                    );
                })
                // Ordenamos de mayor a menor por emitidos
                .sorted((a, b) -> Long.compare(b.emitidos(), a.emitidos()))
                .toList();
    }

    // Normalizar status ENTRAMITE
    private boolean isEnTramite(String status) {

        if (status == null) return false;
        String norm = status
                .replace("_", "")
                .replace(" ", "")
                .toUpperCase();
        return "ENTRAMITE".equals(norm);
    }


    // Devuelve ClassCastException
    private long getCountAsLong(Document d) {
        Number n = d.get("count", Number.class);

        return n != null ? n.longValue() : 0L;
    }

    // Helper para normalizar el status
    private String normalizeStatusFilter(String raw) {

        if (raw == null) return null;
        String s = raw.trim().toUpperCase();

        if ("ENTRAMITE".equals(s) || "EN_TRAMITE".equals(s)) {
            return "EN_TRAMITE";
        }

        return s;
    }

}
