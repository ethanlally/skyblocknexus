package com.ethanlally.skyblocknexus.minecraft;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class MinecraftPlayerNotFoundException extends RuntimeException {

    MinecraftPlayerNotFoundException(String username) {
        super("Minecraft player not found: " + username);
    }
}
