package com.ethanlally.skyblocknexus.bazaar;

import java.util.List;

public record BazaarSnapshot(long lastUpdated, List<BazaarProduct> products) {

    public BazaarSnapshot {
        products = List.copyOf(products);
    }
}
