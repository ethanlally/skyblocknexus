package com.ethanlally.skyblocknexus.hypixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ethanlally.skyblocknexus.bazaar.BazaarController;
import com.ethanlally.skyblocknexus.http.UpstreamErrorHandler;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class BazaarControllerTests {

    private static final String UPSTREAM_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String API_PATH = "/api/bazaar/products";

    @Test
    void returnsSortedProductsAndTheUpstreamTimestampWithoutAnApiKey() throws Exception {
        TestContext context = testContext("", HypixelResponseCache.withDefaults());
        context.server().expect(requestTo(UPSTREAM_URL))
                .andExpect(headerDoesNotExist("API-Key"))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        context.mvc().perform(get(API_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastUpdated").value(1791238200000L))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].productId").value("INK_SACK:3"))
                .andExpect(jsonPath("$.products[0].buyPrice").value(0))
                .andExpect(jsonPath("$.products[0].sellVolume").value(0))
                .andExpect(jsonPath("$.products[1].productId").value("WHEAT"))
                .andExpect(jsonPath("$.products[1].buyPrice").value(6.25))
                .andExpect(jsonPath("$.products[1].sellPrice").value(5.5))
                .andExpect(jsonPath("$.products[1].buyVolume").value(3200000000L))
                .andExpect(jsonPath("$.products[1].sellVolume").value(700))
                .andExpect(jsonPath("$.products[1].buyMovingWeek").value(5000000000L))
                .andExpect(jsonPath("$.products[1].sellMovingWeek").value(4000000000L))
                .andExpect(jsonPath("$.products[1].buyOrders").value(14))
                .andExpect(jsonPath("$.products[1].sellOrders").value(9));
        context.server().verify();
    }

    @Test
    void neverSendsAConfiguredApiKeyAndServesCacheDuringCooldown() throws Exception {
        TestContext context = testContext("private-test-key", HypixelResponseCache.withDefaults());
        context.server().expect(requestTo(UPSTREAM_URL))
                .andExpect(headerDoesNotExist("API-Key"))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON)
                        .header("RateLimit-Remaining", "0").header("RateLimit-Reset", "30"));

        String firstBody = context.mvc().perform(get(API_PATH)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String cachedBody = context.mvc().perform(get(API_PATH)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(cachedBody).isEqualTo(firstBody);
        context.server().verify();
    }

    @Test
    void fetchesANewSnapshotWhenTheCacheExpires() throws Exception {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-10-05T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        TestContext context = testContext("", new HypixelResponseCache(Duration.ofSeconds(60), 100, clock));
        context.server().expect(requestTo(UPSTREAM_URL))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));
        context.server().expect(requestTo(UPSTREAM_URL))
                .andRespond(withSuccess(fixture().replace("1791238200000", "1791238260000"),
                        MediaType.APPLICATION_JSON));

        context.mvc().perform(get(API_PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastUpdated").value(1791238200000L));
        when(clock.instant()).thenReturn(now.plusSeconds(59));
        context.mvc().perform(get(API_PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastUpdated").value(1791238200000L));
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        context.mvc().perform(get(API_PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastUpdated").value(1791238260000L));
        context.server().verify();
    }

    @Test
    void acceptsAnEmptyProductMapAsAnEmptyList() throws Exception {
        TestContext context = testContext("", HypixelResponseCache.withDefaults());
        context.server().expect(requestTo(UPSTREAM_URL))
                .andRespond(withSuccess("{\"success\":true,\"lastUpdated\":123,\"products\":{}}",
                        MediaType.APPLICATION_JSON));
        context.mvc().perform(get(API_PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products").isEmpty());
        context.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{", "{}", "{\"success\":false}",
            "{\"success\":\"true\",\"lastUpdated\":123,\"products\":{}}",
            "{\"success\":true,\"products\":{}}",
            "{\"success\":true,\"lastUpdated\":-1,\"products\":{}}",
            "{\"success\":true,\"lastUpdated\":\"123\",\"products\":{}}",
            "{\"success\":true,\"lastUpdated\":123,\"products\":null}",
            "{\"success\":true,\"lastUpdated\":123,\"products\":[]}",
            "{\"success\":true,\"lastUpdated\":123,\"products\":{\"WHEAT\":null}}"
    })
    void rejectsMalformedSnapshotsWithoutCachingThem(String body) throws Exception {
        assertRejectedThenRecoverable(body);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            buyPrice | -1
            buyPrice | "wrong-type"
            buyPrice | 1e400
            sellPrice | null
            buyVolume | 1.5
            buyVolume | 9223372036854775808
            sellVolume | -1
            buyMovingWeek | null
            sellMovingWeek | "123"
            buyOrders | -1
            sellOrders | 0.5
            """)
    void rejectsInvalidNumbersInsteadOfCoercingThemToZero(String field, String invalidValue) throws Exception {
        String body = fixture().replaceFirst("\"" + field + "\"\\s*:\\s*[0-9.]+",
                "\"" + field + "\":" + invalidValue);
        assertRejectedThenRecoverable(body);
    }

    @Test
    void rejectsMissingPricesAndMismatchedProductIds() throws Exception {
        assertRejectedThenRecoverable(fixture().replace("\"buyPrice\": 6.25,", ""));
        assertRejectedThenRecoverable(fixture().replace("\"product_id\": \"WHEAT\"", "\"product_id\": \"OTHER\""));
        assertRejectedThenRecoverable(fixture().replace("\"productId\": \"WHEAT\"", "\"productId\": \"OTHER\""));
        assertRejectedThenRecoverable(fixture().replace("\"quick_status\"", "\"missing_status\""));
    }

    @Test
    void throttlesLocallyAfterAnUpstream429() throws Exception {
        TestContext context = testContext("", HypixelResponseCache.withDefaults());
        context.server().expect(requestTo(UPSTREAM_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "17"));
        context.mvc().perform(get(API_PATH)).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "17"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(17));
        context.mvc().perform(get(API_PATH)).andExpect(status().isTooManyRequests());
        context.server().verify();
    }

    @Test
    void returnsBadGatewayWhenBazaarIsUnavailableAndAllowsARetry() throws Exception {
        TestContext context = testContext("", HypixelResponseCache.withDefaults());
        context.server().expect(requestTo(UPSTREAM_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        context.server().expect(requestTo(UPSTREAM_URL)).andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));
        context.mvc().perform(get(API_PATH)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").isString());
        context.mvc().perform(get(API_PATH)).andExpect(status().isOk());
        context.server().verify();
    }

    @Test
    void returnsGatewayTimeoutForASlowUpstream() throws Exception {
        TestContext context = testContext("", HypixelResponseCache.withDefaults());
        context.server().expect(requestTo(UPSTREAM_URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });
        context.mvc().perform(get(API_PATH)).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").isString());
        context.server().verify();
    }

    private void assertRejectedThenRecoverable(String body) throws Exception {
        TestContext context = testContext("", HypixelResponseCache.withDefaults());
        context.server().expect(requestTo(UPSTREAM_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        context.server().expect(requestTo(UPSTREAM_URL)).andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));
        context.mvc().perform(get(API_PATH)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").isString());
        context.mvc().perform(get(API_PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(2));
        context.server().verify();
    }

    private TestContext testContext(String apiKey, HypixelResponseCache cache) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.hypixel.net");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HypixelClient client = new HypixelClient(apiKey, builder.build(), new HypixelRateLimiter(), cache);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BazaarController(client))
                .setControllerAdvice(new UpstreamErrorHandler(), new HypixelRateLimitHandler()).build();
        return new TestContext(server, mvc);
    }

    private String fixture() throws Exception {
        return new ClassPathResource("fixtures/hypixel/bazaar-success.json").getContentAsString(StandardCharsets.UTF_8);
    }

    private record TestContext(MockRestServiceServer server, MockMvc mvc) {}
}
