package com.rapptycoon.controller;

import com.rapptycoon.dto.LeaderboardEntryDto;
import com.rapptycoon.dto.LeaderboardResponse;
import com.rapptycoon.dto.ScoreDto;
import com.rapptycoon.exception.ForbiddenException;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.service.PlayerService;
import com.rapptycoon.service.ScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaderboardController.class)
class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScoreService scoreService;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private GameSessionRepository gameSessionRepository;

    private static final String VALID_TOKEN = "valid-token-123";
    private static final String SESSION_CODE = "ABCD1234";

    @Test
    @DisplayName("Returns 200 with leaderboard data")
    void getLeaderboard_returns200WithData() throws Exception {
        Player player = Player.builder()
                .id(1L).sessionId(1L).displayName("Player1").sessionToken(VALID_TOKEN).build();

        GameSession session = GameSession.builder()
                .id(1L).sessionCode(SESSION_CODE).state(GameSessionState.ACTIVE).build();

        LeaderboardResponse response = new LeaderboardResponse(
                List.of(
                        new LeaderboardEntryDto(1, 1L, "Player1",
                                new ScoreDto(new BigDecimal("900.00"), new BigDecimal("91.50"), new BigDecimal("88.00")),
                                new BigDecimal("333.45")),
                        new LeaderboardEntryDto(2, 2L, "Player2",
                                new ScoreDto(new BigDecimal("720.00"), new BigDecimal("85.00"), new BigDecimal("82.50")),
                                new BigDecimal("274.63"))
                ),
                "ACTIVE"
        );

        when(playerService.validateToken(VALID_TOKEN)).thenReturn(player);
        when(gameSessionRepository.findBySessionCode(SESSION_CODE)).thenReturn(Optional.of(session));
        when(scoreService.getLeaderboard(SESSION_CODE)).thenReturn(response);

        mockMvc.perform(get("/api/sessions/{code}/leaderboard", SESSION_CODE)
                        .header("X-Session-Token", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard").isArray())
                .andExpect(jsonPath("$.leaderboard.length()").value(2))
                .andExpect(jsonPath("$.leaderboard[0].rank").value(1))
                .andExpect(jsonPath("$.leaderboard[0].playerId").value(1))
                .andExpect(jsonPath("$.leaderboard[0].displayName").value("Player1"))
                .andExpect(jsonPath("$.leaderboard[0].scores.money").value(900.00))
                .andExpect(jsonPath("$.leaderboard[0].scores.customerSatisfaction").value(91.50))
                .andExpect(jsonPath("$.leaderboard[0].scores.networkStability").value(88.00))
                .andExpect(jsonPath("$.leaderboard[0].compositeScore").value(333.45))
                .andExpect(jsonPath("$.leaderboard[1].rank").value(2))
                .andExpect(jsonPath("$.gameState").value("ACTIVE"));
    }

    @Test
    @DisplayName("Returns 401 when token missing")
    void getLeaderboard_returns401WhenTokenMissing() throws Exception {
        mockMvc.perform(get("/api/sessions/{code}/leaderboard", SESSION_CODE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Returns 403 when player not in session")
    void getLeaderboard_returns403WhenPlayerNotInSession() throws Exception {
        Player player = Player.builder()
                .id(1L).sessionId(99L).displayName("Player1").sessionToken(VALID_TOKEN).build();

        GameSession session = GameSession.builder()
                .id(1L).sessionCode(SESSION_CODE).state(GameSessionState.ACTIVE).build();

        when(playerService.validateToken(VALID_TOKEN)).thenReturn(player);
        when(gameSessionRepository.findBySessionCode(SESSION_CODE)).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/sessions/{code}/leaderboard", SESSION_CODE)
                        .header("X-Session-Token", VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
