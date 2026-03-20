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

/**
 * Endpoints de agregación para el Dashboard.
 *
 * Todos los endpoints aceptan:
 *   ?system=TRUSTVALUE   — filtra por system (opcional si el usuario es orgWide)
 *   &from=2026-01-01T00:00:00Z  — inicio del rango (default: últimos 30 días)
 *   &to=2026-03-19T00:00:00Z    — fin del rango (default: ahora)
 *
 * Retornan datos agregados — no documentos individuales.
 * Tiempo de respuesta: <100ms con índices correctos, independiente del volumen.
 */
@Tag(name = "Dashboard", description = "Agregaciones para el dashboard — reemplaza el GET /all en el frontend.")
@RestController
@RequestMapping("/api/logs/dashboard")
@RequiredArgsConstructor
public class LogDashboardController {

    private final LogDashboardService dashboardService;

    /**
     * Cards del top: total, top eventTypes, outcome, severity, status, tags,
     * locaciones, actores, environments.
     *
     * Reemplaza: el frontend que clasifica el GET /all en JS.
     */
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

    /**
     * Series de tiempo: eventos por día, semana, mes + status × día.
     *
     * Alimenta:
     *   - "Eventos por Día y Acumulado"
     *   - "Eventos por Semana y Acumulado"
     *   - "Eventos por Mes y Acumulado"
     *   - Gráfica de líneas "Estatus — Comportamiento a través del tiempo"
     */
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

    /**
     * Métricas HTTP: latencia p95 por statusCode × método.
     * Alimenta la gráfica radar "HTTP — Latencia p95".
     * Solo retorna datos si los logs tienen el campo http.
     */
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
}