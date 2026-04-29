package backlogs.dinamico.controller.logs;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.service.logs.DeviceRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Devices", description = "Registro de dispositivos detectados por logs")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRegistryService deviceRegistryService;

    @Operation(summary = "Listar dispositivos",
               description = "Devuelve el resumen de dispositivos del tenant. Filtra opcionalmente por sistema y/o estado (ONLINE | OFFLINE).")
    @GetMapping
    public ApiResponse<?> getAllDevices(
            Authentication auth,
            @Parameter(description = "Nombre del sistema a filtrar (ej: ACCESO, CAMARAS). Case-insensitive.")
            @RequestParam(required = false) String system,
            @Parameter(description = "Estado del dispositivo: ONLINE u OFFLINE.")
            @RequestParam(required = false) String status
    ) {
        AuthUser user = (AuthUser) auth.getPrincipal();
        var summary = deviceRegistryService.getSummary(user.getTenantId(), system, status);
        return ApiResponse.ok("Dispositivos", "devices_summary", summary);
    }

}
