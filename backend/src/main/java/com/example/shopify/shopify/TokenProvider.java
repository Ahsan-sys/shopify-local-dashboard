package com.example.shopify.shopify;

import com.example.shopify.config.ShopifySettings;
import com.example.shopify.web.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TokenProvider {
  private static final Logger log = LoggerFactory.getLogger(TokenProvider.class);
  private final ShopifyTransport transport;
  private final ShopifySettings settings;
  private final JsonMapper mapper;
  private final Clock clock;
  private String token;
  private Instant refreshAt = Instant.EPOCH;

  public TokenProvider(ShopifyTransport transport, ShopifySettings settings, JsonMapper mapper, Clock clock) {
    this.transport = transport;
    this.settings = settings;
    this.mapper = mapper;
    this.clock = clock;
  }

  public synchronized String getToken() {
    if (token != null && clock.instant().isBefore(refreshAt)) {
      log.info("Authentication: reusing cached token");
      return token;
    }
    log.info("Authentication: requesting a new Shopify token");
    Instant requestedAt = clock.instant();

    var form = "grant_type=client_credentials&client_id="+ encode(settings.clientId())+ "&client_secret="+ encode(settings.clientSecret());
    var reply = transport.post("/admin/oauth/access_token", Map.of("Content-Type", "application/x-www-form-urlencoded"), form);

    if (reply.status() != 200) {
      log.warn("Authentication failed: Shopify HTTP status={}", reply.status());
      throw new ApiException(reply.status() == 429 ? 429 : 502,"Shopify authentication failed. Check the JVM credentials, app installation, and that the app and store belong to the same organization.");
    }

    try {
      JsonNode json = mapper.readTree(reply.body());
      String newToken = json.path("access_token").asText("");
      long ttl = json.path("expires_in").asLong(0);
      if (newToken.isBlank() || ttl < 1) throw new IllegalArgumentException();
      token = newToken;
      refreshAt = requestedAt.plusSeconds(ttl - Math.min(60, Math.max(1, ttl / 10)));
      log.info("Authentication succeeded: token refreshAt={}", refreshAt);
      return token;
    } catch (Exception ex) {
      log.warn("Authentication response is invalid: exceptionType={}", ex.getClass().getSimpleName());
      throw new ApiException(502, "Shopify returned an invalid token response. Check the backend configuration.");
    }
  }

  public synchronized void invalidate(String rejectedToken) {
    if (rejectedToken.equals(token)) {
      log.warn("Authentication: invalidating rejected cached token");
      token = null;
      refreshAt = Instant.EPOCH;
    }
  }

  private static String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }
}
