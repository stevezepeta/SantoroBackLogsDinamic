package backlogs.dinamico.service.logs;

import backlogs.dinamico.api.dto.LogIngestBatchReq;
import backlogs.dinamico.api.dto.LogIngestReq;
import backlogs.dinamico.model.log.LogEntry;
import backlogs.dinamico.repository.log.LogRepository;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LogIngestService {

    private final LogRepository repo;

    public LogEntry ingestOne(LogIngestReq req) {

        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new IllegalArgumentException("missing_tenant_ctx");

        Instant now = Instant.now();
        Instant ts = req.getTimestamp() == null ? now : req.getTimestamp();

        String level = StringUtils.hasText(req.getLevel()) ? req.getLevel() : "INFO";

        LogEntry e = LogEntry.builder()
                .tenantId(tenantId)
                .timestamp(ts)
                .level(level)
                .message(req.getMessage())

                .processType(req.getProcessType())
                .device(req.getDevice())
                .scanDevice(req.getScanDevice())
                .scanType(req.getScanType())
                .officeId(req.getOfficeId())
                .personId(req.getPersonId())
                .baseCode(req.getBaseCode())
                .errorCode(req.getErrorCode())
                .sessionToken(req.getSessionToken())

                .system(req.getSystem())
                .environment(req.getEnvironment())

                .extra(req.getExtra())
                .ingestAt(now)
                .build();

        return repo.save(e);
    }

    public List<LogEntry> ingestBatch(LogIngestBatchReq batch) {
        return batch.getItems().stream().map(this::ingestOne).toList();
    }

}
