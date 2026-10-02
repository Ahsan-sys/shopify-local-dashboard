package com.example.shopify.shopify;

import java.util.Map;


public interface ShopifyTransport {
  record Reply(int status, String body) {}

  Reply post(String path, Map<String, String> headers, String body);
}
