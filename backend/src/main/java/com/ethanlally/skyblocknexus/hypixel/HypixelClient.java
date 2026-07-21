package com.ethanlally.skyblocknexus.hypixel;

import com.ethanlally.skyblocknexus.player.PlayerSummary;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockProfileSummary;
import java.util.ArrayList;
import java.util.List;
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
        JsonNode response = get("/v2/player", "uuid", uuid);

        JsonNode player = response == null ? null : response.get("player");
        if (player == null || player.isNull()) {
            throw new IllegalArgumentException("Player not found");
        }

        return new PlayerSummary(
                player.path("uuid").asString(uuid),
                player.path("displayname").asString("Unknown"),
                optionalLong(player, "firstLogin"),
                optionalLong(player, "lastLogin"));
    }

    public List<SkyBlockProfileSummary> getSkyBlockProfiles(String uuid) {
        JsonNode response = get("/v2/skyblock/profiles", "uuid", uuid);
        JsonNode profiles = response == null ? null : response.get("profiles");
        if (profiles == null || profiles.isNull()) {
            return List.of();
        }
        if (!profiles.isArray()) {
            throw new IllegalStateException("Hypixel profile response was malformed");
        }

        List<SkyBlockProfileSummary> summaries = new ArrayList<>();
        for (JsonNode profile : profiles) {
            String profileId = profile.path("profile_id").asString();
            if (profileId.isBlank()) {
                throw new IllegalStateException("Hypixel profile did not include an ID");
            }

            summaries.add(new SkyBlockProfileSummary(
                    profileId,
                    profile.path("cute_name").asString("Unnamed"),
                    profile.path("selected").asBoolean(),
                    optionalString(profile, "game_mode")));
        }
        return List.copyOf(summaries);
    }

    private JsonNode get(String path, String queryParameter, String value) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("HYPIXEL_API_KEY is not configured");
        }

        rateLimiter.acquire();

        ResponseEntity<JsonNode> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam(queryParameter, value).build())
                .header("API-Key", apiKey)
                .retrieve()
                .onStatus(
                        status -> status == HttpStatus.TOO_MANY_REQUESTS,
                        (request, upstreamResponse) -> {
                            throw rateLimiter.rejectedByUpstream(upstreamResponse.getHeaders());
                        })
                .toEntity(JsonNode.class);

        rateLimiter.update(response.getHeaders());
        return response.getBody();
    }

    private Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String optionalString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
