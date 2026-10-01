package com.example.shopify.web;

import java.util.List;

public class ApiException extends RuntimeException {
  private final int status;
  private final List<String> details;

  public ApiException(int status, String message) {
    this(status, message, List.of());
  }

  public ApiException(int status, String message, List<String> details) {
    super(message);
    this.status = status;
    this.details = List.copyOf(details);
  }

  public int status() {
    return status;
  }

  public List<String> details() {
    return details;
  }
}
