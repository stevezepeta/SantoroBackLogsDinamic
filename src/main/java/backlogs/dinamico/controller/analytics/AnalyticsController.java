package backlogs.dinamico.controller.analytics;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.analytics.FunnelResponseDto;
import backlogs.dinamico.service.analytics.FunnelAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Controlador REST para endpoints de analítica avanzada.
 * Transforma logs de auditoría en métricas estratégicas de rendimiento.
 */
@Tag(name = "Analytics", description = "Analítica estratégica de procesos - KPIs y métricas de conversión")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final FunnelAnalyticsService funnelAnalyticsService;

    /**
     * Endpoint de análisis de embudos de conversión (Funnel Analysis).
     * 
     * Devuelve las métricas de conversión y abandono de usuarios a través de
     * una serie de pasos secuenciales basados en eventType, agrupados por caseId únicos.
     */
    @Operation(
            summary = "Análisis de Embudo de Conversión (Funnel)",
            description = """
                    Calcula métricas de conversión y abandono para un flujo de proceso específico.
                    
                    **Métricas Calculadas:**
                    - `count`: Casos únicos que alcanzaron cada paso
                    - `conversionRate`: % de conversión respecto al paso inicial (paso 1 = 100%)
                    - `dropRate`: % de abandono respecto al paso anterior (paso 1 = 0%)
                    
                    **Casos de Uso:**
                    - Identificar cuellos de botella en flujos de usuario
                    - Medir eficiencia operativa de procesos
                    - Detectar pasos con alta tasa de abandono
                    - Generar reportes ejecutivos de rendimiento
                    
                    **Ejemplo de Uso:**
                    ```
                    GET /api/analytics/funnel/CITA_GUYANA?from=2026-01-01T00:00:00Z&to=2026-06-15T23:59:59Z
                    ```
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Funnel calculado exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Ejemplo de respuesta exitosa",
                                    value = """
                                    {
                                      "status": "ok",
                                      "message": "Funnel analysis completed",
                                      "messageKey": "funnel_analysis",
                                      "data": {
                                        "system": "CITA_GUYANA",
                                        "funnelName": "Flujo de Trámite de Cita",
                                        "summary": {
                                          "totalStarted": 100,
                                          "totalCompleted": 40,
                                          "globalConversionRate": 40.0
                                        },
                                        "steps": [
                                          {
                                            "step": 1,
                                            "label": "Inicio de Sesión",
                                            "eventType": "AUTH_LOGIN",
                                            "count": 100,
                                            "conversionRate": 100.0,
                                            "dropRate": 0.0
                                          },
                                          {
                                            "step": 2,
                                            "label": "Validación de Registro",
                                            "eventType": "CONSULTA_ESTADO_REGISTRO",
                                            "count": 80,
                                            "conversionRate": 80.0,
                                            "dropRate": 20.0
                                          },
                                          {
                                            "step": 3,
                                            "label": "Cita Completada",
                                            "eventType": "RESERVA_DE_CITA",
                                            "count": 40,
                                            "conversionRate": 40.0,
                                            "dropRate": 50.0
                                          }
                                        ]
                                      }
                                    }
                                    """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Sistema no configurado para análisis de funnel"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Usuario no tiene acceso al sistema solicitado"
            )
    })
    @GetMapping("/funnel/{systemName}")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<FunnelResponseDto> getFunnelAnalysis(
            Authentication auth,
            
            @Parameter(
                    description = "Nombre del sistema a analizar (ej: CITA_GUYANA, TICKETS, TRUSTVALUE)",
                    required = true,
                    example = "CITA_GUYANA"
            )
            @PathVariable String systemName,
            
            @Parameter(
                    description = "Fecha de inicio del rango de análisis (formato ISO-8601)",
                    example = "2026-01-01T00:00:00Z"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            
            @Parameter(
                    description = "Fecha de fin del rango de análisis (formato ISO-8601)",
                    example = "2026-06-15T23:59:59Z"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        FunnelResponseDto funnel = funnelAnalyticsService.calculateFunnel(
                auth, systemName, from, to
        );
        
        return ApiResponse.ok(
                "Funnel analysis completed",
                "funnel_analysis",
                funnel
        );
    }

    /**
     * Endpoint para listar los sistemas con configuración de funnel activos.
     * 
     * NOTA: La ruta está separada (/funnel-systems/available) para evitar
     * conflictos con la ruta parametrizada /funnel/{systemName}. Si usáramos
     * /funnel/available-systems, Spring la confundiría con systemName="available-systems".
     * 
     * @return ApiResponse con la lista de sistemas disponibles
     */
    @Operation(
            summary = "Listar Sistemas con Funnel Configurado",
            description = """
                    Retorna la lista de sistemas que tienen análisis de funnel configurado y activo.
                    
                    **Uso:**
                    Útil para descubrimiento dinámico de sistemas disponibles en el frontend.
                    
                    **Ejemplo de respuesta:**
                    ```json
                    {
                      "status": "ok",
                      "data": ["CITA_GUYANA", "TICKETS", "TRUSTVALUE"]
                    }
                    ```
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de sistemas recuperada exitosamente"
            )
    })
    @GetMapping("/funnel-systems/available")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<List<String>> getAvailableFunnelSystems() {
        List<String> systems = funnelAnalyticsService.getAvailableSystems();
        
        return ApiResponse.ok(
                "Available funnel systems retrieved",
                "funnel_systems_list",
                systems
        );
    }
}

