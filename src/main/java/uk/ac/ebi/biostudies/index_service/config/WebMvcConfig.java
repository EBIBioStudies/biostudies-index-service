package uk.ac.ebi.biostudies.index_service.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.ac.ebi.biostudies.index_service.security.RequireAuthenticationInterceptor;
import uk.ac.ebi.biostudies.index_service.security.RequireSuperuserInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Autowired
  private RequireAuthenticationInterceptor requireAuthenticationInterceptor;

  @Autowired
  private RequireSuperuserInterceptor requireSuperuserInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // Protect index management endpoints - require authentication
    registry.addInterceptor(requireAuthenticationInterceptor)
        .addPathPatterns("/api/index/**")
        .addPathPatterns("/api/admin/**");

    // Protect administrative endpoints - require superuser
    registry.addInterceptor(requireSuperuserInterceptor)
        .addPathPatterns("/api/index/rebuild")
        .addPathPatterns("/api/index/delete")
        .addPathPatterns("/api/admin/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOrigins("http://localhost:8080")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
  }
}
