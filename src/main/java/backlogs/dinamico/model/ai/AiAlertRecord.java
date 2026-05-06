package backlogs.dinamico.model.ai;

import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Document("ai_alerts")
@CompoundIndex(
        name = "uq_tenant_fingerprint",
        def = "{'tenantId': 1, 'fingerprint': 1}",
        unique = true
)
// Serie de tiempo: consultar por tenant + granularidad + bucketStart en rango
@CompoundIndex(
        name = "ix_tenant_gran_bucket",
        def = "{'tenantId': 1, 'granularity': 1, 'bucketStart': 1}"
)
// UI/operación: filtrar por estado + status ordenado por createdAt
@CompoundIndex(
        name = "ix_tenant_state_status_created",
        def = "{'tenantId': 1, 'state': 1, 'status': 1, 'createdAt': -1}"
)
public class AiAlertRecord {

    @Id
    private ObjectId id;
    private ObjectId tenantId;

    /** hourly | daily */
    private String granularity;

    private Instant windowFrom;
    private Instant windowTo;

    private Instant bucketStart;

    private Instant createdAt;

    // campos localizados si los usas en UI
    public String tz;
    public String windowFromLocal;
    public String windowToLocal;
    public String bucketStartLocal;
    public String createdAtLocal;

    /** OK | WARN | CRIT */
    private String status;

    /** Métricas base */
    private long total;
    private double errorRate;

    /** NUEVO: errores absolutos (mejor para anomalías) */
    private long errorCount;

    /** Severities agregadas */
    private Map<String, Long> severities;

    /** Alertas detectadas en ese bucket */
    private List<SummaryInsightsDto.Alert> alerts;

    /** contexto rápido (top1 del rango) */
    private String topSystem;
    private String topErrorKey;

    private String fingerprint;

    // Operación/flujo de atención (si lo usas)
    private String state;        // OPEN / ACK / RESOLVED / etc.
    private Instant ackedAt;
    private String ackedBy;
    private Instant resolvedAt;
    private String resolvedBy;

    public String ackedAtLocal;
    public String resolvedAtLocal;
}