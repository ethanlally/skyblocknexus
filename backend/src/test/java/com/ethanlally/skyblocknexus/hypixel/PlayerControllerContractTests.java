package com.ethanlally.skyblocknexus.hypixel;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ethanlally.skyblocknexus.player.PlayerController;
import com.ethanlally.skyblocknexus.http.UpstreamErrorHandler;
import com.ethanlally.skyblocknexus.http.UpstreamResponseException;
import com.ethanlally.skyblocknexus.player.PlayerSummary;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockCollectionProgress;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockCurrencySummary;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockEquipmentItem;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockProfileProgress;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockProfileSummary;
import com.ethanlally.skyblocknexus.skyblock.SkyBlockSkillProgress;
import java.util.List;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class PlayerControllerContractTests {

    @Mock
    private HypixelClient hypixelClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlayerController(hypixelClient))
                .setControllerAdvice(
                        new HypixelNotFoundHandler(),
                        new HypixelRateLimitHandler(),
                        new UpstreamErrorHandler())
                .build();
    }

    @Test
    void returnsThePlayerContract() throws Exception {
        when(hypixelClient.getPlayer("player-uuid"))
                .thenReturn(new PlayerSummary("player-uuid", "ExamplePlayer", 1000L, null));

        mockMvc.perform(get("/api/players/player-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value("player-uuid"))
                .andExpect(jsonPath("$.displayName").value("ExamplePlayer"))
                .andExpect(jsonPath("$.firstLogin").value(1000))
                .andExpect(jsonPath("$.lastLogin").isEmpty());
    }

    @Test
    void returnsTheProfileListContract() throws Exception {
        when(hypixelClient.getSkyBlockProfiles("player-uuid"))
                .thenReturn(List.of(
                        new SkyBlockProfileSummary(
                                "profile-id", "Apple", true, "ironman")));

        mockMvc.perform(get("/api/players/player-uuid/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profileId").value("profile-id"))
                .andExpect(jsonPath("$[0].name").value("Apple"))
                .andExpect(jsonPath("$[0].selected").value(true))
                .andExpect(jsonPath("$[0].gameMode").value("ironman"));
    }

    @Test
    void returnsTheProfileProgressContract() throws Exception {
        when(hypixelClient.getSkyBlockProfileProgress("player-uuid", "profile-id"))
                .thenReturn(new SkyBlockProfileProgress(
                        "profile-id",
                        new SkyBlockCurrencySummary(123.5, 456.0, null),
                        List.of(new SkyBlockEquipmentItem(
                                "Armor", "TEST_HELMET", "Test Helmet")),
                        List.of(new SkyBlockSkillProgress(
                                "Mining", 10, 5000, 200, 1000.0)),
                        List.of(new SkyBlockCollectionProgress(
                                "COBBLESTONE", "Cobblestone", 2500, 4, 500, 2500L))));

        mockMvc.perform(get(
                        "/api/players/player-uuid/profiles/profile-id/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value("profile-id"))
                .andExpect(jsonPath("$.currencies.coinPurse").value(123.5))
                .andExpect(jsonPath("$.currencies.bankBalance").value(456.0))
                .andExpect(jsonPath("$.currencies.motesPurse").isEmpty())
                .andExpect(jsonPath("$.equipment[0].itemId").value("TEST_HELMET"))
                .andExpect(jsonPath("$.skills[0].name").value("Mining"))
                .andExpect(jsonPath("$.collections[0].itemId").value("COBBLESTONE"));
    }

    @Test
    void returnsTheNotFoundErrorContract() throws Exception {
        when(hypixelClient.getSkyBlockProfileProgress("player-uuid", "missing-profile"))
                .thenThrow(new HypixelDataNotFoundException(
                        "SkyBlock profile was not found"));

        mockMvc.perform(get(
                        "/api/players/player-uuid/profiles/missing-profile/progress"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("SkyBlock profile was not found"));
    }

    @Test
    void returnsTheRateLimitErrorContract() throws Exception {
        when(hypixelClient.getSkyBlockProfiles("player-uuid"))
                .thenThrow(new HypixelRateLimitException(17));

        mockMvc.perform(get("/api/players/player-uuid/profiles"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "17"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(17))
                .andExpect(jsonPath("$.message").value(
                        "Hypixel's request limit has been reached. Try again in 17 seconds."));
    }

    @Test
    void upstreamFailuresReturnBadGatewayInsteadOfAnEmptyProfileList() throws Exception {
        when(hypixelClient.getSkyBlockProfiles("player-uuid"))
                .thenThrow(new UpstreamResponseException("Hypixel"));

        mockMvc.perform(get("/api/players/player-uuid/profiles"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "Hypixel returned an unsuccessful or malformed response. Please try again later."));
    }

    @Test
    void upstreamTimeoutsReturnGatewayTimeout() throws Exception {
        when(hypixelClient.getSkyBlockProfiles("player-uuid"))
                .thenThrow(new ResourceAccessException("Read failed", new SocketTimeoutException()));

        mockMvc.perform(get("/api/players/player-uuid/profiles"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").value(
                        "The upstream service took too long to respond. Please try again."));
    }
}
