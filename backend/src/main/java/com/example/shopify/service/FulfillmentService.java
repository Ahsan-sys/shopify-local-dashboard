package com.example.shopify.service;

import com.example.shopify.shopify.Queries;
import com.example.shopify.shopify.ShopifyClient;
import com.example.shopify.web.ApiException;
import com.example.shopify.web.Requests;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class FulfillmentService {
  private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);
  public record Plan(boolean eligible,String reason,String locationName,int remainingQuantity,List<String> fulfillmentOrderIds) {
    static Plan blocked(String reason) {
      log.info("Fulfillment eligibility blocked: {}", reason);
      return new Plan(false, reason, "", 0, List.of());
    }
  }

  private final ShopifyClient client;
  private final Queries queries;
  private final Object[] locks = new Object[64];

  public FulfillmentService(ShopifyClient client, Queries queries) {
    this.client = client;
    this.queries = queries;
    for (int i = 0; i < locks.length; i++) locks[i] = new Object();
  }

  public Plan plan(String id) {
    log.info("Fulfillment eligibility check started");
    String orderId = CatalogService.gid("Order", id);
    var candidates = new ArrayList<String>();
    var locations = new HashSet<String>();
    String locationName = "";
    int total = 0;
    String after = null;
    var seen = new HashSet<String>();
    do {
      var variables = new LinkedHashMap<String, Object>();
      variables.put("id", orderId);
      variables.put("after", after);
      JsonNode order = CatalogService.required(client.execute(queries.get("fulfillment-orders"), variables).path("order"), "Order");

      if (!order.path("cancelledAt").isNull() && !order.path("cancelledAt").isMissingNode()) return Plan.blocked("This order is cancelled.");
      if ("FULFILLED".equals(order.path("displayFulfillmentStatus").asText())) return Plan.blocked("This order is already fulfilled.");

      JsonNode connection = order.path("fulfillmentOrders");
      for (JsonNode fo : connection.path("nodes")) {
        String status = fo.path("status").asText();
        if (Set.of("CLOSED", "CANCELLED").contains(status)) continue;

        int remaining = remaining(fo);
        if (remaining == 0) continue;

        JsonNode location = fo.path("assignedLocation").path("location");
        if (!location.isObject() || location.path("id").asText().isBlank()) {
          return Plan.blocked("A remaining fulfillment order has no accessible assigned location.");
        }

        if (!location.has("isFulfillmentService")
            || location.path("isFulfillmentService").asBoolean()) {
          return Plan.blocked("This order requires a fulfillment service workflow, which this dashboard does not support.");
        }

        boolean canCreate = false;
        for (JsonNode action : fo.path("supportedActions"))
          canCreate |= "CREATE_FULFILLMENT".equals(action.path("action").asText());

        if (!Set.of("OPEN", "IN_PROGRESS").contains(status) || !canCreate) {
          return Plan.blocked("Some remaining items are on hold, scheduled, or otherwise unavailable for fulfillment. Resolve them in Shopify, then refresh.");
        }
        candidates.add(fo.path("id").asText());
        locations.add(location.path("id").asText());
        locationName = location.path("name").asText();
        total += remaining;
      }
      after = nextCursor(connection, seen);
    } while (after != null);

    if (candidates.isEmpty())
      return Plan.blocked("No remaining merchant-managed items can be fulfilled. Shopify may still be routing the order, or the app may not have access to its fulfillment orders.");
    if (locations.size() != 1)
      return Plan.blocked("Remaining items are assigned to multiple locations. Multiple shipments are outside this dashboard's scope; fulfill them in Shopify.");
    if (candidates.size() > 250)
      return Plan.blocked("This order exceeds Shopify's single-request input limit. Fulfill it in Shopify.");

    log.info("Fulfillment eligibility succeeded fulfillmentOrderCount={} remainingQuantity={}", candidates.size(), total);
    return new Plan(true,"All remaining accessible merchant-managed items are ready for one shipment.",locationName,total,List.copyOf(candidates));
  }

  public JsonNode fulfill(String id, Requests.Fulfill request) {
    log.info("Fulfillment update started; validating tracking URL");
    validateTrackingUrl(request.trackingUrl());
    CatalogService.gid("Order", id);
    synchronized (locks[Math.floorMod(id.hashCode(), locks.length)]) {
      log.info("Fulfillment order lock acquired; rechecking eligibility before mutation");
      Plan plan = plan(id);
      if (!plan.eligible()) throw new ApiException(409, plan.reason());
      var groups = plan.fulfillmentOrderIds().stream().map(foId -> Map.of("fulfillmentOrderId", foId)).toList();

      var input =
          Map.of(
              "lineItemsByFulfillmentOrder",
              groups,
              "notifyCustomer",
              request.notifyCustomer(),
              "trackingInfo",
              Map.of(
                  "number",
                  request.trackingNumber().trim(),
                  "company",
                  request.carrier().trim(),
                  "url",
                  request.trackingUrl().trim()));
      return client.mutate(queries.get("fulfillment-create"),Map.of("fulfillment", input),"fulfillmentCreate","fulfillment");
    }
  }

  private int remaining(JsonNode fo) {
    int total = 0;
    JsonNode lines = fo.path("lineItems");
    var seen = new HashSet<String>();
    while (true) {
      for (JsonNode line : lines.path("nodes"))
        total += Math.max(0, line.path("remainingQuantity").asInt());
      String after = nextCursor(lines, seen);
      if (after == null) return total;
      log.info("Fulfillment eligibility loading next line-item page");
      lines =
          CatalogService.required(client.execute(queries.get("fulfillment-lines"),Map.of("id", fo.path("id").asText(), "after", after))
                          .path("fulfillmentOrder"),"Fulfillment order").path("lineItems");
    }
  }

  private static String nextCursor(JsonNode connection, Set<String> seen) {
    if (!connection.path("nodes").isArray() || !connection.path("pageInfo").has("hasNextPage")) {
      throw new ApiException(502, "Shopify returned incomplete fulfillment data. Refresh before trying again.");
    }
    if (!connection.path("pageInfo").path("hasNextPage").asBoolean()) return null;

    String cursor = connection.path("pageInfo").path("endCursor").asText("");
    if (cursor.isBlank() || !seen.add(cursor)) throw new ApiException(502, "Shopify returned an invalid fulfillment cursor.");
    return cursor;
  }

  private static void validateTrackingUrl(String value) {
    try {
      URI uri = URI.create(value.trim());
      if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
    } catch (RuntimeException ex) {
      throw new ApiException(400, "Tracking URL must be an absolute HTTP or HTTPS URL without embedded credentials.");
    }
  }
}
