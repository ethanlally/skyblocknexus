package com.ethanlally.skyblocknexus.hypixel;

import com.ethanlally.skyblocknexus.player.PlayerSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class HypixelClient {

    private final String apiKey;
    private final RestClient restClient;
    private final HypixelRateLimiter rateLimiter;

    @Autowired
    public HypixelClient(
            @Value("${hypixel.api-key:}") String apiKey,
            HypixelRateLimiter rateLimiter) {
        this(apiKey, RestClient.builder()
                .baseUrl("https://api.hypixel.net")
                .build(), rateLimiter);
    }

    HypixelClient(String apiKey, RestClient restClient, HypixelRateLimiter rateLimiter) {
        this.apiKey = apiKey;
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
    }

    public PlayerSummary getPlayer(String uuid) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("HYPIXEL_API_KEY is not configured");
        }

        rateLimiter.acquire();

        ResponseEntity<JsonNode> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v2/player").queryParam("uuid", uuid).build())
                .header("API-Key", apiKey)
                .retrieve()
                .onStatus(
                        status -> status == HttpStatus.TOO_MANY_REQUESTS,
                        (request, upstreamResponse) -> {
                            throw rateLimiter.rejectedByUpstream(upstreamResponse.getHeaders());
                        })
                .toEntity(JsonNode.class);

        rateLimiter.update(response.getHeaders());

        JsonNode body = response.getBody();
        JsonNode player = body == null ? null : body.get("player");
        if (player == null || player.isNull()) {
            throw new IllegalArgumentException("Player not found");
        }

        return new PlayerSummary(
                player.path("uuid").asString(uuid),
                player.path("displayname").asString("Unknown"),
                optionalLong(player, "firstLogin"),
                optionalLong(player, "lastLogin"));
    }

    private Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }
}
