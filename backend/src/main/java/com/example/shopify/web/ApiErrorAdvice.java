package com.example.shopify.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorAdvice {
  private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvice.class);
  private static String failureLocation(Throwable error) {
    for (StackTraceElement frame : error.getStackTrace()) {
      if (frame.getClassName().startsWith("com.example.shopify.")) return frame.toString();
    }
    return "outside application code";
  }
  public record ErrorBody(String message, List<String> details) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorBody> api(ApiException ex) {
    log.warn("API failure status={} exceptionType={} location={}", ex.status(), ex.getClass().getSimpleName(), failureLocation(ex));
    return ResponseEntity.status(ex.status()).body(new ErrorBody(ex.getMessage(), ex.details()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
    log.warn("API request validation failed");
    var details = ex.getBindingResult().getFieldErrors().stream().map(error -> error.getField() + ": " + error.getDefaultMessage()).distinct().toList();
    return ResponseEntity.badRequest().body(new ErrorBody("Please correct the highlighted request fields.", details));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class,HandlerMethodValidationException.class,MethodArgumentTypeMismatchException.class})
  ResponseEntity<ErrorBody> malformed(Exception ignored) {
    log.warn("API malformed request exceptionType={}", ignored.getClass().getSimpleName());
    return ResponseEntity.badRequest().body(new ErrorBody("The request contains an invalid value or JSON body.", List.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception ignored) {
    log.error("Unexpected API failure exceptionType={} location={}", ignored.getClass().getSimpleName(), failureLocation(ignored));
    return ResponseEntity.internalServerError().body(new ErrorBody("An unexpected error occurred. Refresh and try again.", List.of()));
  }
}
