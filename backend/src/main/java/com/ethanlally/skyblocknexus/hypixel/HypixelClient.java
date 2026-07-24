package com.ethanlally.skyblocknexus.hypixel;

import com.ethanlally.skyblocknexus.player.PlayerSummary;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockCollectionProgress;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockCurrencySummary;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockEquipmentItem;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockItemDecoder;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockProfileProgress;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockProfileSummary;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockSkillProgress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final SkyBlockItemDecoder itemDecoder = new SkyBlockItemDecoder();

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

    public SkyBlockProfileProgress getSkyBlockProfileProgress(String uuid, String profileId) {
        JsonNode response = get("/v2/skyblock/profile", "profile", profileId);
        JsonNode profile = response == null ? null : response.get("profile");
        if (profile == null || profile.isNull()) {
            throw new IllegalArgumentException("SkyBlock profile not found");
        }

        JsonNode member = profile.path("members").get(uuid.replace("-", ""));
        if (member == null || member.isNull()) {
            throw new IllegalArgumentException("Player is not a member of that SkyBlock profile");
        }

        JsonNode skillDefinitions = get("/v2/resources/skyblock/skills").path("skills");
        JsonNode collectionDefinitions = get("/v2/resources/skyblock/collections").path("collections");

        return new SkyBlockProfileProgress(
                profile.path("profile_id").asString(profileId),
                readCurrencies(profile, member),
                readEquipment(member),
                readSkillProgress(member, skillDefinitions),
                readCollectionProgress(member, collectionDefinitions));
    }

    private SkyBlockCurrencySummary readCurrencies(JsonNode profile, JsonNode member) {
        JsonNode currencies = member.path("currencies");
        return new SkyBlockCurrencySummary(
                optionalDouble(currencies, "coin_purse"),
                optionalDouble(profile.path("banking"), "balance"),
                optionalDouble(currencies, "motes_purse"));
    }

    private List<SkyBlockEquipmentItem> readEquipment(JsonNode member) {
        JsonNode inventory = member.path("inventory");
        List<SkyBlockEquipmentItem> items = new ArrayList<>();
        items.addAll(itemDecoder.decodeItems(
                inventory.path("inv_armor").path("data").asString(),
                "Armor"));

        List<SkyBlockEquipmentItem> equipment = itemDecoder.decodeItems(
                inventory.path("equipment_contents").path("data").asString(),
                "Equipment");
        if (equipment.isEmpty()) {
            equipment = readEquipmentLoadout(member.path("loadout"));
        }
        items.addAll(equipment);
        return List.copyOf(items);
    }

    private List<SkyBlockEquipmentItem> readEquipmentLoadout(JsonNode loadout) {
        String equippedSet = loadout.path("armor").path("equipped_set").asString();
        JsonNode equipmentSet = loadout.path("equipment").get(equippedSet);
        if (equipmentSet == null || !equipmentSet.isObject()) {
            return List.of();
        }

        List<SkyBlockEquipmentItem> equipment = new ArrayList<>();
        for (Map.Entry<String, JsonNode> slot : equipmentSet.properties()) {
            if (!slot.getKey().startsWith("EQUIPMENT_SLOT_")) {
                continue;
            }
            equipment.addAll(itemDecoder.decodeItems(
                    slot.getValue().path("data").asString(),
                    "Equipment"));
        }
        return List.copyOf(equipment);
    }

    private List<SkyBlockSkillProgress> readSkillProgress(
            JsonNode member,
            JsonNode definitions) {
        if (!definitions.isObject()) {
            throw new IllegalStateException("Hypixel skill definitions were malformed");
        }

        List<SkyBlockSkillProgress> skills = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : definitions.properties()) {
            Double experience = skillExperience(member, entry.getKey());
            if (experience == null) {
                continue;
            }

            JsonNode definition = entry.getValue();
            LevelProgress progress = levelProgress(experience, definition.path("levels"),
                    "totalExpRequired");
            skills.add(new SkyBlockSkillProgress(
                    definition.path("name").asString(formatIdentifier(entry.getKey())),
                    progress.level(),
                    experience,
                    progress.amountIntoLevel(),
                    progress.amountForNextLevel()));
        }

        skills.sort(Comparator.comparing(SkyBlockSkillProgress::name));
        return List.copyOf(skills);
    }

    private Double skillExperience(JsonNode member, String skillId) {
        JsonNode legacyExperience = member.get(
                "experience_skill_" + skillId.toLowerCase(Locale.ROOT));
        if (legacyExperience != null && legacyExperience.isNumber()) {
            return legacyExperience.asDouble();
        }

        JsonNode experience = member.path("player_data")
                .path("experience")
                .get("SKILL_" + skillId);
        return experience != null && experience.isNumber() ? experience.asDouble() : null;
    }

    private List<SkyBlockCollectionProgress> readCollectionProgress(
            JsonNode member,
            JsonNode definitions) {
        JsonNode collection = member.get("collection");
        if (collection == null || !collection.isObject()) {
            return List.of();
        }
        if (!definitions.isObject()) {
            throw new IllegalStateException("Hypixel collection definitions were malformed");
        }

        Map<String, JsonNode> itemsById = new HashMap<>();
        for (Map.Entry<String, JsonNode> category : definitions.properties()) {
            JsonNode items = category.getValue().path("items");
            if (!items.isObject()) {
                continue;
            }
            for (Map.Entry<String, JsonNode> item : items.properties()) {
                itemsById.put(item.getKey(), item.getValue());
            }
        }

        List<SkyBlockCollectionProgress> collections = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : collection.properties()) {
            if (!entry.getValue().isNumber()) {
                continue;
            }

            String itemId = entry.getKey();
            long amount = entry.getValue().asLong();
            JsonNode definition = itemsById.get(itemId);
            JsonNode tiers = definition == null ? null : definition.path("tiers");
            LevelProgress progress = levelProgress(amount, tiers, "amountRequired");
            collections.add(new SkyBlockCollectionProgress(
                    itemId,
                    definition == null
                            ? formatIdentifier(itemId)
                            : definition.path("name").asString(formatIdentifier(itemId)),
                    amount,
                    progress.level(),
                    (long) progress.amountIntoLevel(),
                    progress.amountForNextLevel() == null
                            ? null
                            : progress.amountForNextLevel().longValue()));
        }

        collections.sort(Comparator.comparingLong(SkyBlockCollectionProgress::totalAmount)
                .reversed());
        return List.copyOf(collections);
    }

    private LevelProgress levelProgress(double amount, JsonNode levels, String thresholdField) {
        if (levels == null || !levels.isArray()) {
            return new LevelProgress(0, amount, null);
        }

        int level = 0;
        double currentThreshold = 0;
        Double nextThreshold = null;
        for (JsonNode levelDefinition : levels) {
            double threshold = levelDefinition.path(thresholdField).asDouble();
            if (amount < threshold) {
                nextThreshold = threshold;
                break;
            }
            level = levelDefinition.path("level").asInt(level + 1);
            currentThreshold = threshold;
        }

        return new LevelProgress(
                level,
                Math.max(0, amount - currentThreshold),
                nextThreshold == null ? null : nextThreshold - currentThreshold);
    }

    private String formatIdentifier(String identifier) {
        String[] words = identifier.toLowerCase(Locale.ROOT).split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!formatted.isEmpty()) {
                formatted.append(' ');
            }
            formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return formatted.toString();
    }

    private JsonNode get(String path, String queryParameter, String value) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("HYPIXEL_API_KEY is not configured");
        }

        return request(restClient.get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam(queryParameter, value).build())
                .header("API-Key", apiKey));
    }

    private JsonNode get(String path) {
        return request(restClient.get().uri(path));
    }

    private JsonNode request(RestClient.RequestHeadersSpec<?> requestSpec) {
        rateLimiter.acquire();

        ResponseEntity<JsonNode> response = requestSpec.retrieve()
                .onStatus(
                        status -> status == HttpStatus.TOO_MANY_REQUESTS,
                        (request, upstreamResponse) -> {
                            throw rateLimiter.rejectedByUpstream(upstreamResponse.getHeaders());
                        })
                .toEntity(JsonNode.class);

        rateLimiter.update(response.getHeaders());
        return response.getBody();
    }

    private record LevelProgress(
            int level,
            double amountIntoLevel,
            Double amountForNextLevel) {}

    private Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private Double optionalDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private String optionalString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
