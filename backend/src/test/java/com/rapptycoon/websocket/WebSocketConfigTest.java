package com.rapptycoon.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WebSocketConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void webSocketConfig_beansAreLoaded() {
        assertThat(applicationContext.getBean(WebSocketConfig.class)).isNotNull();
        assertThat(applicationContext.getBean(WebSocketBroadcaster.class)).isNotNull();
        assertThat(applicationContext.getBean(WebSocketAuthInterceptor.class)).isNotNull();
        assertThat(applicationContext.getBean(WebSocketSessionRegistry.class)).isNotNull();
        assertThat(applicationContext.getBean(WebSocketEventListener.class)).isNotNull();
        assertThat(applicationContext.getBean(GameActionController.class)).isNotNull();
    }

    @Test
    void simpMessagingTemplate_isAvailable() {
        assertThat(messagingTemplate).isNotNull();
    }
}
