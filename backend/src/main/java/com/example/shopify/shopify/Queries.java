package com.example.shopify.shopify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Queries {
  private static final Logger log = LoggerFactory.getLogger(Queries.class);
  private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

  public String get(String name) {
    log.info("GraphQL query resource={} source={}", name, cache.containsKey(name) ? "cache" : "classpath");
    return cache.computeIfAbsent(name,key -> {
          try {
            return new ClassPathResource("graphql/" + key + ".graphql").getContentAsString(StandardCharsets.UTF_8);
          } catch (IOException ex) {
            log.error("Unable to load GraphQL resource={}", key);
            throw new IllegalStateException("Missing GraphQL resource: " + key, ex);
          }
    });
  }
}
