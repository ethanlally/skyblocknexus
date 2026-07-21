package com.ethanlally.skyblocknexus.minecraft;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/minecraft/players")
public class MinecraftController {

    private final MinecraftClient minecraftClient;

    public MinecraftController(MinecraftClient minecraftClient) {
        this.minecraftClient = minecraftClient;
    }

    @GetMapping("/{username}")
    public MinecraftPlayer resolveUsername(@PathVariable String username) {
        return minecraftClient.resolveUsername(username);
    }
}
