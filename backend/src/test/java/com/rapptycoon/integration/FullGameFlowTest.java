package com.rapptycoon.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full game flow integration test that exercises the complete game lifecycle end-to-end.
 * Uses a real Spring context with H2 in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FullGameFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Full game lifecycle: create → join → start → basestations → catalogue → deploy → leaderboard → verify state")
    void fullGameFlow() throws Exception {
        // 1. Create session → verify 201
        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostName\":\"Player1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionCode").exists())
                .andExpect(jsonPath("$.state").value("LOBBY"))
                .andExpect(jsonPath("$.hostPlayer.displayName").value("Player1"))
                .andExpect(jsonPath("$.hostPlayer.sessionToken").exists())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String sessionCode = createResponse.get("sessionCode").asText();
        String hostToken = createResponse.get("hostPlayer").get("sessionToken").asText();

        // 2. Join with second player → verify 200
        MvcResult joinResult = mockMvc.perform(post("/api/sessions/" + sessionCode + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Player2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.displayName").value("Player2"))
                .andExpect(jsonPath("$.player.sessionToken").exists())
                .andExpect(jsonPath("$.session.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.session.players").isArray())
                .andReturn();

        JsonNode joinResponse = objectMapper.readTree(joinResult.getResponse().getContentAsString());
        String player2Token = joinResponse.get("player").get("sessionToken").asText();

        // 3. Start game → verify ACTIVE state, basestations assigned
        MvcResult startResult = mockMvc.perform(post("/api/sessions/" + sessionCode + "/start")
                        .header("X-Session-Token", hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.players").isArray())
                .andReturn();

        JsonNode startResponse = objectMapper.readTree(startResult.getResponse().getContentAsString());
        assertThat(startResponse.get("players").size()).isEqualTo(2);

        // 4. Get basestations → verify 3 per player with default metrics
        MvcResult bsResult = mockMvc.perform(get("/api/sessions/" + sessionCode + "/basestations")
                        .header("X-Session-Token", hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basestations").isArray())
                .andReturn();

        JsonNode bsResponse = objectMapper.readTree(bsResult.getResponse().getContentAsString());
        JsonNode basestations = bsResponse.get("basestations");
        assertThat(basestations.size()).isEqualTo(3);

        // Verify default metrics on first basestation
        JsonNode firstBs = basestations.get(0);
        assertThat(firstBs.get("metrics").get("health").decimalValue())
                .isEqualByComparingTo("100.00");
        assertThat(firstBs.get("metrics").get("customerExperience").decimalValue())
                .isEqualByComparingTo("100.00");
        assertThat(firstBs.get("metrics").get("energyEfficiency").decimalValue())
                .isEqualByComparingTo("100.00");

        Long firstBasestationId = firstBs.get("id").asLong();

        // Also verify player 2 has 3 basestations
        MvcResult bs2Result = mockMvc.perform(get("/api/sessions/" + sessionCode + "/basestations")
                        .header("X-Session-Token", player2Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode bs2Response = objectMapper.readTree(bs2Result.getResponse().getContentAsString());
        assertThat(bs2Response.get("basestations").size()).isEqualTo(3);

        // 5. Get catalogue → verify 7 rApps
        MvcResult catalogueResult = mockMvc.perform(get("/api/rapps/catalogue")
                        .header("X-Session-Token", hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rapps").isArray())
                .andReturn();

        JsonNode catalogueResponse = objectMapper.readTree(catalogueResult.getResponse().getContentAsString());
        assertThat(catalogueResponse.get("rapps").size()).isEqualTo(7);

        // Get the first template ID (cheapest rApp for deployment)
        Long templateId = catalogueResponse.get("rapps").get(0).get("id").asLong();

        // 6. Deploy rApp → verify DEPLOYING status, money deducted
        String deployBody = String.format("{\"templateId\":%d,\"basestationId\":%d}", templateId, firstBasestationId);
        MvcResult deployResult = mockMvc.perform(post("/api/sessions/" + sessionCode + "/rapps/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-Token", hostToken)
                        .content(deployBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DEPLOYING"))
                .andExpect(jsonPath("$.templateId").value(templateId))
                .andExpect(jsonPath("$.basestationId").value(firstBasestationId))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();

        JsonNode deployResponse = objectMapper.readTree(deployResult.getResponse().getContentAsString());
        assertThat(deployResponse.get("id").asLong()).isGreaterThan(0);

        // 7. Get leaderboard → verify both players listed
        MvcResult leaderboardResult = mockMvc.perform(get("/api/sessions/" + sessionCode + "/leaderboard")
                        .header("X-Session-Token", hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard").isArray())
                .andExpect(jsonPath("$.gameState").value("ACTIVE"))
                .andReturn();

        JsonNode leaderboardResponse = objectMapper.readTree(leaderboardResult.getResponse().getContentAsString());
        assertThat(leaderboardResponse.get("leaderboard").size()).isEqualTo(2);

        // Verify both players are in the leaderboard
        JsonNode leaderboard = leaderboardResponse.get("leaderboard");
        boolean foundPlayer1 = false;
        boolean foundPlayer2 = false;
        for (int i = 0; i < leaderboard.size(); i++) {
            String name = leaderboard.get(i).get("displayName").asText();
            if ("Player1".equals(name)) foundPlayer1 = true;
            if ("Player2".equals(name)) foundPlayer2 = true;
        }
        assertThat(foundPlayer1).isTrue();
        assertThat(foundPlayer2).isTrue();

        // 8. Verify session state is ACTIVE
        mockMvc.perform(get("/api/sessions/" + sessionCode)
                        .header("X-Session-Token", hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.sessionCode").value(sessionCode))
                .andExpect(jsonPath("$.players").isArray());
    }
}
