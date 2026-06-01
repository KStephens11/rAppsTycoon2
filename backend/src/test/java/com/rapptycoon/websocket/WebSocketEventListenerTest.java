package com.rapptycoon.websocket;

import com.rapptycoon.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Mock
    private PlayerService playerService;

    private WebSocketEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener = new WebSocketEventListener(sessionRegistry, playerService);
    }

    @Test
    void handleDisconnect_marksPlayerAsDisconnected() {
        when(sessionRegistry.removePlayer("ws-session-1")).thenReturn(42L);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.DISCONNECT);
        accessor.setSessionId("ws-session-1");
        accessor.setSessionAttributes(new HashMap<>());

        SessionDisconnectEvent event = new SessionDisconnectEvent(
                this,
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                "ws-session-1",
                CloseStatus.NORMAL
        );

        eventListener.handleDisconnect(event);

        verify(playerService).disconnect(42L);
    }

    @Test
    void handleDisconnect_unknownSession_doesNotCallPlayerService() {
        when(sessionRegistry.removePlayer("unknown-session")).thenReturn(null);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.DISCONNECT);
        accessor.setSessionId("unknown-session");
        accessor.setSessionAttributes(new HashMap<>());

        SessionDisconnectEvent event = new SessionDisconnectEvent(
                this,
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                "unknown-session",
                CloseStatus.NORMAL
        );

        eventListener.handleDisconnect(event);

        verifyNoInteractions(playerService);
    }
}
