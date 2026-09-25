package backlogs.dinamico.controller.config;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.analytics.FunnelTemplate;
import backlogs.dinamico.repository.analytics.FunnelTemplateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Configuración de filtros dinámicos del Dashboard.
 *
 * Expone en {@code GET /api/filters/config?system=TRUSTVALUE} la configuración
 * de filtros disponibles para un sistema (eventos/etapas configurables),
 * leída de la colección {@code funnel_templates} en MongoDB.
 */
@Tag(name = "Filters Config", description = "Configuración dinámica de filtros del Dashboard por sistema")
@RestController
@RequestMapping("/api/filters")
@RequiredArgsConstructor
@CrossOrigin(origins = "https://dashboard.grupo-santoro.com.mx", allowCredentials = "true")
public class FilterConfigController {

    private final FunnelTemplateRepository funnelTemplateRepository;

    @Operation(
            summary = "Configuración de filtros de un sistema",
            description = "Devuelve la configuración dinámica de filtros (eventos/etapas) " +
                    "para el sistema indicado en el parámetro 'system'."
    )
    @GetMapping("/config")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<FunnelTemplate> getFilterConfig(@RequestParam("system") String system) {
        FunnelTemplate template = funnelTemplateRepository
                .findBySystemNameAndActive(system, true)
                .or(() -> funnelTemplateRepository.findBySystemName(system))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No hay configuración de filtros para el sistema: " + system));

        return ApiResponse.ok("Configuración de filtros", "filters_config", template);
    }
}
