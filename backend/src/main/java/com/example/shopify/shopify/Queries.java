package com.example.shopify.shopify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class Queries {
  private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

  public String get(String name) {
    return cache.computeIfAbsent(
        name,
        key -> {
          try {
            return new ClassPathResource("graphql/" + key + ".graphql")
                .getContentAsString(StandardCharsets.UTF_8);
          } catch (IOException ex) {
            throw new IllegalStateException("Missing GraphQL resource: " + key, ex);
          }
        });
  }
}
