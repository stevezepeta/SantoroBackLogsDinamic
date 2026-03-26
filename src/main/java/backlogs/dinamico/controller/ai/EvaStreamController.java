package backlogs.dinamico.controller.ai;

import backlogs.dinamico.api.dto.EvaStreamRequest;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.service.ai.EvaStreamService;
import backlogs.dinamico.service.ai.HourlySummaryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI - Eva Stream", description = "Streaming SSE de respuestas de Eva.")
@RestController
@RequestMapping("/api/ai/eva")
@RequiredArgsConstructor
public class EvaStreamController {

    private final EvaStreamService     evaStreamService;
    private final HourlySummaryService hourlySummaryService;  // resuelve tenantId igual que el resto

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public SseEmitter stream(
            Authentication auth,
            HttpServletRequest req,
            @RequestParam String message,
            @RequestParam(required = false)                                       String system,
            @RequestParam(required = false, defaultValue = "daily")               String granularity,
            @RequestParam(required = false, defaultValue = "30")                  int    days,
            @RequestParam(required = false, defaultValue = "24")                  int    hours,
            @RequestParam(required = false, defaultValue = "America/Mexico_City") String tz
    ) {
        // Mismo patrón que AiAlertsController.list() — tenantId del JWT
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        String   actor    = resolveActorName(auth);

        // ── Extraer scope del usuario autenticado ─────────────────────────────
        AuthUser authUser = (auth != null && auth.getPrincipal() instanceof AuthUser au) ? au : null;
        java.util.List<String> allowedSystems = authUser != null && authUser.getAllowedSystems() != null
                ? authUser.getAllowedSystems().stream().toList()
                : java.util.List.of();
        boolean isOrgWide = authUser != null && authUser.isOrgWide()
                && (authUser.getAllowedSystems() == null || authUser.getAllowedSystems().isEmpty());

        // Si el usuario especificó un system en el request, verificar que tenga acceso
        String resolvedSystem = system;
        if (resolvedSystem != null && !isOrgWide && !allowedSystems.isEmpty()
                && !allowedSystems.contains(resolvedSystem.toUpperCase())) {
            resolvedSystem = allowedSystems.get(0); // usar el primero permitido
        }
        // Si no especificó system y tiene un solo sistema asignado, usarlo automáticamente
        if (resolvedSystem == null && !allowedSystems.isEmpty() && allowedSystems.size() == 1) {
            resolvedSystem = allowedSystems.get(0);
        }

        SseEmitter emitter = new SseEmitter(60_000L);

        EvaStreamRequest streamReq = EvaStreamRequest.builder()
                .message(message)
                .system(resolvedSystem)
                .granularity(granularity)
                .days(days)
                .hours(hours)
                .tz(tz)
                .tenantId(tenantId)
                .actorName(actor)
                .allowedSystems(allowedSystems)
                .isOrgWide(isOrgWide)
                .build();

        executor.execute(() -> {
            try {
                evaStreamService.streamResponse(streamReq, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // Mismo patrón que actorEmail() en AiAlertsController
    private static String resolveActorName(Authentication auth) {
        if (auth == null) return "unknown";
        Object p = auth.getPrincipal();
        if (p instanceof AuthUser au)
            return au.getName() != null ? au.getName() : au.getEmail();
        if (p instanceof org.springframework.security.core.userdetails.UserDetails ud)
            return ud.getUsername();
        return String.valueOf(p);
    }
}