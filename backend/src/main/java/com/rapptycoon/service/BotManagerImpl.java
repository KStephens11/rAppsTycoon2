package com.rapptycoon.service;

import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link BotManager} that provisions bot player pods on Kubernetes
 * using the Fabric8 Kubernetes Java Client.
 */
@Service
public class BotManagerImpl implements BotManager {

    private static final String BOT_POD_PREFIX = "bot-player-";
    private static final String BOT_IMAGE = "bot-player:latest";
    private static final String BOT_NAMESPACE = "default";
    private static final String LABEL_SESSION = "rapptycoon/session";
    private static final String LABEL_COMPONENT = "rapptycoon/component";
    private static final String LABEL_COMPONENT_VALUE = "bot-player";
    private static final int POD_STARTUP_TIMEOUT_SECONDS = 30;

    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final KubernetesClient kubernetesClient;

    @Value("${rapptycoon.backend.base-url:http://backend:8080}")
    private String backendBaseUrl;

    @Value("${rapptycoon.bot.kubernetes.enabled:false}")
    private boolean kubernetesEnabled;

    public BotManagerImpl(GameSessionRepository gameSessionRepository,
                          PlayerRepository playerRepository,
                          KubernetesClient kubernetesClient) {
        this.gameSessionRepository = gameSessionRepository;
        this.playerRepository = playerRepository;
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public void provisionBots(String sessionCode) {
        if (!kubernetesEnabled) {
            return;
        }

        GameSession session = gameSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Session not found: " + sessionCode));

        List<Player> botPlayers = playerRepository.findBySessionId(session.getId())
                .stream()
                .filter(Player::isBot)
                .toList();

        if (botPlayers.isEmpty()) {
            return;
        }

        for (Player bot : botPlayers) {
            try {
                createBotPod(sessionCode, bot);
            } catch (Exception e) {
                // Continue — game proceeds without this bot
            }
        }
    }

    @Override
    public void cleanupBots(String sessionCode) {
        if (!kubernetesEnabled) {
            return;
        }

        try {
            Map<String, String> labelSelector = Map.of(
                    LABEL_SESSION, sessionCode,
                    LABEL_COMPONENT, LABEL_COMPONENT_VALUE
            );

            kubernetesClient.pods()
                    .inNamespace(BOT_NAMESPACE)
                    .withLabels(labelSelector)
                    .delete();
        } catch (Exception e) {
            // Cleanup failed silently
        }
    }

    private void createBotPod(String sessionCode, Player bot) {
        String podName = generatePodName(sessionCode, bot);

        Pod pod = new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withNamespace(BOT_NAMESPACE)
                    .withLabels(Map.of(
                            LABEL_SESSION, sessionCode,
                            LABEL_COMPONENT, LABEL_COMPONENT_VALUE,
                            "rapptycoon/bot-id", String.valueOf(bot.getId())
                    ))
                .endMetadata()
                .withNewSpec()
                    .withRestartPolicy("Never")
                    .addNewContainer()
                        .withName("bot-player")
                        .withImage(BOT_IMAGE)
                        .withEnv(
                                new EnvVarBuilder().withName("SESSION_CODE").withValue(sessionCode).build(),
                                new EnvVarBuilder().withName("SESSION_TOKEN").withValue(bot.getSessionToken()).build(),
                                new EnvVarBuilder().withName("DIFFICULTY").withValue(bot.getDifficulty()).build(),
                                new EnvVarBuilder().withName("BACKEND_BASE_URL").withValue(backendBaseUrl).build()
                        )
                        .withNewResources()
                            .addToLimits("memory", new Quantity("64Mi"))
                            .addToLimits("cpu", new Quantity("100m"))
                            .addToRequests("memory", new Quantity("32Mi"))
                            .addToRequests("cpu", new Quantity("50m"))
                        .endResources()
                        .addNewPort()
                            .withContainerPort(8081)
                            .withName("health")
                        .endPort()
                        .withNewLivenessProbe()
                            .withNewHttpGet()
                                .withPath("/health")
                                .withPort(new IntOrString(8081))
                            .endHttpGet()
                            .withInitialDelaySeconds(10)
                            .withPeriodSeconds(15)
                        .endLivenessProbe()
                    .endContainer()
                    .withNewSecurityContext()
                        .withRunAsNonRoot(true)
                        .withRunAsUser(1000L)
                    .endSecurityContext()
                .endSpec()
                .build();

        kubernetesClient.pods()
                .inNamespace(BOT_NAMESPACE)
                .resource(pod)
                .create();

        // Wait for pod to start (up to 30 seconds), continue if it doesn't
        try {
            kubernetesClient.pods()
                    .inNamespace(BOT_NAMESPACE)
                    .withName(podName)
                    .waitUntilReady(POD_STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Pod did not become ready in time — continue without it
        }
    }

    private String generatePodName(String sessionCode, Player bot) {
        return BOT_POD_PREFIX + sessionCode.toLowerCase() + "-" + bot.getId();
    }
}
