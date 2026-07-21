package com.ethanlally.skyblocknexus.minecraft;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class MinecraftClient {

    private final RestClient restClient;

    public MinecraftClient() {
        this(RestClient.builder()
                .baseUrl("https://api.minecraftservices.com")
                .build());
    }

    MinecraftClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public MinecraftPlayer resolveUsername(String username) {
        JsonNode response = restClient.get()
                .uri("/minecraft/profile/lookup/name/{username}", username)
                .retrieve()
                .onStatus(
                        status -> status == HttpStatus.NOT_FOUND,
                        (request, upstreamResponse) -> {
                            throw new MinecraftPlayerNotFoundException(username);
                        })
                .body(JsonNode.class);

        String uuid = response == null ? "" : response.path("id").asString();
        if (uuid.isBlank()) {
            throw new IllegalStateException("Minecraft profile response did not include a UUID");
        }

        return new MinecraftPlayer(
                uuid,
                response.path("name").asString(username));
    }
}
