package com.example.shopify.web;

import com.example.shopify.service.CatalogService;
import com.example.shopify.service.FulfillmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api")
public class DashboardController {
  private final CatalogService catalog;
  private final FulfillmentService fulfillment;

  public DashboardController(CatalogService catalog, FulfillmentService fulfillment) {
    this.catalog = catalog;
    this.fulfillment = fulfillment;
  }

  @GetMapping("/shop")
  public JsonNode shop() {
    return catalog.shop();
  }

  @GetMapping("/products")
  public JsonNode products(
      @RequestParam(defaultValue = "10") int first, @RequestParam(required = false) String after) {
    return catalog.products(first, after);
  }

  @GetMapping("/products/{id}/variants")
  public JsonNode variants(
      @PathVariable String id,
      @RequestParam(defaultValue = "10") int first,
      @RequestParam(required = false) String after) {
    return catalog.variants(id, first, after);
  }

  @GetMapping("/inventory/{id}/levels")
  public JsonNode inventory(
      @PathVariable String id,
      @RequestParam(defaultValue = "10") int first,
      @RequestParam(required = false) String after) {
    return catalog.inventory(id, first, after);
  }

  @PutMapping("/products/{id}")
  public JsonNode productUpdate(
      @PathVariable String id, @Valid @RequestBody Requests.ProductUpdate input) {
    return catalog.updateProduct(id, input);
  }

  @GetMapping("/orders")
  public JsonNode orders(
      @RequestParam(defaultValue = "10") int first, @RequestParam(required = false) String after) {
    return catalog.orders(first, after);
  }

  @PutMapping("/orders/{id}")
  public JsonNode orderUpdate(
      @PathVariable String id, @Valid @RequestBody Requests.OrderUpdate input) {
    return catalog.updateOrder(id, input);
  }

  @GetMapping("/orders/{id}/fulfillment-plan")
  public FulfillmentService.Plan plan(@PathVariable String id) {
    return fulfillment.plan(id);
  }

  @PostMapping("/orders/{id}/fulfill")
  public JsonNode fulfill(@PathVariable String id, @Valid @RequestBody Requests.Fulfill input) {
    return fulfillment.fulfill(id, input);
  }
}
