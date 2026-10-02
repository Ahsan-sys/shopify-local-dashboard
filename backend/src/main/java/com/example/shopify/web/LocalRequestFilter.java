package com.example.shopify.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalRequestFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(LocalRequestFilter.class);
  private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");

    boolean api = request.getRequestURI().startsWith("/api/");
    if (!api) {
      chain.doFilter(request, response);
      return;
    }
    long started = System.nanoTime();
    String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    response.setHeader("X-Request-ID", requestId);
    log.info("API {} {} started", request.getMethod(), request.getRequestURI());
    try {
      response.setHeader("Cache-Control", "no-store");
      if (!LOCAL_HOSTS.contains(request.getServerName()) || !allowedOrigin(request)) {
        log.warn("API request rejected by local Host/Origin checks");
        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Only requests from the local dashboard are allowed.\",\"details\":[]}");
        return;
      }
      chain.doFilter(request, response);
    } finally {
      log.info("API {} {} completed status={} elapsedMs={}", request.getMethod(), request.getRequestURI(), response.getStatus(), (System.nanoTime() - started) / 1_000_000);
      MDC.remove("requestId");
    }
  }

  private boolean allowedOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin == null) return !"cross-site".equals(request.getHeader("Sec-Fetch-Site"));
    try {
      URI uri = URI.create(origin);
      return "http".equals(uri.getScheme()) && LOCAL_HOSTS.contains(uri.getHost()) && (uri.getPort() == request.getServerPort() || uri.getPort() == 5173);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
