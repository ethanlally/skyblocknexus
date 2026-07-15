package com.ethanlally.skyblocknexus.player;

public record PlayerSummary(
        String uuid,
        String displayName,
        Long firstLogin,
        Long lastLogin) {}
