package com.rapptycoon.websocket;

import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.service.RappService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameActionControllerTest {

    @Mock
    private RappService rappService;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private WebSocketBroadcaster broadcaster;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    private GameActionController controller;

    @BeforeEach
    void setUp() {
        controller = new GameActionController(rappService, playerRepository, gameSessionRepository, broadcaster);
    }

    private void setupAuthenticatedPlayer(Long playerId) {
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(WebSocketAuthInterceptor.PLAYER_ID_ATTR, playerId);
        when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);
    }

    private Player createPlayer(Long id, String token) {
        return Player.builder()
                .id(id)
                .sessionId(1L)
                .displayName("Player" + id)
                .sessionToken(token)
                .connected(true)
                .build();
    }

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        @DisplayName("returns early when playerId is not in session attributes")
        void returnsEarlyWhenNoPlayerId() {
            Map<String, Object> sessionAttributes = new HashMap<>();
            when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttributes);

            Map<String, Object> payload = Map.of("action", "DEPLOY");
            controller.handleAction("ABCD1234", payload, headerAccessor);

            verifyNoInteractions(rappService);
            verifyNoInteractions(broadcaster);
        }

        @Test
        @DisplayName("sends error when player not found in repository")
        void sendsErrorWhenPlayerNotFound() {
            setupAuthenticatedPlayer(99L);
            when(playerRepository.findById(99L)).thenReturn(Optional.empty());

            Map<String, Object> payload = Map.of("action", "DEPLOY");
            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(99L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
            verifyNoInteractions(rappService);
        }
    }

    @Nested
    @DisplayName("action validation")
    class ActionValidation {

        @Test
        @DisplayName("sends error when action field is missing")
        void sendsErrorWhenActionMissing() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            // No "action" key
            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(1L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
            verifyNoInteractions(rappService);
        }

        @Test
        @DisplayName("sends error for unknown action type")
        void sendsErrorForUnknownAction() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = Map.of("action", "EXPLODE");
            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(1L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
            verifyNoInteractions(rappService);
        }
    }

    @Nested
    @DisplayName("DEPLOY action")
    class DeployAction {

        @Test
        @DisplayName("calls rappService.deploy with correct parameters")
        void callsDeployWithCorrectParams() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "DEPLOY");
            payload.put("templateId", 3);
            payload.put("basestationId", 10);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService).deploy("ABCD1234", "token123", 3L, 10L);
        }

        @Test
        @DisplayName("handles Long values for templateId and basestationId")
        void handlesLongValues() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "DEPLOY");
            payload.put("templateId", 5L);
            payload.put("basestationId", 20L);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService).deploy("ABCD1234", "token123", 5L, 20L);
        }

        @Test
        @DisplayName("sends error when templateId is missing")
        void sendsErrorWhenTemplateIdMissing() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "DEPLOY");
            payload.put("basestationId", 10);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(1L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
        }

        @Test
        @DisplayName("sends error when basestationId is missing")
        void sendsErrorWhenBasestationIdMissing() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "DEPLOY");
            payload.put("templateId", 3);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService, never()).deploy(anyString(), anyString(), anyLong(), anyLong());
            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(1L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
        }

        @Test
        @DisplayName("sends error when rappService throws exception")
        void sendsErrorWhenServiceThrows() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
            doThrow(new RuntimeException("Insufficient funds"))
                    .when(rappService).deploy("ABCD1234", "token123", 3L, 10L);

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "DEPLOY");
            payload.put("templateId", 3);
            payload.put("basestationId", 10);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(1L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
        }
    }

    @Nested
    @DisplayName("TUNE action")
    class TuneAction {

        @Test
        @DisplayName("calls rappService.tune with configuration parameters")
        void callsTuneWithConfig() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> config = Map.of("threshold", 80, "aggressiveness", "HIGH");
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "TUNE");
            payload.put("deploymentId", 5);
            payload.put("configuration", config);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService).tune("ABCD1234", "token123", 5L, 80, "HIGH");
        }

        @Test
        @DisplayName("uses defaults when configuration is null")
        void usesDefaultsWhenConfigNull() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "TUNE");
            payload.put("deploymentId", 5);
            // No "configuration" key

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService).tune("ABCD1234", "token123", 5L, 50, "MODERATE");
        }

        @Test
        @DisplayName("sends error when deploymentId is missing")
        void sendsErrorWhenDeploymentIdMissing() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "TUNE");

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService, never()).tune(anyString(), anyString(), anyLong(), anyInt(), anyString());
            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(1L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
        }
    }

    @Nested
    @DisplayName("DISABLE action")
    class DisableAction {

        @Test
        @DisplayName("calls rappService.disable with correct parameters")
        void callsDisableWithCorrectParams() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "DISABLE");
            payload.put("deploymentId", 7);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService).disable("ABCD1234", "token123", 7L);
        }

        @Test
        @DisplayName("sends error when deploymentId is missing")
        void sendsErrorWhenDeploymentIdMissing() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "DISABLE");

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService, never()).disable(anyString(), anyString(), anyLong());
            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(1L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
        }
    }

    @Nested
    @DisplayName("ROLLBACK action")
    class RollbackAction {

        @Test
        @DisplayName("calls rappService.rollback with correct parameters")
        void callsRollbackWithCorrectParams() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "ROLLBACK");
            payload.put("deploymentId", 9);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService).rollback("ABCD1234", "token123", 9L);
        }

        @Test
        @DisplayName("sends error when deploymentId is missing")
        void sendsErrorWhenDeploymentIdMissing() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "ROLLBACK");

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService, never()).rollback(anyString(), anyString(), anyLong());
            verify(broadcaster).sendToPlayer(eq("ABCD1234"), eq(1L), argThat(msg ->
                    msg.type().equals(MessageType.ACTION_ERROR)));
        }
    }

    @Nested
    @DisplayName("case insensitivity")
    class CaseInsensitivity {

        @Test
        @DisplayName("handles lowercase action names")
        void handlesLowercaseActions() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "deploy");
            payload.put("templateId", 1);
            payload.put("basestationId", 2);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService).deploy("ABCD1234", "token123", 1L, 2L);
        }

        @Test
        @DisplayName("handles mixed case action names")
        void handlesMixedCaseActions() {
            setupAuthenticatedPlayer(1L);
            Player player = createPlayer(1L, "token123");
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "Disable");
            payload.put("deploymentId", 4);

            controller.handleAction("ABCD1234", payload, headerAccessor);

            verify(rappService).disable("ABCD1234", "token123", 4L);
        }
    }
}
