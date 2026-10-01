package com.example.shopify.shopify;

import com.example.shopify.config.ShopifySettings;
import com.example.shopify.web.ApiException;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ShopifyClient {
  private final ShopifyTransport transport;
  private final TokenProvider tokens;
  private final ShopifySettings settings;
  private final JsonMapper mapper;

  public ShopifyClient(
      ShopifyTransport transport,
      TokenProvider tokens,
      ShopifySettings settings,
      JsonMapper mapper) {
    this.transport = transport;
    this.tokens = tokens;
    this.settings = settings;
    this.mapper = mapper;
  }

  public JsonNode execute(String query, Map<String, ?> variables) {
    String body = mapper.writeValueAsString(Map.of("query", query, "variables", variables));
    String token = tokens.getToken();
    var reply = send(token, body);
    if (reply.status() == 401) {
      // Only an explicit authentication rejection is retried. Never replay a timed-out mutation.
      tokens.invalidate(token);
      token = tokens.getToken();
      reply = send(token, body);
    }
    if (reply.status() < 200 || reply.status() >= 300) {
      String message =
          switch (reply.status()) {
            case 401 ->
                "Shopify rejected the access token. Check the app credentials and installation.";
            case 403 ->
                "Shopify denied access. Check the app scopes and customer data permissions.";
            case 429 ->
                "Shopify is rate limiting requests. Wait briefly, then refresh before retrying a"
                    + " change.";
            default ->
                "Shopify returned an HTTP error. If you submitted a change, refresh before retrying"
                    + " because its result may be unknown.";
          };
      throw new ApiException(reply.status() == 429 ? 429 : 502, message);
    }
    JsonNode json;
    try {
      json = mapper.readTree(reply.body());
    } catch (Exception ex) {
      throw invalidResponse();
    }
    if (json == null || !json.isObject()) throw invalidResponse();
    if (json.path("errors").isArray() && !json.path("errors").isEmpty()) {
      var details = new ArrayList<String>();
      boolean throttled = false;
      for (JsonNode error : json.path("errors")) {
        details.add(
            safeMessage(
                error.path("message").asText("Shopify could not complete this request."), token));
        throttled |= "THROTTLED".equals(error.path("extensions").path("code").asText());
      }
      throw new ApiException(
          throttled ? 429 : 502,
          throttled
              ? "Shopify is rate limiting requests. Wait briefly before trying again."
              : "Shopify could not complete the request.",
          details);
    }
    if (!json.path("data").isObject()) throw invalidResponse();
    return json.path("data");
  }

  public JsonNode mutate(
      String query, Map<String, ?> variables, String mutationName, String resultName) {
    JsonNode payload = execute(query, variables).path(mutationName);
    if (!payload.isObject() || !payload.path("userErrors").isArray()) throw invalidResponse();
    var errors = new ArrayList<String>();
    for (JsonNode error : payload.path("userErrors")) {
      String field = "";
      for (JsonNode part : error.path("field")) {
        if (!part.asText().matches("\\d+")) field = part.asText();
      }
      String label = field.replaceAll("([a-z])([A-Z])", "$1 $2");
      errors.add(
          (label.isBlank() ? "" : label + ": ")
              + safeMessage(error.path("message").asText("Change rejected."), ""));
    }
    if (!errors.isEmpty())
      throw new ApiException(
          422, "Shopify rejected the change. Please review these details.", errors);
    if (!payload.path(resultName).isObject()) throw invalidResponse();
    return payload.path(resultName);
  }

  private ShopifyTransport.Reply send(String token, String body) {
    return transport.post(
        settings.graphqlPath(),
        Map.of("Content-Type", "application/json", "X-Shopify-Access-Token", token),
        body);
  }

  private String safeMessage(String text, String token) {
    String safe =
        text.replace(settings.clientSecret(), "[redacted]")
            .replace(settings.clientId(), "[redacted]");
    return token.isEmpty() ? safe : safe.replace(token, "[redacted]");
  }

  private ApiException invalidResponse() {
    return new ApiException(
        502, "Shopify returned an unexpected response. Refresh before retrying a change.");
  }
}
