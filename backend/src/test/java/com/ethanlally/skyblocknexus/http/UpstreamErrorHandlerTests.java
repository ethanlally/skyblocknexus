package com.ethanlally.skyblocknexus.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

class UpstreamErrorHandlerTests {

    private final UpstreamErrorHandler handler = new UpstreamErrorHandler();

    @Test
    void malformedResponsesUseBadGateway() {
        var response = handler.invalidResponse(new UpstreamResponseException("Hypixel"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().message()).contains("Hypixel", "malformed");
    }

    @Test
    void timeoutsUseGatewayTimeout() {
        var response = handler.requestFailed(new ResourceAccessException("Sensitive URL",
                new SocketTimeoutException("Read timed out")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().message()).contains("took too long").doesNotContain("Sensitive");
    }

    @Test
    void otherUpstreamErrorsDoNotExposeSensitiveDetails() {
        var response = handler.requestFailed(new RestClientException("Private upstream body or URL"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().message()).doesNotContain("Private upstream body or URL");
    }
}
