package com.example.shopify.shopify;

import com.example.shopify.web.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class JdkShopifyTransport implements ShopifyTransport {
  private final HttpClient client;
  private final URI baseUri;

  public JdkShopifyTransport(HttpClient client, URI baseUri) {
    this.client = client;
    this.baseUri = baseUri;
  }

  @Override
  public Reply post(String path, Map<String, String> headers, String body) {
    var request =
        HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(request::header);
    try {
      var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      return new Reply(response.statusCode(), response.body());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (IOException ex) {
      throw unavailable();
    }
  }

  private ApiException unavailable() {
    return new ApiException(
        502,
        "Shopify could not be reached. If you were saving or fulfilling, the result is unknown."
            + " Refresh the order or product before trying again.");
  }
}
