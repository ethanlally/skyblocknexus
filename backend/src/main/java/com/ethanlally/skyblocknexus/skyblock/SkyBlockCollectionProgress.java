package com.ethanlally.skyblocknexus.skyblock;

public record SkyBlockCollectionProgress(
        String itemId,
        String name,
        long totalAmount,
        int tier,
        long amountIntoTier,
        Long amountForNextTier) {}
