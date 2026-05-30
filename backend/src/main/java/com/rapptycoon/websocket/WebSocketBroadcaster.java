package com.rapptycoon.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Helper component for broadcasting WebSocket messages to sessions and individual players.
 */
@Component
public class WebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts a message to all players in a game session.
     * Sends to /topic/session/{code}/game
     */
    public void broadcastToSession(String sessionCode, WebSocketMessage message) {
        String destination = "/topic/session/" + sessionCode + "/game";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Broadcast to session {}: type={}", sessionCode, message.type());
    }

    /**
     * Sends a message to a specific player in a session.
     * Sends to /topic/session/{code}/player/{playerId}/events
     */
    public void sendToPlayer(String sessionCode, Long playerId, WebSocketMessage message) {
        String destination = "/topic/session/" + sessionCode + "/player/" + playerId + "/events";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Sent to player {} in session {}: type={}", playerId, sessionCode, message.type());
    }

    /**
     * Broadcasts leaderboard update to all players in a session.
     * Sends to /topic/session/{code}/leaderboard
     */
    public void broadcastLeaderboard(String sessionCode, WebSocketMessage message) {
        String destination = "/topic/session/" + sessionCode + "/leaderboard";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Broadcast leaderboard to session {}", sessionCode);
    }

    /**
     * Sends metrics update to a specific player.
     * Sends to /topic/session/{code}/player/{playerId}/metrics
     */
    public void sendMetricsUpdate(String sessionCode, Long playerId, WebSocketMessage message) {
        String destination = "/topic/session/" + sessionCode + "/player/" + playerId + "/metrics";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Sent metrics update to player {} in session {}", playerId, sessionCode);
    }

    /**
     * Sends rApp status change to a specific player.
     * Sends to /topic/session/{code}/player/{playerId}/rapps
     */
    public void sendRappStatusChange(String sessionCode, Long playerId, WebSocketMessage message) {
        String destination = "/topic/session/" + sessionCode + "/player/" + playerId + "/rapps";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Sent rApp status change to player {} in session {}", playerId, sessionCode);
    }
}
