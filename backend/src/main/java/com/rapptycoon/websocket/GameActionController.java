package com.rapptycoon.websocket;

import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.service.RappService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * Handles client-to-server PLAYER_ACTION messages via WebSocket STOMP.
 */
@Controller
public class GameActionController {

    private static final Logger log = LoggerFactory.getLogger(GameActionController.class);

    private final RappService rappService;
    private final PlayerRepository playerRepository;
    private final GameSessionRepository gameSessionRepository;
    private final WebSocketBroadcaster broadcaster;

    public GameActionController(RappService rappService,
                                PlayerRepository playerRepository,
                                GameSessionRepository gameSessionRepository,
                                WebSocketBroadcaster broadcaster) {
        this.rappService = rappService;
        this.playerRepository = playerRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.broadcaster = broadcaster;
    }

    @MessageMapping("/session/{code}/action")
    public void handleAction(@DestinationVariable String code,
                             @Payload Map<String, Object> actionPayload,
                             SimpMessageHeaderAccessor headerAccessor) {
        Long playerId = (Long) headerAccessor.getSessionAttributes().get(WebSocketAuthInterceptor.PLAYER_ID_ATTR);
        if (playerId == null) {
            log.warn("Action received without authenticated player for session {}", code);
            return;
        }

        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null) {
            sendActionError(code, playerId, "UNAUTHORIZED", "Player not found");
            return;
        }

        String token = player.getSessionToken();
        String action = (String) actionPayload.get("action");

        if (action == null) {
            sendActionError(code, playerId, "VALIDATION_ERROR", "Missing 'action' field");
            return;
        }

        try {
            switch (action.toUpperCase()) {
                case "DEPLOY" -> handleDeploy(code, token, actionPayload);
                case "TUNE" -> handleTune(code, token, actionPayload);
                case "DISABLE" -> handleDisable(code, token, actionPayload);
                case "ROLLBACK" -> handleRollback(code, token, actionPayload);
                default -> sendActionError(code, playerId, "VALIDATION_ERROR",
                        "Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Error handling action {} for player {} in session {}: {}",
                    action, playerId, code, e.getMessage());
            sendActionError(code, playerId, "VALIDATION_ERROR", e.getMessage());
        }
    }

    private void handleDeploy(String code, String token, Map<String, Object> payload) {
        Long templateId = toLong(payload.get("templateId"));
        Long basestationId = toLong(payload.get("basestationId"));

        if (templateId == null || basestationId == null) {
            throw new IllegalArgumentException("DEPLOY requires templateId and basestationId");
        }

        rappService.deploy(code, token, templateId, basestationId);
    }

    private void handleTune(String code, String token, Map<String, Object> payload) {
        Long deploymentId = toLong(payload.get("deploymentId"));
        if (deploymentId == null) {
            throw new IllegalArgumentException("TUNE requires deploymentId");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> configuration = (Map<String, Object>) payload.get("configuration");
        Integer threshold = configuration != null ? toInt(configuration.get("threshold")) : 50;
        String aggressiveness = configuration != null ?
                (String) configuration.get("aggressiveness") : "MODERATE";

        rappService.tune(code, token, deploymentId, threshold, aggressiveness);
    }

    private void handleDisable(String code, String token, Map<String, Object> payload) {
        Long deploymentId = toLong(payload.get("deploymentId"));
        if (deploymentId == null) {
            throw new IllegalArgumentException("DISABLE requires deploymentId");
        }

        rappService.disable(code, token, deploymentId);
    }

    private void handleRollback(String code, String token, Map<String, Object> payload) {
        Long deploymentId = toLong(payload.get("deploymentId"));
        if (deploymentId == null) {
            throw new IllegalArgumentException("ROLLBACK requires deploymentId");
        }

        rappService.rollback(code, token, deploymentId);
    }

    private void sendActionError(String code, Long playerId, String error, String message) {
        Map<String, String> errorPayload = Map.of(
                "error", error,
                "message", message
        );
        WebSocketMessage errorMessage = WebSocketMessage.of(MessageType.ACTION_ERROR, errorPayload);
        broadcaster.sendToPlayer(code, playerId, errorMessage);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
