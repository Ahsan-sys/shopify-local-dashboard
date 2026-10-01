package com.example.shopify.shopify;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JdkShopifyTransportTest {
  @Test
  void postsExactBodyAndHeadersOverHttpWithoutFollowingRedirects() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var body = new AtomicReference<String>();
    var header = new AtomicReference<String>();
    server.createContext(
        "/graphql",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          header.set(exchange.getRequestHeaders().getFirst("X-Shopify-Access-Token"));
          byte[] response = "{\"data\":{}}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.createContext(
        "/redirect",
        exchange -> {
          exchange.getResponseHeaders().set("Location", "/graphql");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.start();
    try {
      var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
      var transport =
          new JdkShopifyTransport(
              client, URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
      assertThat(
              transport
                  .post(
                      "/graphql",
                      Map.of("X-Shopify-Access-Token", "test-token"),
                      "{\"query\":\"test\"}")
                  .status())
          .isEqualTo(200);
      assertThat(body.get()).isEqualTo("{\"query\":\"test\"}");
      assertThat(header.get()).isEqualTo("test-token");
      assertThat(transport.post("/redirect", Map.of(), "{}").status()).isEqualTo(302);
    } finally {
      server.stop(0);
    }
  }
}
