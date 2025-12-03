package backlogs.dinamico.security;

import java.io.IOException;

import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantAccessDeniedHandler implements AccessDeniedHandler {
  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
                     org.springframework.security.access.AccessDeniedException ex) throws IOException {
    response.setStatus(400);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"Tenant is required for this endpoint\"}");
  }
}
