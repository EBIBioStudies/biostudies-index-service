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
public class RequireAuthenticationInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    User currentUser = AuthenticationContext.getCurrentUser();

    if (currentUser == null) {
      log.warn("Unauthorized access attempt to: {}", request.getRequestURI());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
      return false;
    }

    log.debug("Authenticated request from user: {}", currentUser.getLogin());
    return true;
  }
}
