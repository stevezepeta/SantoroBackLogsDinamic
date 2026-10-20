package backlogs.dinamico.controller.analytics;

import backlogs.dinamico.service.analytics.DataDiagnosticsService;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Validación de permisos del endpoint M0: el método debe exigir la autoridad
 * PERM_LOG_READ vía @PreAuthorize. Prueba standalone (sin contexto Spring):
 * intercepta la invocación con el mismo AuthorizationManager que usa
 * @EnableMethodSecurity en producción.
 */
class IntelligenceDiagnosticsControllerSecurityTest {

    private AuthorizationManagerBeforeMethodInterceptor interceptor;
    private IntelligenceDiagnosticsController controller;

    @BeforeEach
    void setUp() {
        DataDiagnosticsService service = mock(DataDiagnosticsService.class);
        controller = new IntelligenceDiagnosticsController(service);

        PreAuthorizeAuthorizationManager manager = new PreAuthorizeAuthorizationManager();
        manager.setExpressionHandler(new DefaultMethodSecurityExpressionHandler());
        interceptor = AuthorizationManagerBeforeMethodInterceptor.preAuthorize(manager);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MethodInvocation invocation() {
        Method method;
        try {
            method = IntelligenceDiagnosticsController.class
                    .getMethod("getCoverage", String.class, Integer.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        Method target = method;
        return new MethodInvocation() {
            @Override
            public Method getMethod() {
                return target;
            }

            @Override
            public Object[] getArguments() {
                return new Object[]{"TRUSTVALUE", 30};
            }

            @Override
            public Object getThis() {
                return controller;
            }

            @Override
            public Object proceed() {
                return null;
            }

            @Override
            public AccessibleObject getStaticPart() {
                return target;
            }
        };
    }

    @Test
    void userWithPermLogReadIsAuthorized() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "auditor", "n/a", List.of(new SimpleGrantedAuthority("PERM_LOG_READ"))));

        assertDoesNotThrow(() -> interceptor.invoke(invocation()));
    }

    @Test
    void userWithoutPermLogReadIsDenied() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "viewer", "n/a", List.of(new SimpleGrantedAuthority("PERM_LOG_VIEW_BASIC"))));

        assertThrows(AccessDeniedException.class, () -> interceptor.invoke(invocation()));
    }

    @Test
    void unauthenticatedUserIsDenied() {
        SecurityContextHolder.clearContext();

        // Sin autenticación Spring Security lanza AuthenticationCredentialsNotFoundException
        // (ambas son rechazos de autorización: la invocación nunca llega al controller).
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> interceptor.invoke(invocation()));
    }
}
