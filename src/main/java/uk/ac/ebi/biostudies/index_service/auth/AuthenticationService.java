package uk.ac.ebi.biostudies.index_service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import uk.ac.ebi.biostudies.index_service.config.SecurityConfig;

@Slf4j
@Component
public class AuthenticationService {
  public static final String X_SESSION_TOKEN = "X-Session-Token";
  private static final int REQUEST_TIMEOUT = 30000;
  ;
  private final Cache<String, User> userAuthCache;
  private final ObjectMapper mapper = new ObjectMapper();
  private final SecurityConfig securityConfig;

  public AuthenticationService(SecurityConfig securityConfig) {
    this.securityConfig = securityConfig;
    // Cache validated users for 60 minutes to reduce backend calls
    userAuthCache = CacheBuilder.newBuilder().expireAfterAccess(60, TimeUnit.MINUTES).build();
  }

  /**
   * Validates a token and returns the authenticated user. Returns null if token is invalid or null.
   */
  public User authenticateToken(String token) {
    if (token == null || token.isEmpty()) {
      return null;
    }

    // Check cache first
    User cachedUser = userAuthCache.getIfPresent(token);
    if (cachedUser != null && token.equals(cachedUser.getToken())) {
      log.debug("User found in cache: {}", cachedUser.getLogin());
      return cachedUser;
    }

    // Call backend /profile endpoint
    try {
      JsonNode responseJSON = sendAuthenticationCheckRequest(token);
      User user = createUserFromJSONResponse(responseJSON);
      if (user != null) {
        userAuthCache.put(token, user);
        log.debug("User authenticated: {}", user.getLogin());
      }
      return user;
    } catch (Exception e) {
      log.error("Authentication failed for token", e);
      return null;
    }
  }

  private JsonNode sendAuthenticationCheckRequest(String token) throws Exception {
    HttpClientBuilder clientBuilder = HttpClients.custom();

    if (securityConfig.isProxyConfigured()) {
      clientBuilder.setProxy(
          new HttpHost(securityConfig.getProxyHost(), securityConfig.getProxyPort()));
    }

    CloseableHttpClient httpClient =
        clientBuilder
            .setSSLSocketFactory(
                new SSLConnectionSocketFactory(
                    SSLContexts.custom()
                        .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                        .build(),
                    NoopHostnameVerifier.INSTANCE))
            .build();

    HttpGet httpGet = new HttpGet(securityConfig.getProfileUrl());
    httpGet.setConfig(
        RequestConfig.custom()
            .setConnectionRequestTimeout(REQUEST_TIMEOUT)
            .setConnectTimeout(REQUEST_TIMEOUT)
            .setSocketTimeout(REQUEST_TIMEOUT)
            .build());
    httpGet.setHeader(X_SESSION_TOKEN, token);

    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
      return mapper.readTree(EntityUtils.toString(response.getEntity()));
    } catch (Exception exception) {
      log.error("Problem sending authentication request", exception);
      throw exception;
    }
  }

  private User createUserFromJSONResponse(JsonNode responseJSON) throws IOException {
    if (responseJSON == null || !responseJSON.has("sessid")) {
      return null;
    }

    User user = new User();
    user.setFullName(
        responseJSON.has("fullname")
            ? responseJSON.get("fullname").textValue()
            : responseJSON.get("username").textValue());
    user.setLogin(responseJSON.get("username").textValue());
    user.setToken(responseJSON.get("sessid").textValue());
    user.setEmail(responseJSON.get("email").textValue());

    if (responseJSON.has("allow")
        && responseJSON.get("allow") != null
        && responseJSON.get("allow").isArray()) {
      String[] allow = mapper.convertValue(responseJSON.get("allow"), String[].class);
      String[] deny = mapper.convertValue(responseJSON.get("deny"), String[].class);

      Set<String> allowedSet = Sets.difference(Sets.newHashSet(allow), Sets.newHashSet(deny));
      user.setAllow(allowedSet.toArray(new String[0]));
      user.setDeny(deny);

      // Remove ~ characters
      user.setAllow(Stream.of(allow).map(item -> item.replaceAll("~", "")).toArray(String[]::new));
      user.setDeny(Stream.of(deny).map(item -> item.replaceAll("~", "")).toArray(String[]::new));
    }

    user.setSuperUser(responseJSON.get("superuser").asBoolean(false));

    return user;
  }

  public void invalidateToken(String token) {
    if (token != null) {
      userAuthCache.invalidate(token);
    }
  }
}
