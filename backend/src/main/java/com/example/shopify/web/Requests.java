package com.example.shopify.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class Requests {
  private Requests() {}

  public enum ProductStatus {
    ACTIVE,
    DRAFT,
    ARCHIVED
  }

  public record ProductUpdate(
      @NotBlank @Size(max = 255) String title,
      @NotNull @Size(max = 100000) String descriptionHtml,
      @NotNull @Size(max = 250) List<@NotBlank @Size(max = 255) String> tags,
      @NotNull ProductStatus status) {}

  public record OrderUpdate(
      @NotNull @Size(max = 5000) String note,
      @NotNull @Size(max = 250) List<@NotBlank @Size(max = 255) String> tags) {}

  public record Fulfill(
      @NotBlank @Size(max = 255) String trackingNumber,
      @NotBlank @Size(max = 255) String carrier,
      @NotBlank @Size(max = 2048) String trackingUrl,
      @NotNull Boolean notifyCustomer) {}
}
