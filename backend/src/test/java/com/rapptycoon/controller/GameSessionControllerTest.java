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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        @DisplayName("returns 201 CREATED with session response")
        void returnsCreatedWithSessionResponse() throws Exception {
            PlayerDto hostPlayer = new PlayerDto(1L, "HostPlayer", "token123abc", true, true, false);
            CreateSessionResponse response = new CreateSessionResponse(
                    "ABCD1234", 1L, hostPlayer, "LOBBY", 6, LocalDateTime.now());

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
        void returnsBadRequestWhenHostNameBlank() throws Exception {
            mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hostName\": \"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("returns 400 when hostName is missing")
        void returnsBadRequestWhenHostNameMissing() throws Exception {
            mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when hostName exceeds 50 characters")
        void returnsBadRequestWhenHostNameTooLong() throws Exception {
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
        @DisplayName("returns 200 with join response")
        void returnsOkWithJoinResponse() throws Exception {
            PlayerDto player = new PlayerDto(2L, "NewPlayer", "newtoken456", false, true, false);
            SessionResponse session = new SessionResponse("ABCD1234", "LOBBY", 6,
                    LocalDateTime.now(), null, null, List.of(), 0, 60);
            JoinResponse response = new JoinResponse(player, session);

            when(gameSessionService.joinSession("ABCD1234", "NewPlayer")).thenReturn(response);

            mockMvc.perform(post("/api/sessions/ABCD1234/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"NewPlayer\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.player.displayName").value("NewPlayer"))
                    .andExpect(jsonPath("$.session.sessionCode").value("ABCD1234"));
        }

        @Test
        @DisplayName("returns 404 when session not found")
        void returnsNotFoundWhenSessionMissing() throws Exception {
            when(gameSessionService.joinSession(eq("INVALID1"), anyString()))
                    .thenThrow(new SessionNotFoundException("INVALID1"));

            mockMvc.perform(post("/api/sessions/INVALID1/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"Player\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
        }

        @Test
        @DisplayName("returns 409 when session is not in LOBBY state")
        void returnsConflictWhenSessionActive() throws Exception {
            when(gameSessionService.joinSession(eq("ABCD1234"), anyString()))
                    .thenThrow(new InvalidStateException("Session is not in LOBBY state"));

            mockMvc.perform(post("/api/sessions/ABCD1234/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"Player\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("INVALID_STATE"));
        }

        @Test
        @DisplayName("returns 409 when session is full")
        void returnsConflictWhenSessionFull() throws Exception {
            when(gameSessionService.joinSession(eq("ABCD1234"), anyString()))
                    .thenThrow(new SessionFullException("ABCD1234"));

            mockMvc.perform(post("/api/sessions/ABCD1234/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"Player\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SESSION_FULL"));
        }

        @Test
        @DisplayName("returns 400 when displayName is blank")
        void returnsBadRequestWhenDisplayNameBlank() throws Exception {
            mockMvc.perform(post("/api/sessions/ABCD1234/join")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/sessions/{code}/start")
    class StartSession {

        @Test
        @DisplayName("returns 200 with session in ACTIVE state")
        void returnsOkWithActiveSession() throws Exception {
            SessionResponse response = new SessionResponse("ABCD1234", "ACTIVE", 6,
                    LocalDateTime.now(), LocalDateTime.now(), null, List.of(), 0, 60);

            when(gameSessionService.startSession("ABCD1234", "hosttoken123", 5)).thenReturn(response);

            mockMvc.perform(post("/api/sessions/ABCD1234/start")
                            .header("X-Session-Token", "hosttoken123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"durationMinutes\": 5}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("ACTIVE"))
                    .andExpect(jsonPath("$.sessionCode").value("ABCD1234"));
        }

        @Test
        @DisplayName("defaults to 5 minutes when request body is null")
        void defaultsTo5MinutesWhenBodyNull() throws Exception {
            SessionResponse response = new SessionResponse("ABCD1234", "ACTIVE", 6,
                    LocalDateTime.now(), LocalDateTime.now(), null, List.of(), 0, 60);

            when(gameSessionService.startSession("ABCD1234", "hosttoken123", 5)).thenReturn(response);

            mockMvc.perform(post("/api/sessions/ABCD1234/start")
                            .header("X-Session-Token", "hosttoken123")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(gameSessionService).startSession("ABCD1234", "hosttoken123", 5);
        }

        @Test
        @DisplayName("returns 401 when token is invalid")
        void returnsUnauthorizedForInvalidToken() throws Exception {
            when(gameSessionService.startSession("ABCD1234", "badtoken", 5))
                    .thenThrow(new UnauthorizedException("Invalid session token"));

            mockMvc.perform(post("/api/sessions/ABCD1234/start")
                            .header("X-Session-Token", "badtoken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"durationMinutes\": 5}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("returns 403 when non-host tries to start")
        void returnsForbiddenForNonHost() throws Exception {
            when(gameSessionService.startSession("ABCD1234", "playertoken", 5))
                    .thenThrow(new ForbiddenException("Only host can start the session"));

            mockMvc.perform(post("/api/sessions/ABCD1234/start")
                            .header("X-Session-Token", "playertoken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"durationMinutes\": 5}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("returns 409 when session is not in LOBBY state")
        void returnsConflictWhenNotLobby() throws Exception {
            when(gameSessionService.startSession("ABCD1234", "hosttoken", 5))
                    .thenThrow(new InvalidStateException("Session is already active"));

            mockMvc.perform(post("/api/sessions/ABCD1234/start")
                            .header("X-Session-Token", "hosttoken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"durationMinutes\": 5}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("INVALID_STATE"));
        }
    }

    @Nested
    @DisplayName("GET /api/sessions/{code}")
    class GetSession {

        @Test
        @DisplayName("returns 200 with session details")
        void returnsOkWithSessionDetails() throws Exception {
            PlayerDto player = new PlayerDto(1L, "Host", "token1", true, true, false);
            SessionResponse response = new SessionResponse("ABCD1234", "LOBBY", 6,
                    LocalDateTime.now(), null, null, List.of(player), 0, 60);

            when(gameSessionService.getSession("ABCD1234", "token1")).thenReturn(response);

            mockMvc.perform(get("/api/sessions/ABCD1234")
                            .header("X-Session-Token", "token1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionCode").value("ABCD1234"))
                    .andExpect(jsonPath("$.players[0].displayName").value("Host"));
        }

        @Test
        @DisplayName("returns 401 when token header is missing")
        void returnsUnauthorizedWhenTokenMissing() throws Exception {
            mockMvc.perform(get("/api/sessions/ABCD1234"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("returns 401 when token header is blank")
        void returnsUnauthorizedWhenTokenBlank() throws Exception {
            mockMvc.perform(get("/api/sessions/ABCD1234")
                            .header("X-Session-Token", "   "))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("returns 404 when session not found")
        void returnsNotFoundWhenSessionMissing() throws Exception {
            when(gameSessionService.getSession("INVALID1", "token1"))
                    .thenThrow(new SessionNotFoundException("INVALID1"));

            mockMvc.perform(get("/api/sessions/INVALID1")
                            .header("X-Session-Token", "token1"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("POST /api/sessions/{code}/leave")
    class LeaveSession {

        @Test
        @DisplayName("returns 204 No Content on success")
        void returnsNoContentOnSuccess() throws Exception {
            doNothing().when(gameSessionService).leaveSession("ABCD1234", "playertoken");

            mockMvc.perform(post("/api/sessions/ABCD1234/leave")
                            .header("X-Session-Token", "playertoken"))
                    .andExpect(status().isNoContent());

            verify(gameSessionService).leaveSession("ABCD1234", "playertoken");
        }

        @Test
        @DisplayName("returns 401 when token is invalid")
        void returnsUnauthorizedForInvalidToken() throws Exception {
            doThrow(new UnauthorizedException("Invalid session token"))
                    .when(gameSessionService).leaveSession("ABCD1234", "badtoken");

            mockMvc.perform(post("/api/sessions/ABCD1234/leave")
                            .header("X-Session-Token", "badtoken"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("returns 404 when session not found")
        void returnsNotFoundWhenSessionMissing() throws Exception {
            doThrow(new SessionNotFoundException("INVALID1"))
                    .when(gameSessionService).leaveSession("INVALID1", "token");

            mockMvc.perform(post("/api/sessions/INVALID1/leave")
                            .header("X-Session-Token", "token"))
                    .andExpect(status().isNotFound());
        }
    }
}
