package com.rapptycoon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapptycoon.dto.ActiveSessionDto;
import com.rapptycoon.dto.ActiveSessionsResponse;
import com.rapptycoon.dto.CreateEventRequest;
import com.rapptycoon.dto.ImpactDto;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalEventController.class)
@TestPropertySource(properties = "internal.api-key=test-internal-key")
class InternalEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private GameSessionRepository gameSessionRepository;

    @MockitoBean
    private PlayerRepository playerRepository;

    @MockitoBean
    private BasestationRepository basestationRepository;

    private static final String VALID_KEY = "test-internal-key";

    @Test
    @DisplayName("POST events returns 201 with valid internal key")
    void postEvent_returns201WithValidKey() throws Exception {
        GameEvent createdEvent = GameEvent.builder()
                .id(5L)
                .sessionId(1L)
                .basestationId(1L)
                .eventType("POWER_OUTAGE")
                .severity(EventSeverity.HIGH)
                .description("Power failure")
                .resolved(false)
                .escalationLevel(0)
                .createdAt(LocalDateTime.of(2025, 1, 15, 10, 10))
                .build();

        when(eventService.createEvent(eq("ABCD1234"), eq(1L), eq("POWER_OUTAGE"), eq("HIGH"),
                eq("Power failure"), any()))
                .thenReturn(createdEvent);

        CreateEventRequest request = new CreateEventRequest(
                1L, "POWER_OUTAGE", "HIGH", "Power failure",
                new ImpactDto(
                        new BigDecimal("-15.00"), new BigDecimal("-10.00"),
                        new BigDecimal("25.00"), new BigDecimal("-20.00"),
                        new BigDecimal("-5.00"), new BigDecimal("-12.00")
                )
        );

        mockMvc.perform(post("/api/internal/sessions/ABCD1234/events")
                        .header("X-Internal-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(5))
                .andExpect(jsonPath("$.sessionCode").value("ABCD1234"))
                .andExpect(jsonPath("$.basestationId").value(1))
                .andExpect(jsonPath("$.eventType").value("POWER_OUTAGE"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.escalationLevel").value(0))
                .andExpect(jsonPath("$.resolved").value(false));
    }

    @Test
    @DisplayName("POST events returns 401 without key")
    void postEvent_returns401WithoutKey() throws Exception {
        CreateEventRequest request = new CreateEventRequest(
                1L, "POWER_OUTAGE", "HIGH", "Power failure", null
        );

        mockMvc.perform(post("/api/internal/sessions/ABCD1234/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST events returns 401 with wrong key")
    void postEvent_returns401WithWrongKey() throws Exception {
        CreateEventRequest request = new CreateEventRequest(
                1L, "POWER_OUTAGE", "HIGH", "Power failure", null
        );

        mockMvc.perform(post("/api/internal/sessions/ABCD1234/events")
                        .header("X-Internal-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET active sessions returns 200 with list")
    void getActiveSessions_returns200WithList() throws Exception {
        GameSession session = GameSession.builder()
                .id(1L)
                .sessionCode("ABCD1234")
                .state(GameSessionState.ACTIVE)
                .startedAt(LocalDateTime.of(2025, 1, 15, 10, 5))
                .build();

        Player player1 = Player.builder().id(1L).sessionId(1L).displayName("Player1").sessionToken("t1").build();
        Player player2 = Player.builder().id(2L).sessionId(1L).displayName("Player2").sessionToken("t2").build();

        Basestation bs1 = Basestation.builder().id(1L).playerId(1L).name("BS-Alpha").build();
        Basestation bs2 = Basestation.builder().id(2L).playerId(1L).name("BS-Beta").build();
        Basestation bs3 = Basestation.builder().id(3L).playerId(2L).name("BS-Gamma").build();

        when(gameSessionRepository.findByState(GameSessionState.ACTIVE)).thenReturn(List.of(session));
        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player1, player2));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs1, bs2));
        when(basestationRepository.findByPlayerId(2L)).thenReturn(List.of(bs3));

        mockMvc.perform(get("/api/internal/sessions/active")
                        .header("X-Internal-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.sessions[0].sessionCode").value("ABCD1234"))
                .andExpect(jsonPath("$.sessions[0].playerCount").value(2))
                .andExpect(jsonPath("$.sessions[0].basestationIds").isArray());
    }

    @Test
    @DisplayName("GET active sessions returns 401 without key")
    void getActiveSessions_returns401WithoutKey() throws Exception {
        mockMvc.perform(get("/api/internal/sessions/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
