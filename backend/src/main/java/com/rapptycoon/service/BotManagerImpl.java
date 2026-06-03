package com.rapptycoon.service;

import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BotManagerImpl.class);

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
            log.info("Kubernetes bot provisioning is disabled (rapptycoon.bot.kubernetes.enabled=false). " +
                     "Bots for session {} will not be provisioned as pods. " +
                     "Run bot-player manually or use 'docker compose --profile bot run bot-player'.", sessionCode);
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
            log.info("No bot players found for session {}, skipping pod provisioning", sessionCode);
            return;
        }

        log.info("Provisioning {} bot pod(s) for session {}", botPlayers.size(), sessionCode);

        for (Player bot : botPlayers) {
            try {
                createBotPod(sessionCode, bot);
            } catch (Exception e) {
                log.error("Failed to create pod for bot '{}' in session {}: {}",
                        bot.getDisplayName(), sessionCode, e.getMessage(), e);
                // Log and continue — game proceeds without this bot
            }
        }
    }

    @Override
    public void cleanupBots(String sessionCode) {
        if (!kubernetesEnabled) {
            return;
        }

        log.info("Cleaning up bot pods for session {}", sessionCode);

        try {
            Map<String, String> labelSelector = Map.of(
                    LABEL_SESSION, sessionCode,
                    LABEL_COMPONENT, LABEL_COMPONENT_VALUE
            );

            kubernetesClient.pods()
                    .inNamespace(BOT_NAMESPACE)
                    .withLabels(labelSelector)
                    .delete();

            log.info("Successfully deleted bot pods for session {}", sessionCode);
        } catch (Exception e) {
            log.error("Failed to cleanup bot pods for session {}: {}",
                    sessionCode, e.getMessage(), e);
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

        log.info("Creating bot pod '{}' for bot '{}' in session {}",
                podName, bot.getDisplayName(), sessionCode);

        kubernetesClient.pods()
                .inNamespace(BOT_NAMESPACE)
                .resource(pod)
                .create();

        // Wait for pod to start (up to 30 seconds), but log and continue if it doesn't
        try {
            kubernetesClient.pods()
                    .inNamespace(BOT_NAMESPACE)
                    .withName(podName)
                    .waitUntilReady(POD_STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Bot pod '{}' is ready for session {}", podName, sessionCode);
        } catch (Exception e) {
            log.warn("Bot pod '{}' did not become ready within {} seconds for session {}. Continuing without it.",
                    podName, POD_STARTUP_TIMEOUT_SECONDS, sessionCode);
        }
    }

    private String generatePodName(String sessionCode, Player bot) {
        return BOT_POD_PREFIX + sessionCode.toLowerCase() + "-" + bot.getId();
    }
}
