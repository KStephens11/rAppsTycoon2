package com.rapptycoon.websocket;

/**
 * Server-to-client and client-to-server WebSocket message types.
 */
public final class MessageType {

    private MessageType() {
        // utility class
    }

    // Server → Client
    public static final String GAME_STARTED = "GAME_STARTED";
    public static final String GAME_ENDED = "GAME_ENDED";
    public static final String EVENT_OCCURRED = "EVENT_OCCURRED";
    public static final String METRICS_UPDATED = "METRICS_UPDATED";
    public static final String LEADERBOARD_UPDATED = "LEADERBOARD_UPDATED";
    public static final String RAPP_STATUS_CHANGED = "RAPP_STATUS_CHANGED";
    public static final String ACTION_ERROR = "ACTION_ERROR";

    // Client → Server
    public static final String PLAYER_ACTION = "PLAYER_ACTION";
}
