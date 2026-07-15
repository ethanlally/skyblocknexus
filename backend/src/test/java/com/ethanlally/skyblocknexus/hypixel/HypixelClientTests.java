package com.ethanlally.skyblocknexus.hypixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HypixelClientTests {

    @Test
    void readsAPlayerFromTheHypixelResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HypixelClient client = new HypixelClient("test-key", builder
                .baseUrl("https://api.hypixel.net")
                .build());
        String fixture = new ClassPathResource("fixtures/hypixel/player-success.json")
                .getContentAsString(StandardCharsets.UTF_8);

        server.expect(queryParam("uuid", "0123456789abcdef0123456789abcdef"))
                .andExpect(header("API-Key", "test-key"))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        var player = client.getPlayer("0123456789abcdef0123456789abcdef");

        assertThat(player.displayName()).isEqualTo("ExamplePlayer");
        assertThat(player.lastLogin()).isEqualTo(1711929600000L);
        server.verify();
    }
}
