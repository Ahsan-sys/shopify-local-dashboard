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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class CatalogServiceTest {
  ShopifyClient client;
  CatalogService service;
  JsonMapper mapper = new JsonMapper();

  @BeforeEach
  void setup() {
    client = mock(ShopifyClient.class);
    service = new CatalogService(client, new Queries());
  }

  @Test
  void forwardsOpaqueCursorWithoutDecodingOrUsingOffset() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(
            mapper.readTree("{\"products\":{\"nodes\":[],\"pageInfo\":{\"hasNextPage\":false}}}"));
    service.products(5, "opaque+/==");
    verify(client)
        .execute(contains("after: $after"), eq(Map.of("first", 5, "after", "opaque+/==")));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 21, 250})
  void rejectsUnboundedProductQueries(int size) {
    assertThatThrownBy(() -> service.products(size, null)).isInstanceOf(ApiException.class);
    verifyNoInteractions(client);
  }

  @Test
  void mutationUsesVariablesSoQuotesAndHtmlRemainData() {
    service.updateProduct(
        "123",
        new Requests.ProductUpdate(
            "A \"quoted\" title",
            "<p>It's fine</p>",
            List.of("tag"),
            Requests.ProductStatus.DRAFT));
    verify(client)
        .mutate(
            contains("$product: ProductUpdateInput!"),
            eq(
                Map.of(
                    "product",
                    Map.of(
                        "id",
                        "gid://shopify/Product/123",
                        "title",
                        "A \"quoted\" title",
                        "descriptionHtml",
                        "<p>It's fine</p>",
                        "tags",
                        List.of("tag"),
                        "status",
                        "DRAFT"))),
            eq("productUpdate"),
            eq("product"));
  }

  @Test
  void emptyNoteAndTagsAreSentToClearExistingValues() {
    service.updateOrder("7", new Requests.OrderUpdate("", List.of()));
    verify(client)
        .mutate(
            anyString(),
            eq(
                Map.of(
                    "input", Map.of("id", "gid://shopify/Order/7", "note", "", "tags", List.of()))),
            eq("orderUpdate"),
            eq("order"));
  }

  @Test
  void customerNamesUseOrderDataWithoutRequiringReadCustomers() {
    when(client.execute(anyString(), anyMap()))
        .thenReturn(
            mapper.readTree(
                """
{"orders":{"nodes":[
  {"billingAddress":{"name":"Billing Customer"},"shippingAddress":{"name":"Recipient"}},
  {"billingAddress":null,"shippingAddress":{"name":"Shipping Customer"}},
  {"billingAddress":null,"shippingAddress":null}
],"pageInfo":{"hasNextPage":false}}}
"""));
    var nodes = service.orders(10, null).path("nodes");
    assertThat(nodes.get(0).path("customer").path("displayName").asText())
        .isEqualTo("Billing Customer");
    assertThat(nodes.get(1).path("customer").path("displayName").asText())
        .isEqualTo("Shipping Customer");
    assertThat(nodes.get(2).path("customer").isNull()).isTrue();
    assertThat(nodes.get(0).has("billingAddress")).isFalse();
    verify(client).execute(argThat(q -> !q.contains("customer {")), anyMap());
  }

  @Test
  void variantsAndInventoryHaveIndependentPagination() {
    when(client.execute(contains("query Variants"), anyMap()))
        .thenReturn(mapper.readTree("{\"product\":{\"variants\":{\"nodes\":[]}}}"));
    when(client.execute(contains("query Inventory"), anyMap()))
        .thenReturn(mapper.readTree("{\"inventoryItem\":{\"inventoryLevels\":{\"nodes\":[]}}}"));
    service.variants("123", 10, "v-next");
    service.inventory("456", 5, "l-next");
    verify(client)
        .execute(
            contains("query Variants"),
            eq(Map.of("id", "gid://shopify/Product/123", "first", 10, "after", "v-next")));
    verify(client)
        .execute(
            contains("query Inventory"),
            eq(Map.of("id", "gid://shopify/InventoryItem/456", "first", 5, "after", "l-next")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "foo", "gid://shopify/Order/1", "1/../../foo"})
  void rejectsMalformedIds(String id) {
    assertThatThrownBy(() -> CatalogService.gid("Product", id)).isInstanceOf(ApiException.class);
  }

  @Test
  void missingProductProduces404() {
    when(client.execute(anyString(), anyMap())).thenReturn(mapper.readTree("{\"product\":null}"));
    ApiException ex =
        catchThrowableOfType(() -> service.variants("123", 10, null), ApiException.class);
    assertThat(ex.status()).isEqualTo(404);
  }
}
