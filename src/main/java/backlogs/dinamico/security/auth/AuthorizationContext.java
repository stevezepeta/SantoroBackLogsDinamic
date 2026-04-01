package backlogs.dinamico.security.auth;

import backlogs.dinamico.model.core.UserRole;
import lombok.Builder;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * Contexto de autorización del usuario autenticado.
 * Se construye en AuthorizationContextService y se empaqueta en el JWT.
 */
@Getter
@Builder
public class AuthorizationContext {

    private Set<String> roles;
    private Set<String> permissions;
    private boolean orgWide;
    private Set<String> allowedSystems;

    /**
     * Filtros de visibilidad de logs.
     * Si está vacío (isEmpty) el usuario ve todos los registros.
     * Si tiene valores, solo ve los registros que coincidan.
     *
     * Se construye como la UNIÓN de todos los logFilters de los UserRole
     * del usuario — si tiene varios roles, ve la suma de lo que cada
     * rol permite.
     */
    @Builder.Default
    private UserRole.LogFilter logFilters = new UserRole.LogFilter();
}