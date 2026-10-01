package com.example.shopify.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shopify.shopify.ShopifyTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
      "SHOPIFY_SHOP=test-shop",
      "SHOPIFY_CLIENT_ID=test-client",
      "SHOPIFY_CLIENT_SECRET=test-secret",
      "SHOPIFY_API_VERSION=2026-07"
    })
class DashboardIntegrationTest {
  @Autowired WebApplicationContext context;
  @Autowired LocalRequestFilter filter;
  @MockitoBean ShopifyTransport transport;
  MockMvc mvc;
  final JsonMapper mapper = new JsonMapper();

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
    when(transport.post(eq("/admin/oauth/access_token"), anyMap(), anyString()))
        .thenReturn(
            new ShopifyTransport.Reply(
                200, "{\"access_token\":\"backend-only-token\",\"expires_in\":3600}"));
  }

  @Test
  void storeEndpointUsesRealServiceAndClientAndReturnsNoSecrets() throws Exception {
    graphql(
        "{\"data\":{\"shop\":{\"name\":\"Demo"
            + " Store\",\"email\":\"store@example.test\",\"myshopifyDomain\":\"demo.myshopify.com\",\"currencyCode\":\"USD\",\"primaryDomain\":{\"host\":\"example.test\",\"url\":\"https://example.test\"}}}}");
    String result =
        mvc.perform(get("/api/shop"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.name").value("Demo Store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(result).doesNotContain("test-secret", "test-client", "backend-only-token");
  }

  @Test
  void productUpdateFlowsThroughValidationToGraphql() throws Exception {
    graphql(
        "{\"data\":{\"productUpdate\":{\"product\":{\"id\":\"gid://shopify/Product/123\",\"title\":\"Edited\"},\"userErrors\":[]}}}");
    mvc.perform(
            put("/api/products/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Edited\",\"descriptionHtml\":\"<p>Text</p>\",\"tags\":[\"new\"],\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Edited"));
    verify(transport)
        .post(
            eq("/admin/api/2026-07/graphql.json"),
            anyMap(),
            argThat(
                body -> {
                  var json = mapper.readTree(body);
                  return "gid://shopify/Product/123"
                      .equals(json.path("variables").path("product").path("id").asText());
                }));
  }

  @Test
  void emptyAndInvalidFieldsNeverReachShopify() throws Exception {
    mvc.perform(
            put("/api/products/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\" \",\"descriptionHtml\":\"\",\"tags\":[],\"status\":\"ACTIVE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0]").exists());
    verifyNoInteractions(transport);
  }

  @Test
  void rejectsUnknownStatusAndMalformedJson() throws Exception {
    mvc.perform(
            put("/api/products/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"T\",\"descriptionHtml\":\"\",\"tags\":[],\"status\":\"INVALID\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(put("/api/orders/123").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(transport);
  }

  @Test
  void rejectsUnexpectedInputProperties() throws Exception {
    mvc.perform(
            put("/api/orders/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"note\":\"\",\"tags\":[],\"email\":\"should-not-be-written@example.test\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(transport);
  }

  @Test
  void invalidPageSizeAndIdNeverReachShopify() throws Exception {
    mvc.perform(get("/api/products?first=1000")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/orders?first=NaN")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/products/invalid/variants")).andExpect(status().isBadRequest());
    verifyNoInteractions(transport);
  }

  @Test
  void shopifyUserErrorsBecome422WithReadableDetails() throws Exception {
    graphql(
        "{\"data\":{\"orderUpdate\":{\"order\":null,\"userErrors\":[{\"field\":[\"input\",\"tags\"],\"message\":\"Tag"
            + " is too long\"}]}}}");
    mvc.perform(
            put("/api/orders/123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"\",\"tags\":[\"X\"]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.details[0]").value("tags: Tag is too long"));
  }

  @Test
  void fulfillmentRequiresTrackingAndExplicitNotifyChoice() throws Exception {
    mvc.perform(
            post("/api/orders/123/fulfill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"trackingNumber\":\"X\",\"carrier\":\"Y\",\"trackingUrl\":\"https://example.test\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(transport);
  }

  @Test
  void deniesRemoteBrowserOriginAndDnsRebindingHost() throws Exception {
    mvc.perform(get("/api/shop").header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden());
    mvc.perform(get("http://evil.example/api/shop")).andExpect(status().isForbidden());
    mvc.perform(get("/api/shop").header("Sec-Fetch-Site", "cross-site"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(transport);
  }

  @Test
  void allowsLocalDevOrigin() throws Exception {
    graphql("{\"data\":{\"shop\":{\"name\":\"Demo\"}}}");
    mvc.perform(get("/api/shop").header("Origin", "http://127.0.0.1:5173"))
        .andExpect(status().isOk());
  }

  @Test
  void mapsReactRoutesToTheBundledEntryPage() throws Exception {
    mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    mvc.perform(get("/orders")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
  }

  void graphql(String body) {
    when(transport.post(eq("/admin/api/2026-07/graphql.json"), anyMap(), anyString()))
        .thenReturn(new ShopifyTransport.Reply(200, body));
  }
}
