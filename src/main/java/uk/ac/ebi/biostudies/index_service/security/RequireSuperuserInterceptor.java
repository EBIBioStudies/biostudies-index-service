package uk.ac.ebi.biostudies.index_service.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.ac.ebi.biostudies.index_service.auth.AuthenticationContext;
import uk.ac.ebi.biostudies.index_service.auth.User;

@Slf4j
@Component
public class RequireSuperuserInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    User currentUser = AuthenticationContext.getCurrentUser();

    if (currentUser == null) {
      log.warn("Unauthenticated access attempt to admin endpoint: {}", request.getRequestURI());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
      return false;
    }

    if (!currentUser.isSuperUser()) {
      log.warn(
          "Non-superuser {} attempted to access admin endpoint: {}",
          currentUser.getLogin(),
          request.getRequestURI());
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Superuser access required");
      return false;
    }

    log.debug("Superuser access granted to: {}", currentUser.getLogin());
    return true;
  }
}
