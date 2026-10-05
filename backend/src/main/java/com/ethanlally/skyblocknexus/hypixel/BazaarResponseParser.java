package com.ethanlally.skyblocknexus.hypixel;

import com.ethanlally.skyblocknexus.bazaar.BazaarProduct;
import com.ethanlally.skyblocknexus.bazaar.BazaarSnapshot;
import com.ethanlally.skyblocknexus.http.UpstreamResponseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

final class BazaarResponseParser {

    private BazaarResponseParser() {}

    static BazaarSnapshot parse(JsonNode response) {
        if (response == null || !response.isObject()
                || !response.path("success").isBoolean() || !response.path("success").asBoolean()
                || !response.path("products").isObject()) {
            throw new UpstreamResponseException("Hypixel Bazaar");
        }
        long lastUpdated = nonNegativeLong(response, "lastUpdated");
        List<BazaarProduct> products = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : response.path("products").properties()) {
            String productId = entry.getKey();
            JsonNode product = entry.getValue();
            JsonNode status = product.path("quick_status");
            if (productId.isBlank() || !product.path("product_id").isString()
                    || !productId.equals(product.path("product_id").asString())
                    || !status.isObject() || !status.path("productId").isString()
                    || !productId.equals(status.path("productId").asString())) {
                throw new UpstreamResponseException("Hypixel Bazaar");
            }
            products.add(new BazaarProduct(
                    productId,
                    nonNegativePrice(status, "buyPrice"),
                    nonNegativePrice(status, "sellPrice"),
                    nonNegativeLong(status, "buyVolume"),
                    nonNegativeLong(status, "sellVolume"),
                    nonNegativeLong(status, "buyMovingWeek"),
                    nonNegativeLong(status, "sellMovingWeek"),
                    nonNegativeLong(status, "buyOrders"),
                    nonNegativeLong(status, "sellOrders")));
        }
        products.sort(Comparator.comparing(BazaarProduct::productId));
        return new BazaarSnapshot(lastUpdated, products);
    }

    private static long nonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0) {
            throw new UpstreamResponseException("Hypixel Bazaar");
        }
        return value.asLong();
    }

    private static double nonNegativePrice(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0) {
            throw new UpstreamResponseException("Hypixel Bazaar");
        }
        return value.asDouble();
    }
}
