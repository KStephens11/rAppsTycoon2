package com.rapptycoon.service;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.dto.*;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameEventRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.repository.RappDeploymentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class BasestationService {

    private static final String[] BASESTATION_NAMES = {
            "BS-Alpha", "BS-Beta", "BS-Gamma", "BS-Delta", "BS-Epsilon", "BS-Zeta",
            "BS-Eta", "BS-Theta", "BS-Iota", "BS-Kappa", "BS-Lambda", "BS-Mu",
            "BS-Nu", "BS-Xi", "BS-Omicron", "BS-Pi", "BS-Rho", "BS-Sigma"
    };

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final BasestationRepository basestationRepository;
    private final PlayerRepository playerRepository;
    private final RappDeploymentRepository rappDeploymentRepository;
    private final GameEventRepository gameEventRepository;
    private final GameProperties gameProperties;

    public BasestationService(BasestationRepository basestationRepository,
                              PlayerRepository playerRepository,
                              RappDeploymentRepository rappDeploymentRepository,
                              GameEventRepository gameEventRepository,
                              GameProperties gameProperties) {
        this.basestationRepository = basestationRepository;
        this.playerRepository = playerRepository;
        this.rappDeploymentRepository = rappDeploymentRepository;
        this.gameEventRepository = gameEventRepository;
        this.gameProperties = gameProperties;
    }

    /**
     * Assigns basestations to all players in a session.
     * Each player gets {@code basestations.perPlayer} (default 3) basestations
     * with unique names and spread positions.
     */
    @Transactional
    public List<Basestation> assignBasestations(Long sessionId) {
        List<Player> players = playerRepository.findBySessionId(sessionId);
        int perPlayer = gameProperties.getBasestations().getPerPlayer();
        List<Basestation> allBasestations = new ArrayList<>();

        int nameIndex = 0;
        for (int playerIdx = 0; playerIdx < players.size(); playerIdx++) {
            Player player = players.get(playerIdx);
            int baseX = 100 + (playerIdx * 300);

            for (int bsIdx = 0; bsIdx < perPlayer; bsIdx++) {
                String name = BASESTATION_NAMES[nameIndex % BASESTATION_NAMES.length];
                nameIndex++;

                int posX = baseX + (bsIdx * 100);
                int posY = 150 + (bsIdx * 100);

                Basestation basestation = Basestation.builder()
                        .playerId(player.getId())
                        .name(name)
                        .positionX(posX)
                        .positionY(posY)
                        .health(new BigDecimal("100.00"))
                        .customerExperience(new BigDecimal("100.00"))
                        .cost(new BigDecimal("0.00"))
                        .energyEfficiency(new BigDecimal("100.00"))
                        .automationReliability(new BigDecimal("100.00"))
                        .slaCompliance(new BigDecimal("100.00"))
                        .build();

                allBasestations.add(basestation);
            }
        }

        return basestationRepository.saveAll(allBasestations);
    }

    /**
     * Returns all basestations for a player with their current metrics,
     * deployed rApps, and active (unresolved) events.
     */
    @Transactional(readOnly = true)
    public List<BasestationStateDto> getPlayerBasestations(Long playerId) {
        List<Basestation> basestations = basestationRepository.findByPlayerId(playerId);

        return basestations.stream()
                .map(bs -> {
                    List<RappDeployment> deployments = rappDeploymentRepository.findByBasestationId(bs.getId());
                    List<GameEvent> events = gameEventRepository.findByBasestationIdAndResolvedFalse(bs.getId());

                    List<DeployedRappDto> deployedRapps = deployments.stream()
                            .map(d -> new DeployedRappDto(
                                    d.getId(),
                                    d.getTemplateId(),
                                    d.getStatus().name(),
                                    d.getVersion(),
                                    d.getDeployedAt()
                            ))
                            .toList();

                    List<ActiveEventDto> activeEvents = events.stream()
                            .map(e -> new ActiveEventDto(
                                    e.getId(),
                                    e.getEventType(),
                                    e.getSeverity().name(),
                                    e.getDescription(),
                                    e.getEscalationLevel(),
                                    e.getCreatedAt()
                            ))
                            .toList();

                    MetricsDto metrics = new MetricsDto(
                            bs.getHealth(),
                            bs.getCustomerExperience(),
                            bs.getCost(),
                            bs.getEnergyEfficiency(),
                            bs.getAutomationReliability(),
                            bs.getSlaCompliance()
                    );

                    return new BasestationStateDto(
                            bs.getId(),
                            bs.getName(),
                            bs.getPositionX(),
                            bs.getPositionY(),
                            metrics,
                            deployedRapps,
                            activeEvents
                    );
                })
                .toList();
    }

    /**
     * Applies metric deltas to a basestation with clamping:
     * - health, customerExperience, energyEfficiency, automationReliability, slaCompliance: [0, 100]
     * - cost: [0, unlimited] (no upper limit, but cannot go negative)
     */
    @Transactional
    public Basestation updateMetrics(Long basestationId, MetricDeltas deltas) {
        Basestation bs = basestationRepository.findById(basestationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Basestation not found with id: " + basestationId));

        bs.setHealth(clampPercentage(bs.getHealth().add(deltas.health())));
        bs.setCustomerExperience(clampPercentage(bs.getCustomerExperience().add(deltas.customerExperience())));
        bs.setCost(clampMinZero(bs.getCost().add(deltas.cost())));
        bs.setEnergyEfficiency(clampPercentage(bs.getEnergyEfficiency().add(deltas.energyEfficiency())));
        bs.setAutomationReliability(clampPercentage(bs.getAutomationReliability().add(deltas.automationReliability())));
        bs.setSlaCompliance(clampPercentage(bs.getSlaCompliance().add(deltas.slaCompliance())));

        return basestationRepository.save(bs);
    }

    private BigDecimal clampPercentage(BigDecimal value) {
        if (value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (value.compareTo(HUNDRED) > 0) {
            return HUNDRED;
        }
        return value;
    }

    private BigDecimal clampMinZero(BigDecimal value) {
        if (value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        return value;
    }
}
