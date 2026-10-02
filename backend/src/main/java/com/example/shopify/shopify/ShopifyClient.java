package com.example.shopify.shopify;

import com.example.shopify.config.ShopifySettings;
import com.example.shopify.web.ApiException;
import java.util.ArrayList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ShopifyClient {
  private static final Logger log = LoggerFactory.getLogger(ShopifyClient.class);
  private final ShopifyTransport transport;
  private final TokenProvider tokens;
  private final ShopifySettings settings;
  private final JsonMapper mapper;

  public ShopifyClient(ShopifyTransport transport,TokenProvider tokens,ShopifySettings settings,JsonMapper mapper) {
    this.transport = transport;
    this.tokens = tokens;
    this.settings = settings;
    this.mapper = mapper;
  }

  public JsonNode execute(String query, Map<String, ?> variables) {
    var operationMatch = java.util.regex.Pattern.compile("\\b(query|mutation)\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(query);
    String operation = operationMatch.find() ? operationMatch.group(2) : "anonymous";
    log.info("GraphQL operation={} started", operation);
    String body = mapper.writeValueAsString(Map.of("query", query, "variables", variables));
    String token = tokens.getToken();
    var reply = send(token, body);
    if (reply.status() == 401) {
      log.warn("GraphQL operation={} received 401; refreshing token and resending once", operation);
      tokens.invalidate(token);
      token = tokens.getToken();
      reply = send(token, body);
    }
    if (reply.status() < 200 || reply.status() >= 300) {
      log.warn("GraphQL operation={} failed HTTP status={}", operation, reply.status());
      String message = switch (reply.status()) {
            case 401 -> "Shopify rejected the access token. Check the app credentials and installation.";
            case 403 -> "Shopify denied access. Check the app scopes and customer data permissions.";
            case 429 -> "Shopify is rate limiting requests. Wait briefly, then refresh before retrying a change.";
            default -> "Shopify returned an HTTP error. If you submitted a change, refresh before retrying because its result may be unknown.";
          };
      throw new ApiException(reply.status() == 429 ? 429 : 502, message);
    }

    JsonNode json;
    try {
      json = mapper.readTree(reply.body());
    } catch (Exception ex) {
      log.warn("GraphQL operation={} response JSON parsing failed exceptionType={}", operation, ex.getClass().getSimpleName());
      throw invalidResponse();
    }

    if (json == null || !json.isObject()) {
      log.warn("GraphQL operation={} expected a JSON object", operation);
      throw invalidResponse();
    }
    if (json.path("errors").isArray() && !json.path("errors").isEmpty()) {
      log.warn("GraphQL operation={} returned {} GraphQL errors", operation, json.path("errors").size());
      var details = new ArrayList<String>();
      boolean throttled = false;

      for (JsonNode error : json.path("errors")) {
        details.add(safeMessage(error.path("message").asText("Shopify could not complete this request."), token));
        throttled |= "THROTTLED".equals(error.path("extensions").path("code").asText());
      }
      throw new ApiException( throttled ? 429 : 502,
          throttled ? "Shopify is rate limiting requests. Wait briefly before trying again." : "Shopify could not complete the request.",details);
    }
    if (!json.path("data").isObject()) {
      log.warn("GraphQL operation={} missing data object", operation);
      throw invalidResponse();
    }
    log.info("GraphQL operation={} response data parsed successfully", operation);
    return json.path("data");
  }

  public JsonNode mutate(String query, Map<String, ?> variables, String mutationName, String resultName) {
    log.info("Mutation={} started", mutationName);
    JsonNode payload = execute(query, variables).path(mutationName);

    if (!payload.isObject() || !payload.path("userErrors").isArray()) {
      log.warn("Mutation={} missing payload object or userErrors array", mutationName);
      throw invalidResponse();
    }
    var errors = new ArrayList<String>();
    for (JsonNode error : payload.path("userErrors")) {
      String field = "";
      for (JsonNode part : error.path("field")) {
        if (!part.asText().matches("\\d+")) field = part.asText();
      }
      String label = field.replaceAll("([a-z])([A-Z])", "$1 $2");
      errors.add((label.isBlank() ? "" : label + ": ")+ safeMessage(error.path("message").asText("Change rejected."), ""));
    }
    if (!errors.isEmpty()) {
      log.warn("Mutation={} rejected userErrorCount={}", mutationName, errors.size());
      throw new ApiException(422, "Shopify rejected the change. Please review these details.", errors);
    }
    if (!payload.path(resultName).isObject()) {
      log.warn("Mutation={} missing result object={}", mutationName, resultName);
      throw invalidResponse();
    }
    log.info("Mutation={} completed successfully", mutationName);
    return payload.path(resultName);
  }

  private ShopifyTransport.Reply send(String token, String body) {
    return transport.post(settings.graphqlPath(),Map.of("Content-Type", "application/json", "X-Shopify-Access-Token", token), body);
  }

  private String safeMessage(String text, String token) {
    String safe = text.replace(settings.clientSecret(), "[redacted]").replace(settings.clientId(), "[redacted]");
    return token.isEmpty() ? safe : safe.replace(token, "[redacted]");
  }

  private ApiException invalidResponse() {
    log.warn("Shopify response failed expected JSON structure validation");
    return new ApiException(502, "Shopify returned an unexpected response. Refresh before retrying a change.");
  }
}
