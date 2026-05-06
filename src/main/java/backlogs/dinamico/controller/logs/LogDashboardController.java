package backlogs.dinamico.controller.logs;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.logs.*;
import backlogs.dinamico.service.logs.LogDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;


@Tag(name = "Dashboard", description = "Agregaciones para el dashboard — reemplaza el GET /all en el frontend.")
@RestController
@RequestMapping("/api/logs/dashboard")
@RequiredArgsConstructor
public class LogDashboardController {

    private final LogDashboardService dashboardService;

    @Operation(summary = "Stats del dashboard",
            description = "Totales y distribuciones — alimenta todas las cards y gráficas de barras.")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<DashboardStatsDto> stats(
            Authentication auth,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.ok("Dashboard stats", "dashboard_stats",
                dashboardService.stats(auth, system, from, to));
    }

    @Operation(summary = "Series de tiempo para gráficas de líneas")
    @GetMapping("/series")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<DashboardSeriesDto> series(
            Authentication auth,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.ok("Dashboard series", "dashboard_series",
                dashboardService.series(auth, system, from, to));
    }

    @Operation(summary = "Métricas HTTP para gráfica radar")
    @GetMapping("/http")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<DashboardHttpDto> http(
            Authentication auth,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.ok("Dashboard HTTP", "dashboard_http",
                dashboardService.http(auth, system, from, to));
    }

    /**
     * Puntos geográficos para el mapa.
     * Alimenta "Mapa de Logs (geo)" — mapa de puntos y mapa de calor.
     * Solo retorna datos si los logs tienen el campo geo.coordinates.
     * Limitado a 2000 puntos para no saturar el mapa.
     */
    @Operation(summary = "Coordenadas geo para el mapa de logs")
    @GetMapping("/geo")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<DashboardGeoDto> geo(
            Authentication auth,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.ok("Dashboard geo", "dashboard_geo",
                dashboardService.geo(auth, system, from, to));
    }

    @Operation(summary = "Estado de salud de todos los sistemas — últimas 24h")
    @GetMapping("/systems-health")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<List<SystemHealthDto>> systemsHealth(Authentication auth) {
        return ApiResponse.ok("Systems health", "systems_health",
                dashboardService.systemsHealth(auth));
    }
}