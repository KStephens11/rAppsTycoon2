package com.rapptycoon.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    @Test
    void registerPlayer_addsPlayerToSession() {
        registry.registerPlayer(1L, 10L, "ws-session-1");

        Set<Long> connected = registry.getConnectedPlayers(1L);
        assertThat(connected).containsExactly(10L);
    }

    @Test
    void registerPlayer_multiplePlayersInSameSession() {
        registry.registerPlayer(1L, 10L, "ws-session-1");
        registry.registerPlayer(1L, 20L, "ws-session-2");

        Set<Long> connected = registry.getConnectedPlayers(1L);
        assertThat(connected).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void removePlayer_removesFromSession() {
        registry.registerPlayer(1L, 10L, "ws-session-1");
        registry.registerPlayer(1L, 20L, "ws-session-2");

        Long removedId = registry.removePlayer("ws-session-1");

        assertThat(removedId).isEqualTo(10L);
        assertThat(registry.getConnectedPlayers(1L)).containsExactly(20L);
    }

    @Test
    void removePlayer_returnsNullForUnknownSession() {
        Long removedId = registry.removePlayer("unknown-ws-session");
        assertThat(removedId).isNull();
    }

    @Test
    void removePlayer_cleansUpEmptySession() {
        registry.registerPlayer(1L, 10L, "ws-session-1");

        registry.removePlayer("ws-session-1");

        assertThat(registry.getConnectedPlayers(1L)).isEmpty();
    }

    @Test
    void getPlayerId_returnsCorrectPlayer() {
        registry.registerPlayer(1L, 10L, "ws-session-1");

        assertThat(registry.getPlayerId("ws-session-1")).isEqualTo(10L);
    }

    @Test
    void getGameSessionId_returnsCorrectSession() {
        registry.registerPlayer(5L, 10L, "ws-session-1");

        assertThat(registry.getGameSessionId("ws-session-1")).isEqualTo(5L);
    }

    @Test
    void isPlayerConnected_returnsTrueForConnectedPlayer() {
        registry.registerPlayer(1L, 10L, "ws-session-1");

        assertThat(registry.isPlayerConnected(1L, 10L)).isTrue();
    }

    @Test
    void isPlayerConnected_returnsFalseForDisconnectedPlayer() {
        assertThat(registry.isPlayerConnected(1L, 10L)).isFalse();
    }

    @Test
    void getConnectedPlayers_returnsEmptySetForUnknownSession() {
        assertThat(registry.getConnectedPlayers(999L)).isEmpty();
    }
}
