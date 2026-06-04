package com.rapptycoon.service;

import com.rapptycoon.model.*;
import com.rapptycoon.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class InProcessBotPlayer {

    private static final Logger log = LoggerFactory.getLogger(InProcessBotPlayer.class);

    private static final Map<String, List<Long>> EVENT_RESOLUTION_MAP = Map.of(
            "POWER_OUTAGE", List.of(1L, 3L),
            "TRAFFIC_SPIKE", List.of(2L, 6L),
            "HARDWARE_FAILURE", List.of(3L, 5L),
            "SLA_BREACH", List.of(4L, 2L),
            "INTERFERENCE", List.of(6L, 7L),
            "CAPACITY_OVERFLOW", List.of(2L, 6L)
    );

    private static final List<long[]> CONFLICT_PAIRS = List.of(
            new long[]{1L, 2L},
            new long[]{3L, 7L},
            new long[]{6L, 1L}
    );

    private static final BigDecimal MINIMUM_SAFE_BALANCE = new BigDecimal("200.00");

    /**
     * Sanitizes a value for safe logging by replacing newlines and control characters.
     * Prevents log injection (Sonar javasecurity:S5145).
     */
    private static String sanitize(String value) {
        if (value == null) return "null";
        return value.replaceAll("[\\r\\n\\t]", "_");
    }

    private final PlayerRepository playerRepository;
    private final BasestationRepository basestationRepository;
    private final RappDeploymentRepository rappDeploymentRepository;
    private final GameEventRepository gameEventRepository;
    private final RappTemplateRepository rappTemplateRepository;
    private final RappService rappService;
    private SplittableRandom random;

    @Autowired
    public InProcessBotPlayer(PlayerRepository playerRepository,
                              BasestationRepository basestationRepository,
                              RappDeploymentRepository rappDeploymentRepository,
                              GameEventRepository gameEventRepository,
                              RappTemplateRepository rappTemplateRepository,
                              RappService rappService) {
        this.playerRepository = playerRepository;
        this.basestationRepository = basestationRepository;
        this.rappDeploymentRepository = rappDeploymentRepository;
        this.gameEventRepository = gameEventRepository;
        this.rappTemplateRepository = rappTemplateRepository;
        this.rappService = rappService;
        this.random = new SplittableRandom();
    }

    /** Package-private for testing — allows injecting a seeded random source. */
    void setRandom(SplittableRandom random) {
        this.random = random;
    }

    @Transactional
    public void executeBotActions(Long sessionId, String sessionCode) {
        List<Player> botPlayers = playerRepository.findBySessionId(sessionId)
                .stream()
                .filter(Player::isBot)
                .toList();

        if (botPlayers.isEmpty()) return;

        for (Player bot : botPlayers) {
            try {
                executeSingleBotAction(bot, sessionCode);
            } catch (Exception e) {
                log.warn("Error executing bot action for '{}': {}", sanitize(bot.getDisplayName()), sanitize(e.getMessage()));
            }
        }
    }

    private void executeSingleBotAction(Player bot, String sessionCode) {
        List<Basestation> basestations = basestationRepository.findByPlayerId(bot.getId());
        BigDecimal money = bot.getScoreMoney();

        String difficulty = bot.getDifficulty();
        double actionChance = switch (difficulty) {
            case "EASY" -> 0.30;
            case "HARD" -> 0.75;
            default -> 0.50;
        };

        if (random.nextDouble() > actionChance) return;

        for (Basestation bs : basestations) {
            List<GameEvent> activeEvents = gameEventRepository.findByBasestationIdAndResolvedFalse(bs.getId());

            for (GameEvent event : activeEvents) {
                List<Long> effectiveRapps = EVENT_RESOLUTION_MAP.getOrDefault(event.getEventType(), List.of());

                for (Long templateId : effectiveRapps) {
                    if (isAlreadyDeployed(bs.getId(), templateId)) continue;
                    if (wouldCreateConflict(bs.getId(), templateId)) continue;

                    RappTemplate template = rappTemplateRepository.findById(templateId).orElse(null);
                    if (template == null) continue;
                    if (money.compareTo(template.getCost()) < 0) continue;

                    boolean wouldDropBelowSafeBalance =
                            money.subtract(template.getCost()).compareTo(MINIMUM_SAFE_BALANCE) < 0;

                    boolean eventIsUrgent =
                            event.getSeverity() == EventSeverity.HIGH ||
                            event.getSeverity() == EventSeverity.CRITICAL;

                    if (wouldDropBelowSafeBalance && !eventIsUrgent) continue;

                    try {
                        rappService.deploy(sessionCode, bot.getSessionToken(), templateId, bs.getId());
                        log.info("Bot '{}' deployed {} to {} to resolve {}",
                                sanitize(bot.getDisplayName()),
                                template.getName(),
                                bs.getName(),
                                event.getEventType());
                        return;
                    } catch (Exception e) {
                        log.debug("Bot '{}' failed to deploy: {}", sanitize(bot.getDisplayName()), sanitize(e.getMessage()));
                    }
                }
            }
        }

        if (money.compareTo(new BigDecimal("300.00")) > 0) {
            Basestation worstBs = findWorstBasestation(basestations);

            if (worstBs != null) {
                Long bestTemplate = findBestMetricImprover(worstBs);

                if (bestTemplate != null) {
                    RappTemplate template = rappTemplateRepository.findById(bestTemplate).orElse(null);

                    if (template != null && money.compareTo(template.getCost()) >= 0) {
                        try {
                            rappService.deploy(sessionCode, bot.getSessionToken(), bestTemplate, worstBs.getId());
                            log.info("Bot '{}' deployed {} to {} for metric improvement",
                                    sanitize(bot.getDisplayName()),
                                    template.getName(),
                                    worstBs.getName());
                        } catch (Exception e) {
                            log.debug("Bot '{}' preventative deploy failed: {}", sanitize(bot.getDisplayName()), sanitize(e.getMessage()));
                        }
                    }
                }
            }
        }
    }

    private boolean isAlreadyDeployed(Long basestationId, Long templateId) {
        boolean active = rappDeploymentRepository
                .findByBasestationIdAndStatus(basestationId, DeploymentStatus.ACTIVE)
                .stream()
                .anyMatch(d -> d.getTemplateId().equals(templateId));

        boolean deploying = rappDeploymentRepository
                .findByBasestationIdAndStatus(basestationId, DeploymentStatus.DEPLOYING)
                .stream()
                .anyMatch(d -> d.getTemplateId().equals(templateId));

        return active || deploying;
    }

    private boolean wouldCreateConflict(Long basestationId, Long candidateTemplateId) {
        Set<Long> deployedTemplates = new HashSet<>();

        rappDeploymentRepository
                .findByBasestationIdAndStatus(basestationId, DeploymentStatus.ACTIVE)
                .forEach(d -> deployedTemplates.add(d.getTemplateId()));

        for (long[] pair : CONFLICT_PAIRS) {
            if (candidateTemplateId.equals(pair[0]) && deployedTemplates.contains(pair[1])) return true;
            if (candidateTemplateId.equals(pair[1]) && deployedTemplates.contains(pair[0])) return true;
        }

        return false;
    }

    private Basestation findWorstBasestation(List<Basestation> basestations) {
        return basestations.stream()
                .min(Comparator.comparing(bs -> bs.getHealth()
                        .add(bs.getCustomerExperience())
                        .add(bs.getSlaCompliance())
                        .add(bs.getAutomationReliability())))
                .filter(bs -> {
                    BigDecimal avg = bs.getHealth()
                            .add(bs.getCustomerExperience())
                            .add(bs.getSlaCompliance())
                            .add(bs.getAutomationReliability())
                            .divide(new BigDecimal("4"), 2, java.math.RoundingMode.HALF_UP);

                    return avg.compareTo(new BigDecimal("85.00")) < 0;
                })
                .orElse(null);
    }

    private Long findBestMetricImprover(Basestation bs) {
        Set<Long> deployedTemplates = new HashSet<>();

        rappDeploymentRepository
                .findByBasestationIdAndStatus(bs.getId(), DeploymentStatus.ACTIVE)
                .forEach(d -> deployedTemplates.add(d.getTemplateId()));

        List<Long> candidates = List.of(5L, 4L, 3L, 7L, 6L, 2L, 1L);

        for (Long templateId : candidates) {
            if (deployedTemplates.contains(templateId)) continue;
            if (wouldCreateConflict(bs.getId(), templateId)) continue;
            return templateId;
        }

        return null;
    }
}