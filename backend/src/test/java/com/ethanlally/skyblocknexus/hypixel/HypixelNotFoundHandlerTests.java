package com.ethanlally.skyblocknexus.hypixel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HypixelNotFoundHandlerTests {

    @Test
    void returnsANotFoundResponseWithAUsefulMessage() {
        var response = new HypixelNotFoundHandler()
                .handleNotFound(new HypixelDataNotFoundException("SkyBlock profile was not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("SkyBlock profile was not found");
    }
}
