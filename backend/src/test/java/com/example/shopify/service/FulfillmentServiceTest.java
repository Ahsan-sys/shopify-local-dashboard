package com.example.shopify.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.shopify.shopify.Queries;
import com.example.shopify.shopify.ShopifyClient;
import com.example.shopify.web.ApiException;
import com.example.shopify.web.Requests;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FulfillmentServiceTest {
  final JsonMapper mapper = new JsonMapper();
  ShopifyClient client;
  FulfillmentService service;
  Requests.Fulfill request =
      new Requests.Fulfill("TRACK-1", "Example carrier", "https://example.test/tracking/1", false);

  @BeforeEach
  void setup() {
    client = mock(ShopifyClient.class);
    service = new FulfillmentService(client, new Queries());
  }

  @Test
  void createsOneFulfillmentForAllRemainingOrdersAtOneLocation() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(
            order(
                List.of(
                    fo("1", "OPEN", "10", false, true, 2),
                    fo("2", "IN_PROGRESS", "10", false, true, 3))));
    when(client.mutate(anyString(), anyMap(), eq("fulfillmentCreate"), eq("fulfillment")))
        .thenReturn(mapper.readTree("{\"id\":\"fulfillment-1\",\"status\":\"SUCCESS\"}"));
    assertThat(service.plan("100").remainingQuantity()).isEqualTo(5);
    service.fulfill("100", request);
    @SuppressWarnings("unchecked")
    var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(client)
        .mutate(
            contains("fulfillmentCreate(fulfillment: $fulfillment)"),
            captor.capture(),
            eq("fulfillmentCreate"),
            eq("fulfillment"));
    JsonNode input = mapper.valueToTree(captor.getValue()).path("fulfillment");
    assertThat(input.path("notifyCustomer").asBoolean()).isFalse();
    assertThat(input.path("trackingInfo").path("number").asText()).isEqualTo("TRACK-1");
    assertThat(input.path("trackingInfo").path("company").asText()).isEqualTo("Example carrier");
    assertThat(input.path("trackingInfo").path("url").asText())
        .isEqualTo("https://example.test/tracking/1");
    assertThat(input.path("lineItemsByFulfillmentOrder").size()).isEqualTo(2);
    assertThat(input.path("lineItemsByFulfillmentOrder").get(0).has("fulfillmentOrderLineItems"))
        .isFalse();
    verify(client, times(2)).execute(anyString(), anyMap()); // preview plus fresh submit check
  }

  @Test
  void passesNotifyCustomerTrueWhenRequested() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(order(List.of(fo("1", "OPEN", "10", false, true, 1))));
    service.fulfill("100", new Requests.Fulfill("TRACK", "Carrier", "https://example.test", true));
    verify(client)
        .mutate(
            anyString(),
            argThat(
                v -> mapper.valueToTree(v).path("fulfillment").path("notifyCustomer").asBoolean()),
            anyString(),
            anyString());
  }

  @Test
  void refusesMultipleLocationsWithoutCreatingAnyShipment() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(
            order(
                List.of(
                    fo("1", "OPEN", "10", false, true, 1), fo("2", "OPEN", "11", false, true, 1))));
    assertThat(service.plan("100").reason()).contains("multiple locations");
    assertThatThrownBy(() -> service.fulfill("100", request))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("multiple locations");
    verify(client, never()).mutate(anyString(), anyMap(), anyString(), anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ON_HOLD", "SCHEDULED", "INCOMPLETE"})
  void refusesUnavailableItemsInsteadOfSilentlyDoingPartialFulfillment(String status) {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(
            order(
                List.of(
                    fo("1", "OPEN", "10", false, true, 1),
                    fo("2", status, "10", false, false, 2))));
    assertThat(service.plan("100").eligible()).isFalse();
    assertThat(service.plan("100").reason()).contains("on hold, scheduled");
  }

  @Test
  void respectsSupportedActionsEvenForOpenOrders() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(order(List.of(fo("1", "OPEN", "10", false, false, 1))));
    assertThat(service.plan("100").eligible()).isFalse();
  }

  @Test
  void refusesFulfillmentServices() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(order(List.of(fo("1", "OPEN", "10", true, true, 1))));
    assertThat(service.plan("100").reason()).contains("fulfillment service workflow");
  }

  @Test
  void skipsClosedCancelledAndZeroRemainingOrders() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(
            order(
                List.of(
                    fo("1", "CLOSED", "10", false, true, 2),
                    fo("2", "CANCELLED", "10", false, true, 2),
                    fo("3", "OPEN", "10", false, true, 0))));
    assertThat(service.plan("100").reason()).contains("No remaining");
  }

  @Test
  void rejectsCancelledOrder() {
    var response = order(List.of());
    ((tools.jackson.databind.node.ObjectNode) response.path("order"))
        .put("cancelledAt", "2026-07-01T00:00:00Z");
    when(client.execute(anyString(), anyMap())).thenReturn(response);
    assertThat(service.plan("100").reason()).isEqualTo("This order is cancelled.");
  }

  @Test
  void rejectsAlreadyFulfilledOrder() {
    var response = order(List.of());
    ((tools.jackson.databind.node.ObjectNode) response.path("order"))
        .put("displayFulfillmentStatus", "FULFILLED");
    when(client.execute(anyString(), anyMap())).thenReturn(response);
    assertThat(service.plan("100").reason()).isEqualTo("This order is already fulfilled.");
  }

  @Test
  void missingOrderProduces404() {
    when(client.execute(anyString(), anyMap())).thenReturn(mapper.readTree("{\"order\":null}"));
    ApiException ex = catchThrowableOfType(() -> service.plan("100"), ApiException.class);
    assertThat(ex.status()).isEqualTo(404);
  }

  @Test
  void doesNotTrustStaleEligibilityAtSubmission() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(order(List.of(fo("1", "OPEN", "10", false, true, 1))), order(List.of()));
    assertThat(service.plan("100").eligible()).isTrue();
    assertThatThrownBy(() -> service.fulfill("100", request)).hasMessageContaining("No remaining");
    verify(client, never()).mutate(anyString(), anyMap(), anyString(), anyString());
  }

  @Test
  void paginatesFulfillmentOrdersAndLineItems() {
    var firstFo = fo("1", "OPEN", "10", false, true, 0);
    firstFo.put(
        "lineItems",
        Map.of(
            "nodes",
            List.of(Map.of("remainingQuantity", 0)),
            "pageInfo",
            Map.of("hasNextPage", true, "endCursor", "line-cursor")));
    var page1 = order(List.of(firstFo));
    ((tools.jackson.databind.node.ObjectNode)
            page1.path("order").path("fulfillmentOrders").path("pageInfo"))
        .put("hasNextPage", true)
        .put("endCursor", "fo-cursor");
    when(client.execute(contains("query FulfillmentOrders"), anyMap()))
        .thenReturn(page1, order(List.of(fo("2", "OPEN", "10", false, true, 1))));
    when(client.execute(contains("query FulfillmentLines"), anyMap()))
        .thenReturn(
            mapper.valueToTree(
                Map.of(
                    "fulfillmentOrder",
                    Map.of(
                        "lineItems",
                        Map.of(
                            "nodes",
                            List.of(Map.of("remainingQuantity", 5)),
                            "pageInfo",
                            Map.of("hasNextPage", false))))));
    assertThat(service.plan("100").remainingQuantity()).isEqualTo(6);
    verify(client)
        .execute(
            contains("query FulfillmentLines"),
            eq(Map.of("id", "gid://shopify/FulfillmentOrder/1", "after", "line-cursor")));
    verify(client)
        .execute(
            contains("query FulfillmentOrders"), argThat(v -> "fo-cursor".equals(v.get("after"))));
  }

  @Test
  void rejectsBrokenPaginationInsteadOfLoopingOrTruncating() {
    var response = order(List.of());
    ((tools.jackson.databind.node.ObjectNode)
            response.path("order").path("fulfillmentOrders").path("pageInfo"))
        .put("hasNextPage", true);
    when(client.execute(anyString(), anyMap())).thenReturn(response);
    assertThatThrownBy(() -> service.plan("100"))
        .hasMessageContaining("invalid fulfillment cursor");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "javascript:alert(1)",
        "file:///etc/passwd",
        "relative/path",
        "https://user:password@example.test"
      })
  void rejectsUnsafeTrackingUrlsBeforeShopifyCalls(String url) {
    assertThatThrownBy(() -> service.fulfill("100", new Requests.Fulfill("X", "Y", url, false)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Tracking URL");
    verifyNoInteractions(client);
  }

  @Test
  void serializesConcurrentSubmissionsForTheSameOrder() throws Exception {
    var writes = new AtomicInteger();
    when(client.execute(anyString(), anyMap()))
        .thenAnswer(
            inv ->
                order(
                    writes.get() == 0
                        ? List.of(fo("1", "OPEN", "10", false, true, 1))
                        : List.of()));
    when(client.mutate(anyString(), anyMap(), anyString(), anyString()))
        .thenAnswer(
            inv -> {
              writes.incrementAndGet();
              return mapper.readTree("{\"id\":\"done\"}");
            });
    var executor = Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<Boolean> submit =
          () -> {
            try {
              service.fulfill("100", request);
              return true;
            } catch (ApiException ex) {
              return false;
            }
          };
      var results = executor.invokeAll(List.of(submit, submit));
      assertThat(
              results.stream()
                  .map(
                      f -> {
                        try {
                          return f.get();
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                      })
                  .filter(Boolean::booleanValue)
                  .count())
          .isEqualTo(1);
      assertThat(writes.get()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  JsonNode order(List<Map<String, Object>> nodes) {
    return mapper.readTree(
        mapper.writeValueAsString(
            Map.of(
                "order",
                Map.of(
                    "id",
                    "gid://shopify/Order/100",
                    "displayFulfillmentStatus",
                    "UNFULFILLED",
                    "fulfillmentOrders",
                    Map.of("nodes", nodes, "pageInfo", Map.of("hasNextPage", false))))));
  }

  Map<String, Object> fo(
      String id,
      String status,
      String location,
      boolean serviceLocation,
      boolean supported,
      int quantity) {
    var fo = new java.util.LinkedHashMap<String, Object>();
    fo.put("id", "gid://shopify/FulfillmentOrder/" + id);
    fo.put("status", status);
    fo.put(
        "supportedActions",
        supported ? List.of(Map.of("action", "CREATE_FULFILLMENT")) : List.of());
    fo.put(
        "assignedLocation",
        Map.of(
            "location",
            Map.of("id", location, "name", "Warehouse", "isFulfillmentService", serviceLocation)));
    fo.put(
        "lineItems",
        Map.of(
            "nodes",
            List.of(Map.of("id", "line-" + id, "remainingQuantity", quantity)),
            "pageInfo",
            Map.of("hasNextPage", false)));
    return fo;
  }
}
