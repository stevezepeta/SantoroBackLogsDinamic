package backlogs.dinamico.service.alerting;

import backlogs.dinamico.model.alerting.AlertIncident;
import backlogs.dinamico.repository.alerting.AlertIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AlertIncidentService {

    private final AlertIncidentRepository repository;

    public Page<AlertIncident> listByTenant(ObjectId tenantId, String status, Pageable pageable) {
        if (StringUtils.hasText(status)) {
            return repository.findByTenantIdAndStatus(tenantId, status.trim().toUpperCase(), pageable);
        }
        return repository.findByTenantId(tenantId, pageable);
    }

    public AlertIncident resolveFlexible(ObjectId tenantId, String rawId, ObjectId objectId, String rootCause, String solutionComment, String resolvedBy) {
        AlertIncident incident = null;

        // Paso 1: Buscar por ObjectId nativo de MongoDB (es la búsqueda más rápida y directa)
        if (objectId != null) {
            if (tenantId != null) {
                incident = repository.findByIdAndTenantId(objectId, tenantId).orElse(null);
            }
            if (incident == null) {
                incident = repository.findById(objectId).orElse(null);
            }
        }

        // Paso 2: Si no se encontró por ObjectId, buscar por identificador en context
        if (incident == null && tenantId != null) {
            incident = repository.findByTenantIdAndContextIdentifier(tenantId, rawId).orElse(null);
        }

        // Paso 3: Si aún no se encuentra, lanzar excepción descriptiva
        if (incident == null) {
            throw new IllegalArgumentException("No se encontró el incidente con el ID especificado: " + rawId);
        }

        // 3. Actualizar campos de resolución
        incident.setStatus("RESOLVED");
        incident.setRootCause(rootCause);
        incident.setSolutionComment(solutionComment);
        incident.setResolvedBy(resolvedBy);
        incident.setResolvedAt(Instant.now());
        incident.setClosedAt(Instant.now());

        return repository.save(incident);
    }
}
