package com.ethanlally.skyblocknexus.hypixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class HypixelClientFailureTests {

    @Test
    void preservesTheInvalidApiKeyResponse() throws Exception {
        TestContext context = testContext();
        context.server().expect(queryParam("uuid", "example-player"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("invalid-api-key.json")));

        assertThatThrownBy(() -> context.client().getPlayer("example-player"))
                .isInstanceOfSatisfying(HttpClientErrorException.Forbidden.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getResponseBodyAsString()).contains("Invalid API key");
                });
        context.server().verify();
    }

    @Test
    void reportsWhenThePlayerIsMissing() throws Exception {
        TestContext context = testContext();
        context.server().expect(queryParam("uuid", "missing-player"))
                .andRespond(withSuccess(
                        fixture("player-not-found.json"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.client().getPlayer("missing-player"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Player not found");
        context.server().verify();
    }

    @Test
    void rejectsMalformedJsonResponses() throws Exception {
        TestContext context = testContext();
        context.server().expect(queryParam("uuid", "example-player"))
                .andRespond(withSuccess(
                        fixture("malformed-response.json"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.client().getPlayer("example-player"))
                .isInstanceOf(RestClientException.class);
        context.server().verify();
    }

    @Test
    void surfacesNetworkTimeouts() {
        TestContext context = testContext();
        context.server().expect(queryParam("uuid", "example-player"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> context.client().getPlayer("example-player"))
                .isInstanceOf(ResourceAccessException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
        context.server().verify();
    }

    private TestContext testContext() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HypixelClient client = new HypixelClient(
                "test-key",
                builder.baseUrl("https://api.hypixel.net").build(),
                new HypixelRateLimiter());
        return new TestContext(client, server);
    }

    private String fixture(String name) throws Exception {
        return new ClassPathResource("fixtures/hypixel/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private record TestContext(HypixelClient client, MockRestServiceServer server) {}
}
