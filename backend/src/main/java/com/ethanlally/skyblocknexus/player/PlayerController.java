package com.ethanlally.skyblocknexus.player;

import com.ethanlally.skyblocknexus.hypixel.HypixelClient;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockProfileProgress;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockProfileSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final HypixelClient hypixelClient;

    public PlayerController(HypixelClient hypixelClient) {
        this.hypixelClient = hypixelClient;
    }

    @GetMapping("/{uuid}")
    public PlayerSummary getPlayer(
            @PathVariable String uuid) {
        return hypixelClient.getPlayer(uuid);
    }

    @GetMapping("/{uuid}/profiles")
    public List<SkyBlockProfileSummary> getProfiles(@PathVariable String uuid) {
        return hypixelClient.getSkyBlockProfiles(uuid);
    }

    @GetMapping("/{uuid}/profiles/{profileId}/progress")
    public SkyBlockProfileProgress getProfileProgress(
            @PathVariable String uuid,
            @PathVariable String profileId) {
        return hypixelClient.getSkyBlockProfileProgress(uuid, profileId);
    }
}
