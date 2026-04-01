package backlogs.dinamico.service.ai.dto;

import java.time.Instant;
import java.util.Map;

public class AiMetricDto {

    public String id;
    public String tenantId;

    public String granularity;
    public Instant bucketStart;
    public String system;

    public Instant windowFrom;
    public Instant windowTo;

    public Instant createdAt;
    public Instant updatedAt;

    public String tz;
    public String bucketStartLocal;
    public String windowFromLocal;
    public String windowToLocal;

    public long total;
    public long errorCount;
    public double errorRate;

    public Map<String, Long> severities;
    public String topErrorKey;

}
