package com.rapptycoon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapptycoon.dto.*;
import com.rapptycoon.exception.*;
import com.rapptycoon.service.GameSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameSessionController.class)
class GameSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GameSessionService gameSessionService;

    @Nested
    @DisplayName("POST /api/sessions")
    class CreateSession {

        @Test
        @DisplayName("returns 201 with valid request")
        void returns201WithValidRequest() throws Exception {
            CreateSessionResponse response = new CreateSessionResponse(
                    "ABCD1234",
                    1L,
                    new PlayerDto(1L, "HostPlayer", "token123abc", true, true),
                    "LOBBY",
                    6,
                    LocalDateTime.now()
            );

            when(gameSessionService.createSession("HostPlayer")).thenReturn(response);

            mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hostName\": \"HostPlayer\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sessionCode").value("ABCD1234"))
                    .andExpect(jsonPath("$.state").value("LOBBY"))
                    .andExpect(jsonPath("$.hostPlayer.displayName").value("HostPlayer"));
        }

        @Test
        @DisplayName("returns 400 when hostName is blank")
        void returns400WhenHostNameBlank() throws Exception {
            mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hostName\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when hostName exceeds 50 chars")
        void returns400WhenHostNameTooLong() throws Exception {
            String longName = "A".repeat(51);

            mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hostName\": \"" + longName + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/sessions/{code}/join")
    class JoinSession {

        @Test
        @DisplayName("returns 200 with valid request")
        void returns200WithValidRequest() throws Exception {
            PlayerDto playerDto = new PlayerDto(2L, "NewPlayer", "newtoken123", false, true);
            SessionResponse sessionResponse = new SessionResponse(
                    "ABCD1234", "LOBBY", 6, LocalDateTime.now(), null, null,
                    List.of(
                            new PlayerDto(1L, "Host", null, true, true),
                            new PlayerDto(2L, "NewPlayer", null, false, true)
                    )
            );
            JoinResponse joinResponse = new JoinResponse(playerDto, sessionResponse);

            when(gameSessionService.joinSession("ABCD1234", "NewPlayer")).thenReturn(joinResponse);

            mockMvc.perform(post("/api/sessions/ABCD1234/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"NewPlayer\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.player.displayName").value("NewPlayer"))
                    .andExpect(jsonPath("$.session.sessionCode").value("ABCD1234"));
        }

        @Test
        @DisplayName("returns 400 when displayName is blank")
        void returns400WhenDisplayNameBlank() throws Exception {
            mockMvc.perform(post("/api/sessions/ABCD1234/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 404 when session not found")
        void returns404WhenSessionNotFound() throws Exception {
            when(gameSessionService.joinSession("INVALID1", "Player"))
                    .thenThrow(new SessionNotFoundException("INVALID1"));

            mockMvc.perform(post("/api/sessions/INVALID1/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"Player\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
        }

        @Test
        @DisplayName("returns 409 when session is full")
        void returns409WhenSessionFull() throws Exception {
            when(gameSessionService.joinSession("ABCD1234", "Player"))
                    .thenThrow(new SessionFullException("ABCD1234"));

            mockMvc.perform(post("/api/sessions/ABCD1234/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"Player\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SESSION_FULL"));
        }

        @Test
        @DisplayName("returns 409 when session not in LOBBY")
        void returns409WhenSessionNotInLobby() throws Exception {
            when(gameSessionService.joinSession("ABCD1234", "Player"))
                    .thenThrow(new InvalidStateException("Session is not in LOBBY state"));

            mockMvc.perform(post("/api/sessions/ABCD1234/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"Player\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("INVALID_STATE"));
        }
    }

    @Nested
    @DisplayName("POST /api/sessions/{code}/start")
    class StartSession {

        @Test
        @DisplayName("returns 200 with valid token")
        void returns200WithValidToken() throws Exception {
            SessionResponse sessionResponse = new SessionResponse(
                    "ABCD1234", "ACTIVE", 6, LocalDateTime.now(), LocalDateTime.now(), null,
                    List.of(
                            new PlayerDto(1L, "Host", null, true, true),
                            new PlayerDto(2L, "Player2", null, false, true)
                    )
            );

            when(gameSessionService.startSession("ABCD1234", "validtoken123")).thenReturn(sessionResponse);

            mockMvc.perform(post("/api/sessions/ABCD1234/start")
                            .header("X-Session-Token", "validtoken123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("ACTIVE"))
                    .andExpect(jsonPath("$.sessionCode").value("ABCD1234"));
        }

        @Test
        @DisplayName("returns 401 when X-Session-Token header is missing")
        void returns401WhenTokenMissing() throws Exception {
            mockMvc.perform(post("/api/sessions/ABCD1234/start"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 403 when non-host tries to start")
        void returns403WhenNonHostTriesToStart() throws Exception {
            when(gameSessionService.startSession("ABCD1234", "nonhosttoken"))
                    .thenThrow(new ForbiddenException("Only the host can start the session"));

            mockMvc.perform(post("/api/sessions/ABCD1234/start")
                            .header("X-Session-Token", "nonhosttoken"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("returns 409 when fewer than 2 players")
        void returns409WhenFewerThan2Players() throws Exception {
            when(gameSessionService.startSession("ABCD1234", "hosttoken"))
                    .thenThrow(new InvalidStateException("Not enough players to start"));

            mockMvc.perform(post("/api/sessions/ABCD1234/start")
                            .header("X-Session-Token", "hosttoken"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("INVALID_STATE"));
        }
    }

    @Nested
    @DisplayName("GET /api/sessions/{code}")
    class GetSession {

        @Test
        @DisplayName("returns 200 with valid token")
        void returns200WithValidToken() throws Exception {
            SessionResponse sessionResponse = new SessionResponse(
                    "ABCD1234", "LOBBY", 6, LocalDateTime.now(), null, null,
                    List.of(new PlayerDto(1L, "Host", null, true, true))
            );

            when(gameSessionService.getSession("ABCD1234")).thenReturn(sessionResponse);

            mockMvc.perform(get("/api/sessions/ABCD1234")
                            .header("X-Session-Token", "validtoken123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionCode").value("ABCD1234"))
                    .andExpect(jsonPath("$.state").value("LOBBY"))
                    .andExpect(jsonPath("$.players").isArray());
        }

        @Test
        @DisplayName("returns 401 when X-Session-Token header is missing")
        void returns401WhenTokenMissing() throws Exception {
            mockMvc.perform(get("/api/sessions/ABCD1234"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 404 when session not found")
        void returns404WhenSessionNotFound() throws Exception {
            when(gameSessionService.getSession("INVALID1"))
                    .thenThrow(new SessionNotFoundException("INVALID1"));

            mockMvc.perform(get("/api/sessions/INVALID1")
                            .header("X-Session-Token", "validtoken123"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
        }
    }
}
