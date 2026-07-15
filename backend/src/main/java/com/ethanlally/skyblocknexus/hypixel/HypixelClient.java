package com.ethanlally.skyblocknexus.hypixel;

import com.ethanlally.skyblocknexus.player.PlayerSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class HypixelClient {

    private final String apiKey;
    private final RestClient restClient;

    @Autowired
    public HypixelClient(@Value("${hypixel.api-key:}") String apiKey) {
        this(apiKey, RestClient.builder()
                .baseUrl("https://api.hypixel.net")
                .build());
    }

    HypixelClient(String apiKey, RestClient restClient) {
        this.apiKey = apiKey;
        this.restClient = restClient;
    }

    public PlayerSummary getPlayer(String uuid) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("HYPIXEL_API_KEY is not configured");
        }

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v2/player").queryParam("uuid", uuid).build())
                .header("API-Key", apiKey)
                .retrieve()
                .body(JsonNode.class);

        JsonNode player = response == null ? null : response.get("player");
        if (player == null || player.isNull()) {
            throw new IllegalArgumentException("Player not found");
        }

        return new PlayerSummary(
                player.path("uuid").asText(uuid),
                player.path("displayname").asText("Unknown"),
                optionalLong(player, "firstLogin"),
                optionalLong(player, "lastLogin"));
    }

    private Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }
}
