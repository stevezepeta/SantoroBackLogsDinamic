package backlogs.dinamico.model.ai;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Document("ai_metrics")
@CompoundIndex(
        name = "uq_tenant_gran_bucket_system",
        def = "{'tenantId': 1, 'granularity': 1, 'bucketStart': 1, 'system': 1}",
        unique = true
)
@CompoundIndex(
        name = "ix_tenant_gran_system_bucket_desc",
        def = "{'tenantId': 1, 'granularity': 1, 'system': 1, 'bucketStart': -1}"
)
@CompoundIndex(
        name = "ix_tenant_gran_bucket_desc",
        def = "{'tenantId': 1, 'granularity': 1, 'bucketStart': -1}"
)
public class AiMetricRecord {

    @Id
    private ObjectId id;

    private ObjectId tenantId;

    /** hourly | daily */
    private String granularity;

    /** inicio exacto del bucket (UTC) */
    private Instant bucketStart;

    /** system / módulo  */
    private String system;

    /** ventana agregada (UTC) que usaste para calcular el resumen */
    private Instant windowFrom;
    private Instant windowTo;

    private Instant createdAt;
    private Instant updatedAt;

    // para UI
    private String tz;
    private String bucketStartLocal;
    private String windowFromLocal;
    private String windowToLocal;

    /** métricas base del bucket */
    private long total;
    private long errorCount;
    private double errorRate;

    /** severities globales (opcional por system). Puedes dejar null si no aplica */
    private Map<String, Long> severities;

    /** top error key del rango (opcional) */
    private String topErrorKey;
}