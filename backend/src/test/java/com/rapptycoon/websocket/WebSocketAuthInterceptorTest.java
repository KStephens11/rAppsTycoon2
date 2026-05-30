package com.rapptycoon.websocket;

import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Player;
import com.rapptycoon.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private PlayerService playerService;

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Mock
    private MessageChannel channel;

    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(playerService, sessionRegistry);
    }

    @Test
    void preSend_connectWithValidToken_authenticatesPlayer() {
        Player player = Player.builder()
                .id(1L)
                .sessionId(10L)
                .displayName("TestPlayer")
                .sessionToken("valid-token-123")
                .build();

        when(playerService.validateToken("valid-token-123")).thenReturn(player);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("X-Session-Token", "valid-token-123");
        accessor.setSessionId("ws-session-1");
        accessor.setSessionAttributes(new HashMap<>());

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        verify(sessionRegistry).registerPlayer(10L, 1L, "ws-session-1");
    }

    @Test
    void preSend_connectWithMissingToken_throwsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("ws-session-1");
        accessor.setSessionAttributes(new HashMap<>());

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing X-Session-Token");
    }

    @Test
    void preSend_connectWithInvalidToken_throwsException() {
        when(playerService.validateToken("bad-token"))
                .thenThrow(new UnauthorizedException("Invalid session token"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("X-Session-Token", "bad-token");
        accessor.setSessionId("ws-session-1");
        accessor.setSessionAttributes(new HashMap<>());

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid session token");
    }

    @Test
    void preSend_nonConnectCommand_passesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("ws-session-1");
        accessor.setSessionAttributes(new HashMap<>());

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        verifyNoInteractions(playerService);
        verifyNoInteractions(sessionRegistry);
    }
}
