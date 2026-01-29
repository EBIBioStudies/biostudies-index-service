package uk.ac.ebi.biostudies.index_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uk.ac.ebi.biostudies.index_service.auth.TokenAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityAccessConfig {

  private final TokenAuthenticationFilter tokenAuthenticationFilter;

    public SecurityAccessConfig(TokenAuthenticationFilter tokenAuthenticationFilter) {
    this.tokenAuthenticationFilter = tokenAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())  // Disable CSRF for REST API
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // Stateless sessions
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/health").permitAll()  // Public health endpoint
            .anyRequest().permitAll()  // Allow all requests (your interceptors will handle auth)
        )
        .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);  // Add your custom filter

    return http.build();
  }
}