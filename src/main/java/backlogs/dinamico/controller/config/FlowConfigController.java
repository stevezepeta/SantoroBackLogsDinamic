package backlogs.dinamico.controller.config;

import backlogs.dinamico.service.config.FlowConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class FlowConfigController {

    private final FlowConfigService service;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/flujo/{flowId}/modulos")
    public ResponseEntity<Map<String, Object>> getModules(@PathVariable("flowId") String flowId) {

        var data = service.getModulesByFlow(flowId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
        ));
    }

}
