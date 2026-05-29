package com.rapptycoon.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security integration tests that verify access control across all endpoints.
 * Uses a full Spring Boot context with H2 in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String sessionCode;
    private String hostToken;
    private String playerToken;

    @BeforeEach
    void setUp() throws Exception {
        // Create a session (public endpoint)
        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostName\":\"TestHost\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        sessionCode = createResponse.get("sessionCode").asText();
        hostToken = createResponse.get("hostPlayer").get("sessionToken").asText();

        // Join with a second player (public endpoint)
        MvcResult joinResult = mockMvc.perform(post("/api/sessions/" + sessionCode + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"TestPlayer\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode joinResponse = objectMapper.readTree(joinResult.getResponse().getContentAsString());
        playerToken = joinResponse.get("player").get("sessionToken").asText();
    }

    @Nested
    @DisplayName("Public endpoints (no token required)")
    class PublicEndpoints {

        @Test
        @DisplayName("POST /api/sessions works without token")
        void createSession_noToken_succeeds() throws Exception {
            mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hostName\":\"AnotherHost\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sessionCode").exists())
                    .andExpect(jsonPath("$.hostPlayer.sessionToken").exists());
        }

        @Test
        @DisplayName("POST /api/sessions/{code}/join works without token")
        void joinSession_noToken_succeeds() throws Exception {
            mockMvc.perform(post("/api/sessions/" + sessionCode + "/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Player3\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.player.sessionToken").exists());
        }
    }

    @Nested
    @DisplayName("Protected endpoints return 401 without token")
    class MissingTokenEndpoints {

        @Test
        @DisplayName("GET /api/sessions/{code} returns 401 without token")
        void getSession_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/sessions/" + sessionCode))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("GET /api/sessions/{code}/basestations returns 401 without token")
        void getBasestations_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/sessions/" + sessionCode + "/basestations"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("GET /api/sessions/{code}/leaderboard returns 401 without token")
        void getLeaderboard_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/sessions/" + sessionCode + "/leaderboard"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("POST /api/sessions/{code}/rapps/deploy returns 401 without token")
        void deploy_noToken_returns401() throws Exception {
            mockMvc.perform(post("/api/sessions/" + sessionCode + "/rapps/deploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"templateId\":1,\"basestationId\":1}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("PUT /api/sessions/{code}/rapps/{id}/tune returns 401 without token")
        void tune_noToken_returns401() throws Exception {
            mockMvc.perform(put("/api/sessions/" + sessionCode + "/rapps/1/tune")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("PUT /api/sessions/{code}/rapps/{id}/disable returns 401 without token")
        void disable_noToken_returns401() throws Exception {
            mockMvc.perform(put("/api/sessions/" + sessionCode + "/rapps/1/disable"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("PUT /api/sessions/{code}/rapps/{id}/rollback returns 401 without token")
        void rollback_noToken_returns401() throws Exception {
            mockMvc.perform(put("/api/sessions/" + sessionCode + "/rapps/1/rollback"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("GET /api/rapps/catalogue returns 401 without token")
        void catalogue_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/rapps/catalogue"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("POST /api/sessions/{code}/start returns 400 without token (required header)")
        void startSession_noToken_returns400() throws Exception {
            // This endpoint uses @RequestHeader without required=false,
            // so Spring returns 400 for missing header (still protected)
            mockMvc.perform(post("/api/sessions/" + sessionCode + "/start"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Invalid token returns 401")
    class InvalidTokenEndpoints {

        @Test
        @DisplayName("GET /api/sessions/{code} returns 401 with invalid token")
        void getSession_invalidToken_returns401() throws Exception {
            mockMvc.perform(get("/api/sessions/" + sessionCode)
                            .header("X-Session-Token", "invalid-token-that-does-not-exist"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("GET /api/rapps/catalogue returns 401 with invalid token")
        void catalogue_invalidToken_returns401() throws Exception {
            mockMvc.perform(get("/api/rapps/catalogue")
                            .header("X-Session-Token", "invalid-token-that-does-not-exist"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("POST /api/sessions/{code}/rapps/deploy returns 401 with invalid token")
        void deploy_invalidToken_returns401() throws Exception {
            mockMvc.perform(post("/api/sessions/" + sessionCode + "/rapps/deploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-Token", "invalid-token-that-does-not-exist")
                            .content("{\"templateId\":1,\"basestationId\":1}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("Cross-session access returns 403")
    class CrossSessionAccess {

        private String otherSessionCode;
        private String otherPlayerToken;

        @BeforeEach
        void setUpOtherSession() throws Exception {
            // Create a separate session
            MvcResult createResult = mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hostName\":\"OtherHost\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
            otherSessionCode = createResponse.get("sessionCode").asText();
            otherPlayerToken = createResponse.get("hostPlayer").get("sessionToken").asText();
        }

        @Test
        @DisplayName("GET /api/sessions/{code} returns 403 for non-member")
        void getSession_nonMember_returns403() throws Exception {
            // Use otherPlayerToken to access the first session
            mockMvc.perform(get("/api/sessions/" + sessionCode)
                            .header("X-Session-Token", otherPlayerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("GET /api/sessions/{code}/basestations returns 403 for non-member")
        void getBasestations_nonMember_returns403() throws Exception {
            mockMvc.perform(get("/api/sessions/" + sessionCode + "/basestations")
                            .header("X-Session-Token", otherPlayerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("GET /api/sessions/{code}/leaderboard returns 403 for non-member")
        void getLeaderboard_nonMember_returns403() throws Exception {
            mockMvc.perform(get("/api/sessions/" + sessionCode + "/leaderboard")
                            .header("X-Session-Token", otherPlayerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("Internal API key validation")
    class InternalApiKeyValidation {

        @Test
        @DisplayName("POST /api/internal/sessions/{code}/events returns 401 without API key")
        void pushEvent_noKey_returns401() throws Exception {
            mockMvc.perform(post("/api/internal/sessions/" + sessionCode + "/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"basestationId\":1,\"eventType\":\"POWER_OUTAGE\",\"severity\":\"HIGH\",\"description\":\"Test\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("GET /api/internal/sessions/active returns 401 without API key")
        void getActiveSessions_noKey_returns401() throws Exception {
            mockMvc.perform(get("/api/internal/sessions/active"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("POST /api/internal/sessions/{code}/events returns 401 with invalid API key")
        void pushEvent_invalidKey_returns401() throws Exception {
            mockMvc.perform(post("/api/internal/sessions/" + sessionCode + "/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Key", "wrong-key")
                            .content("{\"basestationId\":1,\"eventType\":\"POWER_OUTAGE\",\"severity\":\"HIGH\",\"description\":\"Test\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("GET /api/internal/sessions/active returns 401 with invalid API key")
        void getActiveSessions_invalidKey_returns401() throws Exception {
            mockMvc.perform(get("/api/internal/sessions/active")
                            .header("X-Internal-Key", "wrong-key"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("GET /api/internal/sessions/active succeeds with valid API key")
        void getActiveSessions_validKey_succeeds() throws Exception {
            mockMvc.perform(get("/api/internal/sessions/active")
                            .header("X-Internal-Key", "test-internal-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions").isArray());
        }
    }

    @Nested
    @DisplayName("Valid token access succeeds")
    class ValidTokenAccess {

        @Test
        @DisplayName("GET /api/sessions/{code} succeeds with valid member token")
        void getSession_validToken_succeeds() throws Exception {
            mockMvc.perform(get("/api/sessions/" + sessionCode)
                            .header("X-Session-Token", hostToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionCode").value(sessionCode));
        }

        @Test
        @DisplayName("GET /api/rapps/catalogue succeeds with valid token")
        void catalogue_validToken_succeeds() throws Exception {
            mockMvc.perform(get("/api/rapps/catalogue")
                            .header("X-Session-Token", hostToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rapps").isArray());
        }

        @Test
        @DisplayName("GET /api/sessions/{code}/leaderboard succeeds with valid member token")
        void leaderboard_validToken_succeeds() throws Exception {
            mockMvc.perform(get("/api/sessions/" + sessionCode + "/leaderboard")
                            .header("X-Session-Token", hostToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.leaderboard").isArray());
        }
    }

    @Nested
    @DisplayName("No direct score/metrics modification endpoints exist")
    class NoDirectModification {

        @Test
        @DisplayName("PUT /api/sessions/{code}/score does not exist (404)")
        void putScore_returns404() throws Exception {
            mockMvc.perform(put("/api/sessions/" + sessionCode + "/score")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-Token", hostToken)
                            .content("{\"money\":9999}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST /api/sessions/{code}/metrics does not exist (404)")
        void postMetrics_returns404() throws Exception {
            mockMvc.perform(post("/api/sessions/" + sessionCode + "/metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-Token", hostToken)
                            .content("{\"health\":100}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("PUT /api/sessions/{code}/basestations/1/metrics does not exist (404)")
        void putBasestationMetrics_returns404() throws Exception {
            mockMvc.perform(put("/api/sessions/" + sessionCode + "/basestations/1/metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-Token", hostToken)
                            .content("{\"health\":100,\"customerExperience\":100}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Input validation on request DTOs")
    class InputValidation {

        @Test
        @DisplayName("POST /api/sessions rejects blank hostName")
        void createSession_blankHostName_returns400() throws Exception {
            mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hostName\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("POST /api/sessions rejects hostName exceeding 50 chars")
        void createSession_longHostName_returns400() throws Exception {
            String longName = "A".repeat(51);
            mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hostName\":\"" + longName + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("POST /api/sessions/{code}/join rejects blank displayName")
        void joinSession_blankName_returns400() throws Exception {
            mockMvc.perform(post("/api/sessions/" + sessionCode + "/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("POST /api/sessions/{code}/rapps/deploy rejects null templateId")
        void deploy_nullTemplateId_returns400() throws Exception {
            mockMvc.perform(post("/api/sessions/" + sessionCode + "/rapps/deploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-Token", hostToken)
                            .content("{\"basestationId\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("PUT /api/sessions/{code}/rapps/{id}/tune rejects threshold out of range")
        void tune_thresholdOutOfRange_returns400() throws Exception {
            mockMvc.perform(put("/api/sessions/" + sessionCode + "/rapps/1/tune")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-Token", hostToken)
                            .content("{\"threshold\":101,\"aggressiveness\":\"MODERATE\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("PUT /api/sessions/{code}/rapps/{id}/tune rejects blank aggressiveness")
        void tune_blankAggressiveness_returns400() throws Exception {
            mockMvc.perform(put("/api/sessions/" + sessionCode + "/rapps/1/tune")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-Token", hostToken)
                            .content("{\"threshold\":50,\"aggressiveness\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }
    }
}
