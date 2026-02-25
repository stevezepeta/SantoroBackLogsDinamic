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
@CompoundIndex(name = "uq_tenant_fingerprint", def = "{'tenantId': 1, 'fingerprint': 1}", unique = true)
public class AiAlertRecord {

    @Id
    private ObjectId id;
    private ObjectId tenantId;

    private String granularity;

    private Instant windowFrom;
    private Instant windowTo;
    private Instant bucketStart;
    private Instant createdAt;

    public String tz;
    public String windowFromLocal;
    public String windowToLocal;
    public String bucketStartLocal;
    public String createdAtLocal;

    private String status;
    private long total;
    private double errorRate;

    private Map<String, Long> severities;
    private List<SummaryInsightsDto.Alert> alerts;

    private String fingerprint;

    // Solo ejemplo de campos
    private String state;
    private Instant ackedAt;
    private String ackedBy;
    private Instant resolvedAt;
    private String resolvedBy;

    public String ackedAtLocal;
    public String resolvedAtLocal;
}
