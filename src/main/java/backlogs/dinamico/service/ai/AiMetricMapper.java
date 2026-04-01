package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.ai.AiMetricRecord;
import backlogs.dinamico.service.ai.dto.AiMetricDto;

public class AiMetricMapper {

    public static AiMetricDto toDto(AiMetricRecord r) {

        AiMetricDto d = new AiMetricDto();
        d.id =  r.getId() == null ? null : r.getTenantId().toHexString();
        d.tenantId = r.getTenantId() == null ? null : r.getTenantId().toHexString();

        d.granularity = r.getGranularity();
        d.bucketStart = r.getBucketStart();
        d.system = r.getSystem();

        d.windowFrom = r.getWindowFrom();
        d.windowTo = r.getWindowTo();

        d.createdAt = r.getCreatedAt();
        d.updatedAt = r.getUpdatedAt();

        d.tz = r.getTz();
        d.bucketStartLocal = r.getBucketStartLocal();
        d.windowFromLocal = r.getWindowFromLocal();
        d.windowToLocal = r.getWindowToLocal();

        d.total = r.getTotal();
        d.errorCount = r.getErrorCount();
        d.errorRate = r.getErrorRate();

        d.severities = r.getSeverities();
        d.topErrorKey = r.getTopErrorKey();
        return d;
    }

}
