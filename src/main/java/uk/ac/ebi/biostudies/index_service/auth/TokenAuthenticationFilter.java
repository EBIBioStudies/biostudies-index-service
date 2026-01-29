package uk.ac.ebi.biostudies.index_service.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
  public static final String X_SESSION_TOKEN = "X-Session-Token";
  // Endpoints that don't require authentication
  private static final Set<String> PUBLIC_ENDPOINTS = Set.of("/health");
  private final AuthenticationService authenticationService;

  public TokenAuthenticationFilter(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String token = extractToken(request);

      if (token != null) {
        User user = authenticationService.authenticateToken(token);
        if (user != null) {
          // Store user in thread-local for this request
          AuthenticationContext.setCurrentUser(user);
          log.debug("Authenticated user: {}", user.getLogin());
        }
      }

      filterChain.doFilter(request, response);
    } finally {
      // Always clear thread-local after request completes
      AuthenticationContext.clear();
    }
  }

  /** Extracts token from X-Session-Token header or Authorization Bearer header */
  private String extractToken(HttpServletRequest request) {
    // First check X-Session-Token header (backward compatibility)
    String token = request.getHeader(X_SESSION_TOKEN);
    if (token != null && !token.isEmpty()) {
      return token;
    }

    // Also support standard Authorization Bearer header
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }

    return null;
  }
}
