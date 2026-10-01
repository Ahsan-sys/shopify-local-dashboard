package com.example.shopify.config;

import com.example.shopify.shopify.JdkShopifyTransport;
import com.example.shopify.shopify.ShopifyTransport;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class BackendConfig {
  @Bean
  ShopifySettings shopifySettings(Environment environment) {
    return ShopifySettings.from(environment);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ShopifyTransport shopifyTransport(ShopifySettings settings) {
    var client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    return new JdkShopifyTransport(client, settings.baseUri());
  }
}
