package com.rapptycoon.controller;

import com.rapptycoon.dto.*;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.service.BasestationService;
import com.rapptycoon.service.PlayerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BasestationController.class)
class BasestationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BasestationService basestationService;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private GameSessionRepository gameSessionRepository;

    @Test
    @DisplayName("GET /api/sessions/{code}/basestations returns 200 with valid token")
    void returns200WithValidToken() throws Exception {
        Player player = Player.builder()
                .id(1L)
                .sessionId(1L)
                .displayName("TestPlayer")
                .sessionToken("valid-token-123")
                .connected(true)
                .build();

        MetricsDto metrics = new MetricsDto(
                new BigDecimal("85.00"),
                new BigDecimal("90.00"),
                new BigDecimal("50.00"),
                new BigDecimal("75.00"),
                new BigDecimal("95.00"),
                new BigDecimal("88.00")
        );

        DeployedRappDto rapp = new DeployedRappDto(
                10L, 3L, "ACTIVE", 1, LocalDateTime.of(2025, 1, 15, 10, 0)
        );

        ActiveEventDto event = new ActiveEventDto(
                20L, "POWER_OUTAGE", "HIGH", "Power failure", 1,
                LocalDateTime.of(2025, 1, 15, 10, 5)
        );

        BasestationStateDto bsState = new BasestationStateDto(
                1L, "BS-Alpha", 100, 200, metrics, List.of(rapp), List.of(event)
        );

        when(playerService.validateToken("valid-token-123")).thenReturn(player);
        when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(
                GameSession.builder().id(1L).sessionCode("ABCD1234").state(GameSessionState.ACTIVE).maxPlayers(6).build()
        ));
        when(basestationService.getPlayerBasestations(1L)).thenReturn(List.of(bsState));

        mockMvc.perform(get("/api/sessions/ABCD1234/basestations")
                        .header("X-Session-Token", "valid-token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basestations").isArray())
                .andExpect(jsonPath("$.basestations[0].id").value(1))
                .andExpect(jsonPath("$.basestations[0].name").value("BS-Alpha"))
                .andExpect(jsonPath("$.basestations[0].metrics.health").value(85.00))
                .andExpect(jsonPath("$.basestations[0].deployedRapps[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.basestations[0].activeEvents[0].eventType").value("POWER_OUTAGE"));
    }

    @Test
    @DisplayName("GET /api/sessions/{code}/basestations returns 401 when token header missing")
    void returns401WhenTokenMissing() throws Exception {
        mockMvc.perform(get("/api/sessions/ABCD1234/basestations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/sessions/{code}/basestations returns 401 when token invalid")
    void returns401WhenTokenInvalid() throws Exception {
        when(playerService.validateToken("bad-token"))
                .thenThrow(new UnauthorizedException("Invalid session token"));

        mockMvc.perform(get("/api/sessions/ABCD1234/basestations")
                        .header("X-Session-Token", "bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
