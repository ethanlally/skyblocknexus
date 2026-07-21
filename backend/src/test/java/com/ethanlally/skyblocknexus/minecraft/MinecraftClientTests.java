package com.ethanlally.skyblocknexus.minecraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MinecraftClientTests {

    @Test
    void resolvesAUsernameToAUuid() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MinecraftClient client = new MinecraftClient(builder
                .baseUrl("https://api.minecraftservices.com")
                .build());
        String fixture = new ClassPathResource("fixtures/minecraft/player-success.json")
                .getContentAsString(StandardCharsets.UTF_8);

        server.expect(requestTo(
                        "https://api.minecraftservices.com/minecraft/profile/lookup/name/Notch"))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        MinecraftPlayer player = client.resolveUsername("Notch");

        assertThat(player.uuid()).isEqualTo("069a79f444e94726a5befca90e38aaf5");
        assertThat(player.username()).isEqualTo("Notch");
        server.verify();
    }

    @Test
    void reportsAnUnknownUsername() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MinecraftClient client = new MinecraftClient(builder
                .baseUrl("https://api.minecraftservices.com")
                .build());

        server.expect(requestTo(
                        "https://api.minecraftservices.com/minecraft/profile/lookup/name/UnknownPlayer"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.resolveUsername("UnknownPlayer"))
                .isInstanceOf(MinecraftPlayerNotFoundException.class)
                .hasMessage("Minecraft player not found: UnknownPlayer");
        server.verify();
    }
}
