package com.example.shopify.shopify;

import static org.assertj.core.api.Assertions.*;

import com.example.shopify.config.ShopifySettings;
import com.example.shopify.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ShopifyClientTest {
  private final JsonMapper mapper = new JsonMapper();
  private final ShopifySettings settings =
      new ShopifySettings("test-shop", "test-client", "test-secret&encoded", "2026-07");
  private FakeTransport transport;
  private MutableClock clock;
  private TokenProvider tokens;
  private ShopifyClient client;

  @BeforeEach
  void setup() {
    transport = new FakeTransport();
    clock = new MutableClock();
    tokens = new TokenProvider(transport, settings, mapper, clock);
    client = new ShopifyClient(transport, tokens, settings, mapper);
  }

  @Test
  void exchangesClientCredentialsAndKeepsTokenOnlyInBackendHeader() {
    token("token-1", 3600);
    transport.add(200, "{\"data\":{\"shop\":{\"name\":\"Demo\"}}}");
    assertThat(
            client
                .execute("query Shop { shop { name } }", Map.of())
                .path("shop")
                .path("name")
                .asText())
        .isEqualTo("Demo");
    var auth = transport.calls.get(0);
    assertThat(auth.path()).isEqualTo("/admin/oauth/access_token");
    assertThat(auth.headers()).containsEntry("Content-Type", "application/x-www-form-urlencoded");
    assertThat(auth.body())
        .isEqualTo(
            "grant_type=client_credentials&client_id=test-client&client_secret=test-secret%26encoded");
    var graph = transport.calls.get(1);
    assertThat(graph.path()).isEqualTo("/admin/api/2026-07/graphql.json");
    assertThat(graph.headers()).containsEntry("X-Shopify-Access-Token", "token-1");
    assertThat(graph.body()).doesNotContain("test-secret", "token-1");
  }

  @Test
  void cachesTokenAndRenewsBeforeExpiry() {
    token("first", 3600);
    token("second", 3600);
    assertThat(tokens.getToken()).isEqualTo("first");
    clock.advance(3500);
    assertThat(tokens.getToken()).isEqualTo("first");
    clock.advance(41);
    assertThat(tokens.getToken()).isEqualTo("second");
    assertThat(transport.calls).hasSize(2);
  }

  @Test
  void concurrentRequestsOnlyExchangeCredentialsOnce() throws Exception {
    token("shared-token", 3600);
    var executor = Executors.newFixedThreadPool(8);
    try {
      var jobs = new ArrayList<java.util.concurrent.Callable<String>>();
      for (int i = 0; i < 20; i++) jobs.add(tokens::getToken);
      for (var result : executor.invokeAll(jobs))
        assertThat(result.get()).isEqualTo("shared-token");
      assertThat(transport.calls).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void lateUnauthorizedResponseDoesNotInvalidateNewerToken() {
    token("old", 3600);
    token("new", 3600);
    assertThat(tokens.getToken()).isEqualTo("old");
    tokens.invalidate("old");
    assertThat(tokens.getToken()).isEqualTo("new");
    tokens.invalidate("old");
    assertThat(tokens.getToken()).isEqualTo("new");
    assertThat(transport.calls).hasSize(2);
  }

  @Test
  void retriesOneExplicit401WithNewToken() {
    token("old", 3600);
    transport.add(401, "denied");
    token("new", 3600);
    transport.add(200, "{\"data\":{\"ok\":true}}");
    assertThat(client.execute("query { ok }", Map.of()).path("ok").asBoolean()).isTrue();
    assertThat(transport.calls).hasSize(4);
    assertThat(transport.calls.get(3).headers()).containsEntry("X-Shopify-Access-Token", "new");
  }

  @Test
  void stopsAfterSecond401() {
    token("old", 3600);
    transport.add(401, "denied");
    token("new", 3600);
    transport.add(401, "denied");
    assertThatThrownBy(() -> client.execute("query { ok }", Map.of()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("rejected the access token");
    assertThat(transport.calls).hasSize(4);
  }

  @Test
  void topLevelGraphqlErrorsWithHttp200AreVisibleAndRedacted() {
    token("private-token", 3600);
    transport.add(
        200,
        "{\"errors\":[{\"message\":\"Access denied private-token"
            + " test-secret&encoded\"}],\"data\":{\"shop\":null}}");
    ApiException ex =
        catchThrowableOfType(
            () -> client.execute("query { shop { name } }", Map.of()), ApiException.class);
    assertThat(ex.status()).isEqualTo(502);
    assertThat(ex.details()).containsExactly("Access denied [redacted] [redacted]");
  }

  @Test
  void mutationUserErrorsAreNotTreatedAsSuccess() {
    token("token", 3600);
    transport.add(
        200,
        "{\"data\":{\"productUpdate\":{\"product\":null,\"userErrors\":[{\"field\":[\"product\",\"title\"],\"message\":\"Title"
            + " cannot be blank\"}]}}}");
    ApiException ex =
        catchThrowableOfType(
            () -> client.mutate("mutation X { x }", Map.of(), "productUpdate", "product"),
            ApiException.class);
    assertThat(ex.status()).isEqualTo(422);
    assertThat(ex.details()).containsExactly("title: Title cannot be blank");
  }

  @Test
  void successfulMutationReturnsOnlyRequestedPublicResult() {
    token("token", 3600);
    transport.add(
        200,
        "{\"data\":{\"orderUpdate\":{\"order\":{\"id\":\"1\",\"note\":\"OK\"},\"userErrors\":[]}}}");
    assertThat(
            client
                .mutate("mutation X { x }", Map.of(), "orderUpdate", "order")
                .path("note")
                .asText())
        .isEqualTo("OK");
  }

  @Test
  void graphqlThrottlingIsVisibleWithoutAutomaticMutationRetry() {
    token("token", 3600);
    transport.add(
        200, "{\"errors\":[{\"message\":\"Throttled\",\"extensions\":{\"code\":\"THROTTLED\"}}]}");
    ApiException ex =
        catchThrowableOfType(
            () -> client.execute("mutation X { x }", Map.of()), ApiException.class);
    assertThat(ex.status()).isEqualTo(429);
    assertThat(transport.calls).hasSize(2);
  }

  @ParameterizedTest
  @ValueSource(ints = {403, 429, 500, 503})
  void doesNotReplayMutationOnHttpFailure(int status) {
    token("token", 3600);
    transport.add(status, "potentially sensitive raw upstream body");
    ApiException ex =
        catchThrowableOfType(
            () -> client.execute("mutation X { x }", Map.of()), ApiException.class);
    assertThat(ex.getMessage()).doesNotContain("sensitive");
    assertThat(transport.calls).hasSize(2);
    assertThat(ex.status()).isEqualTo(status == 429 ? 429 : 502);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-json", "null", "{}", "{\"data\":null}"})
  void rejectsInvalidGraphqlResponses(String body) {
    token("token", 3600);
    transport.add(200, body);
    assertThatThrownBy(() -> client.execute("query { x }", Map.of()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unexpected response");
  }

  @Test
  void doesNotRetryWhenMutationTransportFailsWithUnknownOutcome() {
    var failAfterToken =
        new ShopifyTransport() {
          int calls;

          public Reply post(String path, Map<String, String> headers, String body) {
            calls++;
            if (calls == 1)
              return new Reply(200, "{\"access_token\":\"token\",\"expires_in\":3600}");
            if (calls > 2) fail("Unsafe automatic retry");
            throw new ApiException(502, "Result unknown");
          }
        };
    var tokenProvider = new TokenProvider(failAfterToken, settings, mapper, clock);
    var failingClient = new ShopifyClient(failAfterToken, tokenProvider, settings, mapper);
    assertThatThrownBy(() -> failingClient.execute("mutation X { x }", Map.of()))
        .hasMessage("Result unknown");
  }

  @Test
  void authenticationFailureDoesNotExposeTheTokenResponse() {
    transport.add(400, "test-secret&encoded PRIVATE");
    assertThatThrownBy(tokens::getToken)
        .isInstanceOf(ApiException.class)
        .hasMessageNotContaining("PRIVATE")
        .hasMessageNotContaining("test-secret");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "null", "garbage", "{\"access_token\":\"x\",\"expires_in\":0}"})
  void rejectsInvalidTokenResponses(String body) {
    transport.add(200, body);
    assertThatThrownBy(tokens::getToken)
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("invalid token response");
  }

  void token(String value, long ttl) {
    transport.add(200, "{\"access_token\":\"" + value + "\",\"expires_in\":" + ttl + "}");
  }

  static final class FakeTransport implements ShopifyTransport {
    record Call(String path, Map<String, String> headers, String body) {}

    final List<Call> calls = new ArrayList<>();
    final ArrayDeque<Reply> replies = new ArrayDeque<>();

    void add(int status, String body) {
      replies.add(new Reply(status, body));
    }

    @Override
    public Reply post(String path, Map<String, String> headers, String body) {
      calls.add(new Call(path, headers, body));
      return replies.remove();
    }
  }

  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-07-01T00:00:00Z");

    void advance(long seconds) {
      now = now.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
