package com.rapptycoon.service;

import com.rapptycoon.dto.DeploymentResponse;
import com.rapptycoon.dto.MetricsDto;
import com.rapptycoon.exception.ForbiddenException;
import com.rapptycoon.exception.InvalidStateException;
import com.rapptycoon.factory.RappBehaviour;
import com.rapptycoon.factory.RappBehaviourRegistry;
import com.rapptycoon.factory.RappFactory;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.repository.RappDeploymentRepository;
import com.rapptycoon.repository.RappTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RappService {

    private static final String DEFAULT_CONFIGURATION = "{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}";

    /**
     * Conflicting rApp pairs and their penalties.
     * Each entry: Set of two rApp names -> MetricDeltas penalty.
     */
    private static final List<ConflictRule> CONFLICT_RULES = List.of(
            new ConflictRule(
                    "Energy Saver", "Capacity Optimiser",
                    new MetricDeltas(
                            BigDecimal.ZERO,
                            new BigDecimal("-10.00"),
                            BigDecimal.ZERO,
                            new BigDecimal("-5.00"),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO
                    )
            ),
            new ConflictRule(
                    "Fault Predictor", "Alarm Noise Reducer",
                    new MetricDeltas(
                            new BigDecimal("-5.00"),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            new BigDecimal("-8.00"),
                            BigDecimal.ZERO
                    )
            ),
            new ConflictRule(
                    "Traffic Balancer", "Energy Saver",
                    new MetricDeltas(
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            new BigDecimal("15.00"),
                            new BigDecimal("-7.00"),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO
                    )
            )
    );

    private final RappDeploymentRepository rappDeploymentRepository;
    private final RappTemplateRepository rappTemplateRepository;
    private final BasestationRepository basestationRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final BasestationService basestationService;
    private final RappFactory rappFactory;
    private final RappBehaviourRegistry rappBehaviourRegistry;

    public RappService(RappDeploymentRepository rappDeploymentRepository,
                       RappTemplateRepository rappTemplateRepository,
                       BasestationRepository basestationRepository,
                       PlayerRepository playerRepository,
                       PlayerService playerService,
                       BasestationService basestationService,
                       RappFactory rappFactory,
                       RappBehaviourRegistry rappBehaviourRegistry) {
        this.rappDeploymentRepository = rappDeploymentRepository;
        this.rappTemplateRepository = rappTemplateRepository;
        this.basestationRepository = basestationRepository;
        this.playerRepository = playerRepository;
        this.playerService = playerService;
        this.basestationService = basestationService;
        this.rappFactory = rappFactory;
        this.rappBehaviourRegistry = rappBehaviourRegistry;
    }

    /**
     * Deploys an rApp to a basestation.
     * Validates ownership, deducts cost, creates deployment in DEPLOYING status.
     */
    @Transactional
    public DeploymentResponse deploy(String code, String token, Long templateId, Long basestationId) {
        Player player = playerService.validateToken(token);

        Basestation basestation = basestationRepository.findById(basestationId)
                .orElseThrow(() -> new EntityNotFoundException("Basestation not found with id: " + basestationId));

        if (!basestation.getPlayerId().equals(player.getId())) {
            throw new ForbiddenException("Player does not own this basestation");
        }

        RappTemplate template = rappTemplateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("RappTemplate not found with id: " + templateId));

        // Deduct cost from player money
        player.setScoreMoney(player.getScoreMoney().subtract(template.getCost()));
        playerRepository.save(player);

        // Create deployment via factory
        RappDeployment deployment = rappFactory.createDeployment(templateId, basestationId, player.getId());
        deployment.setConfiguration(DEFAULT_CONFIGURATION);
        deployment = rappDeploymentRepository.save(deployment);

        return toDeploymentResponse(deployment, template.getName(), null);
    }

    /**
     * Activates a deployment: transitions DEPLOYING → ACTIVE, applies impact to basestation metrics.
     * Called by the tick engine after 1 tick.
     */
    @Transactional
    public DeploymentResponse activate(Long deploymentId) {
        RappDeployment deployment = rappDeploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found with id: " + deploymentId));

        if (deployment.getStatus() != DeploymentStatus.DEPLOYING) {
            throw new InvalidStateException("Cannot activate deployment that is not in DEPLOYING status");
        }

        deployment.setStatus(DeploymentStatus.ACTIVE);

        RappTemplate template = rappTemplateRepository.findById(deployment.getTemplateId())
                .orElseThrow(() -> new EntityNotFoundException("RappTemplate not found"));

        Aggressiveness aggressiveness = parseAggressiveness(deployment.getConfiguration());
        RappBehaviour behaviour = rappBehaviourRegistry.getBehaviour(template.getName());
        MetricDeltas impact = behaviour.calculateImpact(aggressiveness);

        Basestation updatedBs = basestationService.updateMetrics(deployment.getBasestationId(), impact);

        // Check for conflicts and apply penalties
        applyConflictPenalties(deployment, template.getName());

        deployment = rappDeploymentRepository.save(deployment);

        MetricsDto metricsDto = toMetricsDto(updatedBs);
        return toDeploymentResponse(deployment, template.getName(), metricsDto);
    }

    /**
     * Disables a deployed rApp: validates ownership, removes impact from metrics, sets DISABLED.
     */
    @Transactional
    public DeploymentResponse disable(String code, String token, Long deploymentId) {
        Player player = playerService.validateToken(token);

        RappDeployment deployment = rappDeploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found with id: " + deploymentId));

        if (!deployment.getPlayerId().equals(player.getId())) {
            throw new ForbiddenException("Player does not own this deployment");
        }

        if (deployment.getStatus() != DeploymentStatus.ACTIVE) {
            throw new InvalidStateException("Cannot disable deployment that is not ACTIVE");
        }

        RappTemplate template = rappTemplateRepository.findById(deployment.getTemplateId())
                .orElseThrow(() -> new EntityNotFoundException("RappTemplate not found"));

        // Calculate current impact and negate it
        Aggressiveness aggressiveness = parseAggressiveness(deployment.getConfiguration());
        RappBehaviour behaviour = rappBehaviourRegistry.getBehaviour(template.getName());
        MetricDeltas impact = behaviour.calculateImpact(aggressiveness);
        MetricDeltas negatedImpact = negateDeltas(impact);

        Basestation updatedBs = basestationService.updateMetrics(deployment.getBasestationId(), negatedImpact);

        deployment.setStatus(DeploymentStatus.DISABLED);
        deployment = rappDeploymentRepository.save(deployment);

        MetricsDto metricsDto = toMetricsDto(updatedBs);
        return toDeploymentResponse(deployment, template.getName(), metricsDto);
    }

    /**
     * Tunes a deployed rApp: validates ownership, increments version, recalculates impact.
     */
    @Transactional
    public DeploymentResponse tune(String code, String token, Long deploymentId, Integer threshold, String aggressivenessStr) {
        Player player = playerService.validateToken(token);

        RappDeployment deployment = rappDeploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found with id: " + deploymentId));

        if (!deployment.getPlayerId().equals(player.getId())) {
            throw new ForbiddenException("Player does not own this deployment");
        }

        if (deployment.getStatus() != DeploymentStatus.ACTIVE) {
            throw new InvalidStateException("Cannot tune deployment that is not ACTIVE");
        }

        RappTemplate template = rappTemplateRepository.findById(deployment.getTemplateId())
                .orElseThrow(() -> new EntityNotFoundException("RappTemplate not found"));

        RappBehaviour behaviour = rappBehaviourRegistry.getBehaviour(template.getName());

        // Calculate old impact
        Aggressiveness oldAggressiveness = parseAggressiveness(deployment.getConfiguration());
        MetricDeltas oldImpact = behaviour.calculateImpact(oldAggressiveness);

        // Store previous config for rollback
        deployment.setPreviousConfiguration(deployment.getConfiguration());

        // Update configuration
        Aggressiveness newAggressiveness = Aggressiveness.valueOf(aggressivenessStr);
        String newConfig = String.format("{\"threshold\":%d,\"aggressiveness\":\"%s\"}", threshold, aggressivenessStr);
        deployment.setConfiguration(newConfig);
        deployment.setVersion(deployment.getVersion() + 1);

        // Calculate new impact
        MetricDeltas newImpact = behaviour.calculateImpact(newAggressiveness);

        // Apply difference (remove old, add new)
        MetricDeltas diff = subtractDeltas(newImpact, oldImpact);
        Basestation updatedBs = basestationService.updateMetrics(deployment.getBasestationId(), diff);

        deployment = rappDeploymentRepository.save(deployment);

        MetricsDto metricsDto = toMetricsDto(updatedBs);
        return toDeploymentResponse(deployment, template.getName(), metricsDto);
    }

    /**
     * Rolls back a deployed rApp to its previous version/config.
     */
    @Transactional
    public DeploymentResponse rollback(String code, String token, Long deploymentId) {
        Player player = playerService.validateToken(token);

        RappDeployment deployment = rappDeploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new EntityNotFoundException("Deployment not found with id: " + deploymentId));

        if (!deployment.getPlayerId().equals(player.getId())) {
            throw new ForbiddenException("Player does not own this deployment");
        }

        if (deployment.getVersion() <= 1) {
            throw new InvalidStateException("Cannot rollback deployment at version 1");
        }

        if (deployment.getStatus() != DeploymentStatus.ACTIVE) {
            throw new InvalidStateException("Cannot rollback deployment that is not ACTIVE");
        }

        RappTemplate template = rappTemplateRepository.findById(deployment.getTemplateId())
                .orElseThrow(() -> new EntityNotFoundException("RappTemplate not found"));

        RappBehaviour behaviour = rappBehaviourRegistry.getBehaviour(template.getName());

        // Calculate current impact
        Aggressiveness currentAggressiveness = parseAggressiveness(deployment.getConfiguration());
        MetricDeltas currentImpact = behaviour.calculateImpact(currentAggressiveness);

        // Revert to previous config
        String previousConfig = deployment.getPreviousConfiguration();
        deployment.setConfiguration(previousConfig);
        deployment.setPreviousConfiguration(null);
        deployment.setVersion(deployment.getVersion() - 1);

        // Calculate previous impact
        Aggressiveness previousAggressiveness = parseAggressiveness(previousConfig);
        MetricDeltas previousImpact = behaviour.calculateImpact(previousAggressiveness);

        // Apply difference
        MetricDeltas diff = subtractDeltas(previousImpact, currentImpact);
        Basestation updatedBs = basestationService.updateMetrics(deployment.getBasestationId(), diff);

        deployment = rappDeploymentRepository.save(deployment);

        MetricsDto metricsDto = toMetricsDto(updatedBs);
        return toDeploymentResponse(deployment, template.getName(), metricsDto);
    }

    /**
     * Detects conflicts between active rApps on the same basestation and applies penalties.
     */
    private void applyConflictPenalties(RappDeployment newDeployment, String newRappName) {
        List<RappDeployment> activeDeployments = rappDeploymentRepository
                .findByBasestationIdAndStatus(newDeployment.getBasestationId(), DeploymentStatus.ACTIVE);

        for (RappDeployment existing : activeDeployments) {
            if (existing.getId().equals(newDeployment.getId())) {
                continue;
            }
            RappTemplate existingTemplate = rappTemplateRepository.findById(existing.getTemplateId())
                    .orElse(null);
            if (existingTemplate == null) {
                continue;
            }

            String existingName = existingTemplate.getName();
            for (ConflictRule rule : CONFLICT_RULES) {
                if (rule.matches(newRappName, existingName)) {
                    basestationService.updateMetrics(newDeployment.getBasestationId(), rule.penalty());
                }
            }
        }
    }

    /**
     * Checks if a conflict exists between rApps on a basestation.
     * Returns the conflict penalty deltas if a conflict is found, null otherwise.
     */
    public MetricDeltas detectConflict(String rappName1, String rappName2) {
        for (ConflictRule rule : CONFLICT_RULES) {
            if (rule.matches(rappName1, rappName2)) {
                return rule.penalty();
            }
        }
        return null;
    }

    // --- Helper methods ---

    private Aggressiveness parseAggressiveness(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return Aggressiveness.MODERATE;
        }
        // Simple JSON parsing for aggressiveness field
        String upper = configuration.toUpperCase();
        if (upper.contains("\"HIGH\"")) {
            return Aggressiveness.HIGH;
        } else if (upper.contains("\"LOW\"")) {
            return Aggressiveness.LOW;
        }
        return Aggressiveness.MODERATE;
    }

    private MetricDeltas negateDeltas(MetricDeltas deltas) {
        return new MetricDeltas(
                deltas.health().negate(),
                deltas.customerExperience().negate(),
                deltas.cost().negate(),
                deltas.energyEfficiency().negate(),
                deltas.automationReliability().negate(),
                deltas.slaCompliance().negate()
        );
    }

    private MetricDeltas subtractDeltas(MetricDeltas newDeltas, MetricDeltas oldDeltas) {
        return new MetricDeltas(
                newDeltas.health().subtract(oldDeltas.health()),
                newDeltas.customerExperience().subtract(oldDeltas.customerExperience()),
                newDeltas.cost().subtract(oldDeltas.cost()),
                newDeltas.energyEfficiency().subtract(oldDeltas.energyEfficiency()),
                newDeltas.automationReliability().subtract(oldDeltas.automationReliability()),
                newDeltas.slaCompliance().subtract(oldDeltas.slaCompliance())
        );
    }

    private DeploymentResponse toDeploymentResponse(RappDeployment deployment, String name, MetricsDto metrics) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getTemplateId(),
                name,
                deployment.getBasestationId(),
                deployment.getStatus().name(),
                deployment.getVersion(),
                deployment.getConfiguration(),
                deployment.getDeployedAt(),
                metrics
        );
    }

    private MetricsDto toMetricsDto(Basestation bs) {
        return new MetricsDto(
                bs.getHealth(),
                bs.getCustomerExperience(),
                bs.getCost(),
                bs.getEnergyEfficiency(),
                bs.getAutomationReliability(),
                bs.getSlaCompliance()
        );
    }

    /**
     * Represents a conflict rule between two rApp names with an associated penalty.
     */
    private record ConflictRule(String rapp1, String rapp2, MetricDeltas penalty) {
        boolean matches(String name1, String name2) {
            return (rapp1.equals(name1) && rapp2.equals(name2))
                    || (rapp1.equals(name2) && rapp2.equals(name1));
        }
    }
}
