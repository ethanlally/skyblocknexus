package com.ethanlally.skyblocknexus.bazaar;

import com.ethanlally.skyblocknexus.hypixel.HypixelClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bazaar")
public class BazaarController {

    private final HypixelClient hypixelClient;

    public BazaarController(HypixelClient hypixelClient) {
        this.hypixelClient = hypixelClient;
    }

    @GetMapping("/products")
    public BazaarSnapshot getProducts() {
        return hypixelClient.getBazaarProducts();
    }
}
