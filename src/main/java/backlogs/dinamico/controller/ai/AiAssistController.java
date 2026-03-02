package backlogs.dinamico.controller.ai;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.service.ai.AiDailyManagerService;
import backlogs.dinamico.service.ai.ErrorCorrelationService;
import backlogs.dinamico.service.ai.HourlySummaryService;
import backlogs.dinamico.service.ai.dto.AiTicketDraftDto;
import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;
import backlogs.dinamico.service.ai.dto.ErrorCorrelationDto;
import backlogs.dinamico.service.ai.dto.ErrorCorrelationReq;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Validated
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(value = "/api/ai/assist", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AiAssistController {

    private static final String DEFAULT_TZ = "America/Mexico_City";

    private final HourlySummaryService hourlySummaryService;
    private final AiDailyManagerService dailyManagerService;
    private final ErrorCorrelationService errorCorrelationService;

    // Resumen diario para gerente
    @GetMapping("/manager/daily")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<DailyManagerSummaryDto> managerDailySummary(
            Authentication auth, HttpServletRequest req,
            @RequestParam(defaultValue = "1") @Min(1) @Max(30) int days,
            @RequestParam(defaultValue = DEFAULT_TZ) String tz,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
            ) {

        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        DailyManagerSummaryDto dto = dailyManagerService.buildManagerSummary(tenantId, days, tz, from, to);
        return ApiResponse.ok(
                "Resumen diario gerente",
                "ai_manager_daily_summary",
                dto
        );
    }

    // Ticket drafts desde manager summary
    @PostMapping("/manager/daily/ticket-draft")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<List<AiTicketDraftDto>> managerDailyTicketDrafts(
            Authentication auth, HttpServletRequest req,
            @RequestParam(defaultValue = "1") @Min(1) @Max(30) int days,
            @RequestParam(defaultValue = DEFAULT_TZ) String tz,
            @RequestParam(required = false) String system,
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int maxTickets,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {

        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        List<AiTicketDraftDto> drafts = dailyManagerService.buildTicketDraftsFromManagerSummary(
                tenantId, days, tz, from, to, system, maxTickets
        );

        return ApiResponse.ok("Daily manager ticket drafts", "ai_manager_daily_ticket_drafts", drafts);
    }

    @PostMapping("/errors/correlate")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<ErrorCorrelationDto> correlateErrors(
            Authentication auth,
            HttpServletRequest req,
            @RequestBody ErrorCorrelationReq body
            ) {

        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        ErrorCorrelationDto dto = errorCorrelationService.correlateWithEvidence(tenantId, body);

        return ApiResponse.ok("Correlacion de errores", "ai_errors_correlate", dto);
    }

}
