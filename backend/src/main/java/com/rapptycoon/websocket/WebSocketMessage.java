package com.rapptycoon.websocket;

import java.time.Instant;

/**
 * Generic WebSocket message wrapper with type, timestamp, and payload.
 */
public record WebSocketMessage(String type, String timestamp, Object payload) {

    public static WebSocketMessage of(String type, Object payload) {
        return new WebSocketMessage(type, Instant.now().toString(), payload);
    }
}
