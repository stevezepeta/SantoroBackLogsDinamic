package backlogs.dinamico.security;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import backlogs.dinamico.tenant.TenantContext;

public class TenantPresentAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
  @Override
  public AuthorizationDecision check(Supplier authentication, RequestAuthorizationContext ctx) {
    var t = TenantContext.get();
    boolean ok = (t != null && t.getTenantId() != null);
    return new AuthorizationDecision(ok);
  }
}
