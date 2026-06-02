package com.rapptycoon.websocket;

import com.rapptycoon.service.PlayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listens for WebSocket disconnect events and marks players as disconnected.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final WebSocketSessionRegistry sessionRegistry;
    private final PlayerService playerService;

    public WebSocketEventListener(WebSocketSessionRegistry sessionRegistry, PlayerService playerService) {
        this.sessionRegistry = sessionRegistry;
        this.playerService = playerService;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String wsSessionId = accessor.getSessionId();

        Long playerId = sessionRegistry.removePlayer(wsSessionId);
        if (playerId != null) {
            try {
                playerService.disconnect(playerId);
                log.info("Player {} disconnected (ws session: {})", playerId, wsSessionId);
            } catch (Exception e) {
                log.error("Error disconnecting player {}: {}", playerId, e.getMessage());
            }
        }
    }
}
