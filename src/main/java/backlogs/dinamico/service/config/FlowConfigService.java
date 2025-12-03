package backlogs.dinamico.service.config;

import backlogs.dinamico.model.config.FlowConfig;
import backlogs.dinamico.repository.config.FlowConfigRepository;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlowConfigService {

    private final FlowConfigRepository repo;

    // Extraemos los permisos del JWT
    private Set<String> currentUserPerms() {

        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();

        return auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
    }

    // Filtra los modulos
    @Cacheable(value = "flowModules", key = "T(java.util.Objects).hash(#flowId, T(backlogs.dinamico.infra.tenant.TenantContext).getTenantId())")
    public Map<String, Object> getModulesByFlow(String flowId) {

        ObjectId tenant = TenantContext.getTenantId();

        FlowConfig cfg = repo.findByTenantIdAndFlowId(tenant, flowId)
                .orElseThrow(() -> new NoSuchElementException("flow_not_found"));

        Set<String> perms = currentUserPerms();

        var visible = cfg.getModules() == null ? List.<FlowConfig.ModuleItem>of()
                : cfg.getModules().stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()))
                .filter(m -> m.getPerms() == null || m.getPerms().isEmpty() || perms.containsAll(m.getPerms()))
                .sorted(Comparator.comparing(FlowConfig.ModuleItem::getOrder))
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "nombre", m.getName(),
                        "icono", m.getIcon(),
                        "ruta", m.getRoute(),
                        "orden", m.getOrder(),
                        "activo", m.getActive()
                ))
                .toList();

        return Map.of(
                "Flujo", Map.of(
                        "id", cfg.getFlowId(),
                        "nombre", cfg.getName(),
                        "icono", cfg.getIcon(),
                        "color", cfg.getColor()
                ),
                "modulos", visible,
                "_meta", Map.of("generatedAt", Instant.now().toString())
        );

    }

}
