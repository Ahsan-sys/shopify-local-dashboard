package com.example.shopify.shopify;

import com.example.shopify.config.ShopifySettings;
import com.example.shopify.web.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TokenProvider {
  private final ShopifyTransport transport;
  private final ShopifySettings settings;
  private final JsonMapper mapper;
  private final Clock clock;
  private String token;
  private Instant refreshAt = Instant.EPOCH;

  public TokenProvider(
      ShopifyTransport transport, ShopifySettings settings, JsonMapper mapper, Clock clock) {
    this.transport = transport;
    this.settings = settings;
    this.mapper = mapper;
    this.clock = clock;
  }

  public synchronized String getToken() {
    if (token != null && clock.instant().isBefore(refreshAt)) return token;
    Instant requestedAt = clock.instant();
    var form =
        "grant_type=client_credentials&client_id="
            + encode(settings.clientId())
            + "&client_secret="
            + encode(settings.clientSecret());
    var reply =
        transport.post(
            "/admin/oauth/access_token",
            Map.of("Content-Type", "application/x-www-form-urlencoded"),
            form);
    if (reply.status() != 200) {
      // A token endpoint may echo credentials; never forward its raw body.
      throw new ApiException(
          reply.status() == 429 ? 429 : 502,
          "Shopify authentication failed. Check the JVM credentials, app installation, and that the"
              + " app and store belong to the same organization.");
    }
    try {
      JsonNode json = mapper.readTree(reply.body());
      String newToken = json.path("access_token").asText("");
      long ttl = json.path("expires_in").asLong(0);
      if (newToken.isBlank() || ttl < 1) throw new IllegalArgumentException();
      token = newToken;
      refreshAt = requestedAt.plusSeconds(ttl - Math.min(60, Math.max(1, ttl / 10)));
      return token;
    } catch (Exception ex) {
      throw new ApiException(
          502, "Shopify returned an invalid token response. Check the backend configuration.");
    }
  }

  public synchronized void invalidate(String rejectedToken) {
    // A late 401 must not invalidate a newer token obtained by another request.
    if (rejectedToken.equals(token)) {
      token = null;
      refreshAt = Instant.EPOCH;
    }
  }

  private static String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }
}
