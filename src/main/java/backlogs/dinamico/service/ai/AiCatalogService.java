package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.catalog.SystemApp;
import backlogs.dinamico.repository.catalog.SystemAppRepository;
import backlogs.dinamico.service.ai.dto.SystemCatalogItemDto;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiCatalogService {

    private final SystemAppRepository systemAppRepository;

    /**
     * Lista los sistemas visibles para el usuario actual directamente desde la
     * colección de catálogo, sin escanear {@code log_events}.
     *
     * @param allowedSystems null o vacío = sin restricción (ORG_ADMIN/ORG_OWNER);
     *                       con elementos = filtrar sistema IN allowedSystems (VIEWER, etc.)
     */
    public List<SystemCatalogItemDto> listSystems(ObjectId tenantId, String q, int limit,
                                                   List<String> allowedSystems) {
        if (tenantId == null) return List.of();

        int safeLimit = Math.min(Math.max(limit, 1), 200);
        String query = StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;

        Page<SystemApp> page;
        if (allowedSystems == null || allowedSystems.isEmpty()) {
            page = systemAppRepository.findByTenantId(tenantId, Pageable.unpaged());
        } else {
            List<String> normalizedCodes = allowedSystems.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .distinct()
                    .collect(Collectors.toList());
            if (normalizedCodes.isEmpty()) return List.of();
            page = systemAppRepository.findByTenantIdAndCodeIn(tenantId, normalizedCodes, Pageable.unpaged());
        }

        return page.getContent().stream()
                .filter(app -> StringUtils.hasText(app.getCode()))
                .filter(app -> query == null || app.getCode().toLowerCase(Locale.ROOT).contains(query)
                        || (app.getName() != null && app.getName().toLowerCase(Locale.ROOT).contains(query)))
                .limit(safeLimit)
                .map(app -> new SystemCatalogItemDto(app.getCode(), 0L))
                .collect(Collectors.toList());
    }
}