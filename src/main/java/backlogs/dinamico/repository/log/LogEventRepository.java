package backlogs.dinamico.repository.log;

import backlogs.dinamico.model.log.LogEvent;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;

import java.util.Optional;

@CompoundIndexes({
        // Base: listado paginado por fecha
        @CompoundIndex(name = "idx_tenant_system_time",
                def = "{'tenantId':1,'system':1,'eventTime':-1}"),

        // Timeline: caseId + orden por fecha
        @CompoundIndex(name = "idx_tenant_system_case_time",
                def = "{'tenantId':1,'system':1,'caseId':1,'eventTime':1}"),

        // Filtros típicos del dashboard
        @CompoundIndex(name = "idx_tenant_system_eventType_time",
                def = "{'tenantId':1,'system':1,'eventType':1,'eventTime':-1}"),
        @CompoundIndex(name = "idx_tenant_system_status_time",
                def = "{'tenantId':1,'system':1,'status':1,'eventTime':-1}"),
        @CompoundIndex(name = "idx_tenant_system_outcome_time",
                def = "{'tenantId':1,'system':1,'outcome':1,'eventTime':-1}"),
        @CompoundIndex(name = "idx_tenant_system_severity_time",
                def = "{'tenantId':1,'system':1,'severity':1,'eventTime':-1}"),

        // Correlación: requestId (muy usado para trazabilidad)
        @CompoundIndex(name = "idx_tenant_system_requestId_time",
                def = "{'tenantId':1,'system':1,'correlation.requestId':1,'eventTime':-1}"),

        // Actor/Location (cuando filtren por usuario/dispositivo/oficina/sucursal/etc.)
        @CompoundIndex(name = "idx_tenant_system_actor_time",
                def = "{'tenantId':1,'system':1,'actor.id':1,'eventTime':-1}"),
        @CompoundIndex(name = "idx_tenant_system_location_time",
                def = "{'tenantId':1,'system':1,'location.id':1,'eventTime':-1}")
})
public interface LogEventRepository extends MongoRepository<LogEvent, ObjectId> {

    Optional<LogEvent> findByIdAndTenantId(ObjectId id, ObjectId tenantId);

}
