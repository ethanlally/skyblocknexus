package com.ethanlally.skyblocknexus.bazaar;

public record BazaarProduct(
        String productId,
        double buyPrice,
        double sellPrice,
        long buyVolume,
        long sellVolume,
        long buyMovingWeek,
        long sellMovingWeek,
        long buyOrders,
        long sellOrders) {}
