package com.example.shopify.service;

import com.example.shopify.shopify.Queries;
import com.example.shopify.shopify.ShopifyClient;
import com.example.shopify.web.ApiException;
import com.example.shopify.web.Requests;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class CatalogService {
  private final ShopifyClient client;
  private final Queries queries;

  public CatalogService(ShopifyClient client, Queries queries) {
    this.client = client;
    this.queries = queries;
  }

  public JsonNode shop() {
    return required(client.execute(queries.get("shop"), Map.of()).path("shop"), "Store");
  }

  public JsonNode products(int first, String after) {
    return required(client.execute(queries.get("products"), page(first, after)).path("products"), "Products");
  }

  public JsonNode variants(String id, int first, String after) {
    var variables = page(first, after);
    variables.put("id", gid("Product", id));
    return required(client.execute(queries.get("variants"), variables).path("product"), "Product").path("variants");
  }

  public JsonNode inventory(String id, int first, String after) {
    var variables = page(first, after);
    variables.put("id", gid("InventoryItem", id));
    return required(client.execute(queries.get("inventory"), variables).path("inventoryItem"),"Inventory item").path("inventoryLevels");
  }

  public JsonNode updateProduct(String id, Requests.ProductUpdate input) {
    return client.mutate(queries.get("product-update"),Map.of("product",Map.of(
            "id",gid("Product", id),"title",input.title(),"descriptionHtml",input.descriptionHtml(),"tags",input.tags(),"status",input.status().name())),
        "productUpdate","product");
  }

  public JsonNode orders(int first, String after) {
    JsonNode connection = required(client.execute(queries.get("orders"), page(first, after)).path("orders"), "Orders");
    for (JsonNode node : connection.path("nodes")) {
      ObjectNode order = (ObjectNode) node;
      String billing = node.path("billingAddress").path("name").asText("");
      String shipping = node.path("shippingAddress").path("name").asText("");
      String name = billing.isBlank() ? shipping : billing;
      if (name.isBlank()) order.putNull("customer");
      else
        order.putObject("customer").put("displayName", name).put("source", billing.isBlank() ? "Shipping name" : "Billing name");

      order.remove("billingAddress");
      order.remove("shippingAddress");
    }
    return connection;
  }

  public JsonNode updateOrder(String id, Requests.OrderUpdate input) {
    return client.mutate(queries.get("order-update"),
        Map.of("input", Map.of("id", gid("Order", id), "note", input.note(), "tags", input.tags())),"orderUpdate","order");
  }

  public static String gid(String type, String id) {
    if (id == null || !id.matches("[1-9][0-9]{0,29}")) throw new ApiException(400, "The item ID must be a positive numeric Shopify ID.");
    return "gid://shopify/" + type + "/" + id;
  }

  public static Map<String, Object> page(int first, String after) {
    if (first < 1 || first > 20) throw new ApiException(400, "Page size must be between 1 and 20.");
    if (after != null && after.length() > 4096) throw new ApiException(400, "The page cursor is too long.");
    var variables = new LinkedHashMap<String, Object>();
    variables.put("first", first);
    variables.put("after", after == null || after.isBlank() ? null : after);
    return variables;
  }

  public static JsonNode required(JsonNode value, String name) {
    if (value.isMissingNode() || value.isNull()) throw new ApiException(404, name + " was not found or is not accessible with this app's scopes.");
    return value;
  }
}
