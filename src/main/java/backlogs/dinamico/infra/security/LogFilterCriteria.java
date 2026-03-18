package backlogs.dinamico.infra.security;

import backlogs.dinamico.model.core.UserRole;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Utility para aplicar los filtros de visibilidad de logs del usuario
 * autenticado a cualquier Criteria de MongoDB.
 *
 * Uso en cualquier servicio de logs:
 *
 *   Criteria c = Criteria.where("tenant_id").is(tenantId);
 *   c = LogFilterCriteria.apply(c);   // ← una línea
 *   // Ya listo — si el usuario es VIEWER con filtros, solo verá lo permitido
 */
public final class LogFilterCriteria {

    private LogFilterCriteria() {}

    /**
     * Aplica los logFilters del usuario autenticado al Criteria dado.
     * Si el usuario no tiene filtros activos, devuelve el Criteria original sin cambios.
     *
     * @param base Criteria base (ya debe tener tenant_id y otros filtros del negocio)
     * @return Criteria con restricciones de visibilidad aplicadas
     */
    public static Criteria apply(Criteria base) {
        AuthUser user = currentUser();
        if (user == null || !user.hasLogFilters()) return base;

        return apply(base, user.getLogFilters());
    }

    /**
     * Versión explícita — recibe los filtros directamente.
     * Útil cuando ya tienes el AuthUser o el LogFilter en mano.
     */
    public static Criteria apply(Criteria base, UserRole.LogFilter lf) {
        if (lf == null || lf.isEmpty()) return base;

        List<Criteria> restrictions = new ArrayList<>();

        // outcome
        Set<String> outcomes = lf.getAllowedOutcomes();
        if (outcomes != null && !outcomes.isEmpty()) {
            restrictions.add(Criteria.where("outcome").in(outcomes));
        }

        // status
        Set<String> statuses = lf.getAllowedStatuses();
        if (statuses != null && !statuses.isEmpty()) {
            restrictions.add(Criteria.where("status").in(statuses));
        }

        // severity
        Set<String> severities = lf.getAllowedSeverities();
        if (severities != null && !severities.isEmpty()) {
            restrictions.add(Criteria.where("severity").in(severities));
        }

        // eventType
        Set<String> eventTypes = lf.getAllowedEventTypes();
        if (eventTypes != null && !eventTypes.isEmpty()) {
            restrictions.add(Criteria.where("eventType").in(eventTypes));
        }

        if (restrictions.isEmpty()) return base;

        // Combinar base + restricciones con andOperator
        List<Criteria> all = new ArrayList<>();
        all.add(base);
        all.addAll(restrictions);

        return new Criteria().andOperator(all.toArray(new Criteria[0]));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        return (p instanceof AuthUser au) ? au : null;
    }
}