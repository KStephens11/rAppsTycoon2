package com.rapptycoon.websocket;

/**
 * Server-to-client WebSocket message types.
 */
public final class MessageType {

    private MessageType() {
        // utility class
    }

    public static final String GAME_ENDED = "GAME_ENDED";
    public static final String METRICS_UPDATED = "METRICS_UPDATED";
    public static final String LEADERBOARD_UPDATED = "LEADERBOARD_UPDATED";
    public static final String ACTION_ERROR = "ACTION_ERROR";
}
