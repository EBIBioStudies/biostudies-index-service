package uk.ac.ebi.biostudies.index_service.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Security configuration loaded from classpath:security.properties.
 *
 * <p>This is a facade that aggregates various security-related configuration properties from
 * different prefixes in the security.properties file.
 */
@Getter
@Component
@PropertySource("classpath:security.properties")
public class SecurityConfig {

  private final String profileUrl;
  private final String loginUrl;
  private final String adminIpAllowList;
  private final String proxyHost;
  private final Integer proxyPort;
  private final String partialUpdateRestToken;
  private final String backendBaseURL;

  public SecurityConfig(
      @Value("${auth.profileUrl:}") String profileUrl,
      @Value("${auth.loginUrl:}") String loginUrl,
      @Value("${index.admin.ip.allow.list:127.0.0.1,0:0:0:0:0:0:0:1,localhost}")
          String adminIpAllowList,
      @Value("${http.proxy.host:}") String proxyHost,
      @Value("${http.proxy.port:#{null}}") Integer proxyPort,
      @Value("${partial.update.rest.token:test}") String partialUpdateRestToken,
      @Value("${backend.baseUrl:}") String backendBaseURL) {
    this.profileUrl = profileUrl;
    this.loginUrl = loginUrl;
    this.adminIpAllowList = adminIpAllowList;
    this.proxyHost = proxyHost;
    this.proxyPort = proxyPort;
    this.partialUpdateRestToken = partialUpdateRestToken;
    this.backendBaseURL = backendBaseURL;
  }

  public List<String> getAdminIpAllowListAsList() {
    if (adminIpAllowList == null || adminIpAllowList.trim().isEmpty()) {
      return Collections.emptyList();
    }
    return Arrays.stream(adminIpAllowList.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public boolean isProxyConfigured() {
    return proxyHost != null && !proxyHost.isEmpty() && proxyPort != null;
  }

  public String getProxyAddress() {
    return isProxyConfigured() ? proxyHost + ":" + proxyPort : "";
  }
}
