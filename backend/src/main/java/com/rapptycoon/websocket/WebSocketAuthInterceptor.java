package com.rapptycoon.websocket;

import com.rapptycoon.model.Player;
import com.rapptycoon.service.PlayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Intercepts STOMP CONNECT frames to validate the X-Session-Token header.
 * On successful validation, stores player info in session attributes.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);
    private static final String TOKEN_HEADER = "X-Session-Token";
    public static final String PLAYER_ID_ATTR = "playerId";
    public static final String SESSION_ID_ATTR = "gameSessionId";

    private final PlayerService playerService;
    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketAuthInterceptor(PlayerService playerService, WebSocketSessionRegistry sessionRegistry) {
        this.playerService = playerService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader(TOKEN_HEADER);
            if (token == null || token.isBlank()) {
                log.warn("WebSocket CONNECT rejected: missing X-Session-Token header");
                throw new IllegalArgumentException("Missing X-Session-Token header");
            }

            try {
                Player player = playerService.validateToken(token);
                accessor.getSessionAttributes().put(PLAYER_ID_ATTR, player.getId());
                accessor.getSessionAttributes().put(SESSION_ID_ATTR, player.getSessionId());

                // Register the player in the session registry
                sessionRegistry.registerPlayer(player.getSessionId(), player.getId(), accessor.getSessionId());

                log.debug("WebSocket CONNECT authenticated: playerId={}, sessionId={}",
                        player.getId(), player.getSessionId());
            } catch (Exception e) {
                log.warn("WebSocket CONNECT rejected: invalid token - {}", e.getMessage());
                throw new IllegalArgumentException("Invalid session token");
            }
        }

        return message;
    }
}
