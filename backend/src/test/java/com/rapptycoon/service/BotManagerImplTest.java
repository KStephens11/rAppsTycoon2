package com.rapptycoon.service;

import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotManagerImplTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private MixedOperation mixedOperation;

    @Mock
    private NonNamespaceOperation nonNamespaceOperation;

    @Mock
    private PodResource podResource;

    @Mock
    private FilterWatchListDeletable filterWatchListDeletable;

    private BotManagerImpl botManager;

    @BeforeEach
    void setUp() {
        botManager = new BotManagerImpl(gameSessionRepository, playerRepository, kubernetesClient);
        ReflectionTestUtils.setField(botManager, "backendBaseUrl", "http://backend:8080");
        ReflectionTestUtils.setField(botManager, "kubernetesEnabled", true);
    }

    private GameSession createSession(String code) {
        return GameSession.builder()
                .id(1L)
                .sessionCode(code)
                .state(GameSessionState.ACTIVE)
                .hostPlayerId(1L)
                .build();
    }

    private Player createBotPlayer(Long id, String name, String difficulty) {
        return Player.builder()
                .id(id)
                .sessionId(1L)
                .displayName(name)
                .sessionToken("bot-token-" + id)
                .isBot(true)
                .difficulty(difficulty)
                .scoreMoney(new BigDecimal("1000.00"))
                .build();
    }

    private Player createHumanPlayer(Long id) {
        return Player.builder()
                .id(id)
                .sessionId(1L)
                .displayName("Human")
                .sessionToken("human-token-" + id)
                .isBot(false)
                .scoreMoney(new BigDecimal("1000.00"))
                .build();
    }

    @Nested
    @DisplayName("provisionBots")
    class ProvisionBots {

        @Test
        @DisplayName("does nothing when kubernetes is disabled")
        void doesNothingWhenKubernetesDisabled() {
            ReflectionTestUtils.setField(botManager, "kubernetesEnabled", false);

            botManager.provisionBots("ABCD1234");

            verify(gameSessionRepository, never()).findBySessionCode(anyString());
            verify(kubernetesClient, never()).pods();
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-existent session")
        void throwsForNonExistentSession() {
            when(gameSessionRepository.findBySessionCode("INVALID1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> botManager.provisionBots("INVALID1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Session not found");
        }

        @Test
        @DisplayName("does nothing when no bot players exist in session")
        void doesNothingWithNoBots() {
            GameSession session = createSession("ABCD1234");
            Player human = createHumanPlayer(1L);

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(human));

            botManager.provisionBots("ABCD1234");

            verify(kubernetesClient, never()).pods();
        }

        @Test
        @DisplayName("creates a pod for each bot player in the session")
        @SuppressWarnings("unchecked")
        void createsPodForEachBot() {
            GameSession session = createSession("ABCD1234");
            Player bot1 = createBotPlayer(10L, "Bot-Alpha", "MEDIUM");
            Player bot2 = createBotPlayer(11L, "Bot-Beta", "HARD");
            Player human = createHumanPlayer(1L);

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(human, bot1, bot2));

            // Mock the Kubernetes client chain
            when(kubernetesClient.pods()).thenReturn(mixedOperation);
            when(mixedOperation.inNamespace("default")).thenReturn(nonNamespaceOperation);
            when(nonNamespaceOperation.resource(any(Pod.class))).thenReturn(podResource);
            when(podResource.create()).thenReturn(new Pod());
            when(nonNamespaceOperation.withName(anyString())).thenReturn(podResource);
            when(podResource.waitUntilReady(anyLong(), any())).thenReturn(new Pod());

            botManager.provisionBots("ABCD1234");

            // Should create 2 pods (one for each bot, not for human)
            verify(nonNamespaceOperation, times(2)).resource(any(Pod.class));
            verify(podResource, times(2)).create();
        }

        @Test
        @DisplayName("continues provisioning other bots if one pod creation fails")
        @SuppressWarnings("unchecked")
        void continuesOnPodCreationFailure() {
            GameSession session = createSession("ABCD1234");
            Player bot1 = createBotPlayer(10L, "Bot-Alpha", "MEDIUM");
            Player bot2 = createBotPlayer(11L, "Bot-Beta", "HARD");

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(bot1, bot2));

            when(kubernetesClient.pods()).thenReturn(mixedOperation);
            when(mixedOperation.inNamespace("default")).thenReturn(nonNamespaceOperation);

            // First pod creation throws, second succeeds
            when(nonNamespaceOperation.resource(any(Pod.class)))
                    .thenThrow(new RuntimeException("API error"))
                    .thenReturn(podResource);
            when(podResource.create()).thenReturn(new Pod());
            when(nonNamespaceOperation.withName(anyString())).thenReturn(podResource);
            when(podResource.waitUntilReady(anyLong(), any())).thenReturn(new Pod());

            // Should not throw
            botManager.provisionBots("ABCD1234");

            // Still tried to create second pod despite first failing
            verify(nonNamespaceOperation, times(2)).resource(any(Pod.class));
        }
    }

    @Nested
    @DisplayName("cleanupBots")
    class CleanupBots {

        @Test
        @DisplayName("does nothing when kubernetes is disabled")
        void doesNothingWhenKubernetesDisabled() {
            ReflectionTestUtils.setField(botManager, "kubernetesEnabled", false);

            botManager.cleanupBots("ABCD1234");

            verify(kubernetesClient, never()).pods();
        }

        @Test
        @DisplayName("deletes pods with matching session labels")
        @SuppressWarnings("unchecked")
        void deletesPodsWithSessionLabels() {
            when(kubernetesClient.pods()).thenReturn(mixedOperation);
            when(mixedOperation.inNamespace("default")).thenReturn(nonNamespaceOperation);
            when(nonNamespaceOperation.withLabels(anyMap())).thenReturn(filterWatchListDeletable);

            botManager.cleanupBots("ABCD1234");

            verify(nonNamespaceOperation).withLabels(Map.of(
                    "rapptycoon/session", "ABCD1234",
                    "rapptycoon/component", "bot-player"
            ));
            verify(filterWatchListDeletable).delete();
        }

        @Test
        @DisplayName("does not throw when deletion fails")
        @SuppressWarnings("unchecked")
        void doesNotThrowOnDeletionFailure() {
            when(kubernetesClient.pods()).thenReturn(mixedOperation);
            when(mixedOperation.inNamespace("default")).thenReturn(nonNamespaceOperation);
            when(nonNamespaceOperation.withLabels(anyMap())).thenThrow(new RuntimeException("K8s API error"));

            // Should not throw, just log the error
            botManager.cleanupBots("ABCD1234");
        }
    }
}
