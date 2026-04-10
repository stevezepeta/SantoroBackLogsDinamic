package backlogs.dinamico.controller.ai;

import backlogs.dinamico.api.dto.DailyManagerPrettyDto;
import backlogs.dinamico.service.ai.AiDailyManagerService;
import backlogs.dinamico.service.ai.AiLlmPrettyService;
import backlogs.dinamico.service.ai.EvaDeepAnalysisService;
import backlogs.dinamico.service.ai.dto.AiTicketDraftDto;
import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/ai/llm/assist/manager/daily")
@RequiredArgsConstructor
public class AiDailyManagerLlmController {

    private final AiDailyManagerService dailyManagerService;
    private final AiLlmPrettyService llmPrettyService;
    private final EvaDeepAnalysisService deepAnalysisService;

    @GetMapping("/pretty")
    public DailyManagerPrettyDto pretty(
            @RequestParam(defaultValue = "1") int days,
            @RequestParam(defaultValue = "America/Mexico_City") String tz,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "5") int maxTickets,
            @RequestParam(required = false) String system
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null)
            throw new IllegalStateException("tenant_not_resolved");

        DailyManagerSummaryDto mgr = dailyManagerService.buildManagerSummary(
                tenantId, days, tz, from, to, system, null);

        // ── análisis profundo del evento dominante ─────────────────
        Instant rangeFrom = (from != null) ? from
                : Instant.now().minusSeconds((long) days * 24 * 60 * 60);
        Instant rangeTo   = (to != null) ? to : Instant.now();

        mgr.aiDeepAnalysis = deepAnalysisService.analyze(tenantId, system, rangeFrom, rangeTo, mgr);
        // ─────────────────────────────────────────────────────────────────

        List<AiTicketDraftDto> drafts = dailyManagerService.buildTicketDraftsFromManagerSummary(
                tenantId, days, tz, from, to, system, maxTickets);

        return llmPrettyService.prettyDailyManager(mgr, drafts);
    }

}
