package com.rapptycoon.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new WebSocketBroadcaster(messagingTemplate);
    }

    @Test
    void broadcastToSession_sendsToCorrectTopic() {
        String sessionCode = "ABC12345";
        WebSocketMessage message = WebSocketMessage.of(MessageType.GAME_STARTED, Map.of("sessionCode", sessionCode));

        broadcaster.broadcastToSession(sessionCode, message);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/session/ABC12345/game");
        assertThat(messageCaptor.getValue()).isEqualTo(message);
    }

    @Test
    void sendToPlayer_sendsToCorrectPlayerTopic() {
        String sessionCode = "XYZ99999";
        Long playerId = 42L;
        WebSocketMessage message = WebSocketMessage.of(MessageType.EVENT_OCCURRED, Map.of("eventId", 5));

        broadcaster.sendToPlayer(sessionCode, playerId, message);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/session/XYZ99999/player/42/events");
        assertThat(messageCaptor.getValue()).isEqualTo(message);
    }

    @Test
    void broadcastLeaderboard_sendsToLeaderboardTopic() {
        String sessionCode = "GAME0001";
        WebSocketMessage message = WebSocketMessage.of(MessageType.LEADERBOARD_UPDATED, Map.of("leaderboard", "data"));

        broadcaster.broadcastLeaderboard(sessionCode, message);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), (Object) org.mockito.ArgumentMatchers.any());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/session/GAME0001/leaderboard");
    }

    @Test
    void sendMetricsUpdate_sendsToPlayerMetricsTopic() {
        String sessionCode = "SESS0002";
        Long playerId = 7L;
        WebSocketMessage message = WebSocketMessage.of(MessageType.METRICS_UPDATED, Map.of("basestationId", 1));

        broadcaster.sendMetricsUpdate(sessionCode, playerId, message);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), (Object) org.mockito.ArgumentMatchers.any());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/session/SESS0002/player/7/metrics");
    }

    @Test
    void sendRappStatusChange_sendsToPlayerRappsTopic() {
        String sessionCode = "SESS0003";
        Long playerId = 10L;
        WebSocketMessage message = WebSocketMessage.of(MessageType.RAPP_STATUS_CHANGED,
                Map.of("deploymentId", 5, "newStatus", "ACTIVE"));

        broadcaster.sendRappStatusChange(sessionCode, playerId, message);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), (Object) org.mockito.ArgumentMatchers.any());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/session/SESS0003/player/10/rapps");
    }
}
