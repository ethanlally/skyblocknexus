package com.ethanlally.skyblocknexus.hypixel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class HypixelResponseCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(1);
    private static final int DEFAULT_MAX_ENTRIES = 100;

    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public HypixelResponseCache(
            @Value("${hypixel.cache.ttl:60s}") Duration ttl,
            @Value("${hypixel.cache.max-entries:100}") int maxEntries) {
        this(ttl, maxEntries, Clock.systemUTC());
    }

    HypixelResponseCache(Duration ttl, int maxEntries, Clock clock) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache TTL must be positive");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("Cache size must be at least one");
        }

        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    static HypixelResponseCache withDefaults() {
        return new HypixelResponseCache(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    public synchronized JsonNode get(String key) {
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(key);
            return null;
        }
        return entry.response();
    }

    public synchronized void put(String key, JsonNode response) {
        if (response == null) {
            return;
        }

        entries.put(key, new CacheEntry(response, clock.instant().plus(ttl)));
        while (entries.size() > maxEntries) {
            String leastRecentlyUsedKey = entries.keySet().iterator().next();
            entries.remove(leastRecentlyUsedKey);
        }
    }

    private record CacheEntry(JsonNode response, Instant expiresAt) {}
}
