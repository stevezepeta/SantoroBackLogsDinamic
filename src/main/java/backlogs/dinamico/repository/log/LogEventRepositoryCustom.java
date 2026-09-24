package backlogs.dinamico.repository.log;

import backlogs.dinamico.api.dto.analytics.UserQuickAuditResponse;

import java.util.List;

/**
 * Fragmento de repositorio para metodos custom de LogEvent (agregaciones, etc).
 * Los metodos se definen aqui y su implementacion va en LogEventRepositoryImpl.
 */
public interface LogEventRepositoryCustom {

    /**
     * Busca usuarios (actor) cuya actividad coincida con el termino de busqueda
     * y resume su actividad mas reciente en log_events.
     */
    List<UserQuickAuditResponse> quickAuditUsers(String query, int limit);
}