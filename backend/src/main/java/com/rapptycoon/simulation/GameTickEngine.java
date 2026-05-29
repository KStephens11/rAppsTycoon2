package com.rapptycoon.simulation;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.factory.RappBehaviour;
import com.rapptycoon.factory.RappBehaviourRegistry;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.*;
import com.rapptycoon.service.BasestationService;
import com.rapptycoon.service.EventService;
import com.rapptycoon.service.GameSessionService;
import com.rapptycoon.service.ScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class GameTickEngine {

    private static final Logger log = LoggerFactory.getLogger(GameTickEngine.class);

    private final GameSessionRepository gameSessionRepository;
    private final RappDeploymentRepository rappDeploymentRepository;
    private final GameEventRepository gameEventRepository;
    private final BasestationRepository basestationRepository;
    private final RappTemplateRepository rappTemplateRepository;
    private final PlayerRepository playerRepository;
    private final RappBehaviourRegistry rappBehaviourRegistry;
    private final BasestationService basestationService;
    private final EventService eventService;
    private final ScoreService scoreService;
    private final GameSessionService gameSessionService;
    private final GameProperties gameProperties;

    public GameTickEngine(GameSessionRepository gameSessionRepository,
                          RappDeploymentRepository rappDeploymentRepository,
                          GameEventRepository gameEventRepository,
                          BasestationRepository basestationRepository,
                          RappTemplateRepository rappTemplateRepository,
                          PlayerRepository playerRepository,
                          RappBehaviourRegistry rappBehaviourRegistry,
                          BasestationService basestationService,
                          EventService eventService,
                          ScoreService scoreService,
                          GameSessionService gameSessionService,
                          GameProperties gameProperties) {
        this.gameSessionRepository = gameSessionRepository;
        this.rappDeploymentRepository = rappDeploymentRepository;
        this.gameEventRepository = gameEventRepository;
        this.basestationRepository = basestationRepository;
        this.rappTemplateRepository = rappTemplateRepository;
        this.playerRepository = playerRepository;
        this.rappBehaviourRegistry = rappBehaviourRegistry;
        this.basestationService = basestationService;
        this.eventService = eventService;
        this.scoreService = scoreService;
        this.gameSessionService = gameSessionService;
        this.gameProperties = gameProperties;
    }

    @Scheduled(fixedDelayString = "${game.tick.interval}")
    public void tick() {
        List<GameSession> activeSessions = gameSessionRepository.findByState(GameSessionState.ACTIVE);
        for (GameSession session : activeSessions) {
            try {
                processTick(session);
            } catch (Exception e) {
                log.error("Error processing tick for session {}: {}", session.getSessionCode(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void processTick(GameSession session) {
        int currentTick = session.getCurrentTick();

        // 1. Activate DEPLOYING rApps that have waited 1 tick
        activateDeployingRapps(session);

        // 2. Apply active rApp impacts per tick to basestations
        applyRappImpacts(session);

        // 3. Apply active event impacts per tick
        applyEventImpacts(session);

        // 4. Escalate unresolved events
        escalateEvents(session, currentTick);

        // 5. Check event resolution
        checkEventResolution(session);

        // 6. Check auto-resolve for events at max escalation
        checkAutoResolve(session);

        // 7. Recalculate all player scores
        scoreService.recalculateAllScores(session.getId());

        // 8. Increment tick counter
        session.setCurrentTick(currentTick + 1);
        gameSessionRepository.save(session);

        // 9. Check game end condition
        if (session.getCurrentTick() >= gameProperties.getTick().getTotal()) {
            gameSessionService.endSession(session.getSessionCode());
        }
    }

    /**
     * Activates rApps that are in DEPLOYING status (they've waited 1 tick).
     */
    private void activateDeployingRapps(GameSession session) {
        // Find all basestations in this session via players
        List<Basestation> basestations = getSessionBasestations(session.getId());
        for (Basestation bs : basestations) {
            List<RappDeployment> deployingRapps = rappDeploymentRepository
                    .findByBasestationIdAndStatus(bs.getId(), DeploymentStatus.DEPLOYING);
            for (RappDeployment deployment : deployingRapps) {
                deployment.setStatus(DeploymentStatus.ACTIVE);
                rappDeploymentRepository.save(deployment);
            }
        }
    }

    /**
     * Applies per-tick impacts from all ACTIVE rApps to their basestations.
     */
    private void applyRappImpacts(GameSession session) {
        List<Basestation> basestations = getSessionBasestations(session.getId());
        for (Basestation bs : basestations) {
            List<RappDeployment> activeDeployments = rappDeploymentRepository
                    .findByBasestationIdAndStatus(bs.getId(), DeploymentStatus.ACTIVE);
            for (RappDeployment deployment : activeDeployments) {
                rappTemplateRepository.findById(deployment.getTemplateId()).ifPresent(template -> {
                    RappBehaviour behaviour = rappBehaviourRegistry.getBehaviour(template.getName());
                    Aggressiveness aggressiveness = parseAggressiveness(deployment.getConfiguration());
                    MetricDeltas impact = behaviour.calculateImpact(aggressiveness);
                    basestationService.updateMetrics(bs.getId(), impact);
                });
            }
        }
    }

    /**
     * Applies per-tick impacts from all unresolved events to their basestations.
     * Applies both escalation multiplier and severity multiplier.
     */
    private void applyEventImpacts(GameSession session) {
        List<GameEvent> unresolvedEvents = eventService.getUnresolvedEvents(session.getId());
        for (GameEvent event : unresolvedEvents) {
            BigDecimal escalationMultiplier = getEscalationMultiplier(event.getEscalationLevel());
            BigDecimal severityMultiplier = getSeverityMultiplier(event.getSeverity());
            BigDecimal combinedMultiplier = escalationMultiplier.multiply(severityMultiplier);

            MetricDeltas eventImpact = new MetricDeltas(
                    event.getImpactHealth().multiply(combinedMultiplier).setScale(2, RoundingMode.HALF_UP),
                    event.getImpactCustomerExperience().multiply(combinedMultiplier).setScale(2, RoundingMode.HALF_UP),
                    event.getImpactCost().multiply(combinedMultiplier).setScale(2, RoundingMode.HALF_UP),
                    event.getImpactEnergyEfficiency().multiply(combinedMultiplier).setScale(2, RoundingMode.HALF_UP),
                    event.getImpactAutomationReliability().multiply(combinedMultiplier).setScale(2, RoundingMode.HALF_UP),
                    event.getImpactSlaCompliance().multiply(combinedMultiplier).setScale(2, RoundingMode.HALF_UP)
            );

            basestationService.updateMetrics(event.getBasestationId(), eventImpact);
        }
    }

    /**
     * Escalates unresolved events based on their severity rate.
     */
    private void escalateEvents(GameSession session, int currentTick) {
        List<GameEvent> unresolvedEvents = eventService.getUnresolvedEvents(session.getId());
        for (GameEvent event : unresolvedEvents) {
            if (shouldEscalate(event.getSeverity(), currentTick)) {
                eventService.escalateEvent(event.getId());
            }
        }
    }

    /**
     * Checks if any deployed rApps resolve events on their basestations.
     */
    private void checkEventResolution(GameSession session) {
        List<Basestation> basestations = getSessionBasestations(session.getId());
        for (Basestation bs : basestations) {
            List<GameEvent> resolvableEvents = eventService.checkEventResolution(bs.getId());
            for (GameEvent event : resolvableEvents) {
                eventService.resolveEvent(event.getId());
            }
        }
    }

    /**
     * Checks auto-resolve for events at max escalation level.
     */
    private void checkAutoResolve(GameSession session) {
        List<GameEvent> unresolvedEvents = eventService.getUnresolvedEvents(session.getId());
        for (GameEvent event : unresolvedEvents) {
            if (event.getEscalationLevel() == gameProperties.getEscalation().getMaxLevel()) {
                eventService.checkAutoResolve(event.getId());
            }
        }
    }

    /**
     * Gets all basestations belonging to a session (across all players).
     */
    private List<Basestation> getSessionBasestations(Long sessionId) {
        List<Player> players = playerRepository.findBySessionId(sessionId);
        List<Basestation> allBasestations = new ArrayList<>();
        for (Player player : players) {
            allBasestations.addAll(basestationRepository.findByPlayerId(player.getId()));
        }
        return allBasestations;
    }

    // --- Helper methods ---

    BigDecimal getEscalationMultiplier(int escalationLevel) {
        return switch (escalationLevel) {
            case 0 -> BigDecimal.ONE;
            case 1 -> new BigDecimal("1.5");
            case 2 -> new BigDecimal("2.0");
            case 3 -> new BigDecimal("3.0");
            default -> new BigDecimal("3.0");
        };
    }

    BigDecimal getSeverityMultiplier(EventSeverity severity) {
        return switch (severity) {
            case LOW -> BigDecimal.ONE;
            case MEDIUM -> new BigDecimal("1.5");
            case HIGH -> new BigDecimal("2.0");
            case CRITICAL -> new BigDecimal("3.0");
        };
    }

    boolean shouldEscalate(EventSeverity severity, int currentTick) {
        return switch (severity) {
            case LOW -> currentTick % 3 == 0;
            case MEDIUM -> currentTick % 2 == 0;
            case HIGH, CRITICAL -> true;
        };
    }

    private Aggressiveness parseAggressiveness(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return Aggressiveness.MODERATE;
        }
        String upper = configuration.toUpperCase();
        if (upper.contains("\"HIGH\"")) {
            return Aggressiveness.HIGH;
        } else if (upper.contains("\"LOW\"")) {
            return Aggressiveness.LOW;
        }
        return Aggressiveness.MODERATE;
    }
}
