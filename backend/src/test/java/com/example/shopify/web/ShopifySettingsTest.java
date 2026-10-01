package com.example.shopify.web;

import static org.assertj.core.api.Assertions.*;

import com.example.shopify.config.ShopifySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.StandardEnvironment;

class ShopifySettingsTest {
  @ParameterizedTest
  @ValueSource(strings = {"https://test.myshopify.com", "test.myshopify.com", "../evil", "a/b", ""})
  void preventsArbitraryShopEndpoints(String shop) {
    assertThatThrownBy(() -> new ShopifySettings(shop, "client", "secret", "2026-07"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fixesApiVersionAndDoesNotPrintCredentials() {
    var settings = new ShopifySettings("test-shop", "example-client", "example-secret", "2026-07");
    assertThat(settings.toString()).doesNotContain("example-client", "example-secret");
    assertThatThrownBy(() -> new ShopifySettings("test-shop", "client", "secret", "latest"))
        .hasMessageContaining("2026-07");
  }

  @Test
  void acceptsJvmSystemProperties() {
    String[] names = {
      "SHOPIFY_SHOP", "SHOPIFY_CLIENT_ID", "SHOPIFY_CLIENT_SECRET", "SHOPIFY_API_VERSION"
    };
    var previous = new java.util.HashMap<String, String>();
    for (String name : names) previous.put(name, System.getProperty(name));
    try {
      System.setProperty(names[0], "jvm-store");
      System.setProperty(names[1], "fake-client");
      System.setProperty(names[2], "fake-secret");
      System.setProperty(names[3], "2026-07");
      assertThat(ShopifySettings.from(new StandardEnvironment()).baseUri().getHost())
          .isEqualTo("jvm-store.myshopify.com");
    } finally {
      previous.forEach(
          (name, value) -> {
            if (value == null) System.clearProperty(name);
            else System.setProperty(name, value);
          });
    }
  }
}
