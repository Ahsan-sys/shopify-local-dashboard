package com.example.shopify.web;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorAdvice {
  public record ErrorBody(String message, List<String> details) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorBody> api(ApiException ex) {
    return ResponseEntity.status(ex.status()).body(new ErrorBody(ex.getMessage(), ex.details()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
    var details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .distinct()
            .toList();
    return ResponseEntity.badRequest()
        .body(new ErrorBody("Please correct the highlighted request fields.", details));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorBody> malformed(Exception ignored) {
    return ResponseEntity.badRequest()
        .body(new ErrorBody("The request contains an invalid value or JSON body.", List.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception ignored) {
    // Do not return exception messages, request bodies, credentials, or stack traces.
    return ResponseEntity.internalServerError()
        .body(new ErrorBody("An unexpected error occurred. Refresh and try again.", List.of()));
  }
}
