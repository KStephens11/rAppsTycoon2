package com.rapptycoon.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks connected players per game session.
 * Maps gameSessionId → set of playerIds currently connected via WebSocket.
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    // gameSessionId → Set of playerIds
    private final Map<Long, Set<Long>> sessionPlayers = new ConcurrentHashMap<>();

    // WebSocket sessionId → playerId (for disconnect lookup)
    private final Map<String, Long> wsSessionToPlayer = new ConcurrentHashMap<>();

    // WebSocket sessionId → gameSessionId (for disconnect lookup)
    private final Map<String, Long> wsSessionToGameSession = new ConcurrentHashMap<>();

    /**
     * Registers a player as connected to a game session.
     */
    public void registerPlayer(Long gameSessionId, Long playerId, String wsSessionId) {
        sessionPlayers.computeIfAbsent(gameSessionId, k -> ConcurrentHashMap.newKeySet())
                .add(playerId);
        wsSessionToPlayer.put(wsSessionId, playerId);
        wsSessionToGameSession.put(wsSessionId, gameSessionId);
        log.debug("Player {} registered in game session {} (ws={})", playerId, gameSessionId, wsSessionId);
    }

    /**
     * Removes a player from the registry on disconnect.
     * Returns the playerId that was removed, or null if not found.
     */
    public Long removePlayer(String wsSessionId) {
        Long playerId = wsSessionToPlayer.remove(wsSessionId);
        Long gameSessionId = wsSessionToGameSession.remove(wsSessionId);

        if (playerId != null && gameSessionId != null) {
            Set<Long> players = sessionPlayers.get(gameSessionId);
            if (players != null) {
                players.remove(playerId);
                if (players.isEmpty()) {
                    sessionPlayers.remove(gameSessionId);
                }
            }
            log.debug("Player {} removed from game session {} (ws={})", playerId, gameSessionId, wsSessionId);
        }

        return playerId;
    }

    /**
     * Returns the set of connected player IDs for a game session.
     */
    public Set<Long> getConnectedPlayers(Long gameSessionId) {
        return sessionPlayers.getOrDefault(gameSessionId, Collections.emptySet());
    }

    /**
     * Returns the player ID associated with a WebSocket session.
     */
    public Long getPlayerId(String wsSessionId) {
        return wsSessionToPlayer.get(wsSessionId);
    }

    /**
     * Returns the game session ID associated with a WebSocket session.
     */
    public Long getGameSessionId(String wsSessionId) {
        return wsSessionToGameSession.get(wsSessionId);
    }

    /**
     * Checks if a player is currently connected.
     */
    public boolean isPlayerConnected(Long gameSessionId, Long playerId) {
        Set<Long> players = sessionPlayers.get(gameSessionId);
        return players != null && players.contains(playerId);
    }
}
