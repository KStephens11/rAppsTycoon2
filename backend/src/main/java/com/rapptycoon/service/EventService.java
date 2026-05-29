package com.rapptycoon.service;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.dto.ImpactDto;
import com.rapptycoon.exception.InvalidStateException;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EventService {

    private static final BigDecimal AUTO_RESOLVE_DAMAGE = new BigDecimal("-10.00");

    /**
     * Maps event types to the rApp template names that can resolve them.
     */
    private static final Map<String, List<String>> EVENT_RAPP_EFFECTIVENESS = Map.of(
            "POWER_OUTAGE", List.of("Energy Saver", "Fault Predictor"),
            "TRAFFIC_SPIKE", List.of("Capacity Optimiser", "Traffic Balancer"),
            "HARDWARE_FAILURE", List.of("Fault Predictor", "Configuration Drift Detector"),
            "SLA_BREACH", List.of("SLA Guardian", "Capacity Optimiser"),
            "INTERFERENCE", List.of("Traffic Balancer", "Alarm Noise Reducer"),
            "CAPACITY_OVERFLOW", List.of("Capacity Optimiser", "Traffic Balancer")
    );

    private final GameEventRepository gameEventRepository;
    private final GameSessionRepository gameSessionRepository;
    private final BasestationRepository basestationRepository;
    private final RappDeploymentRepository rappDeploymentRepository;
    private final RappTemplateRepository rappTemplateRepository;
    private final GameProperties gameProperties;

    public EventService(GameEventRepository gameEventRepository,
                        GameSessionRepository gameSessionRepository,
                        BasestationRepository basestationRepository,
                        RappDeploymentRepository rappDeploymentRepository,
                        RappTemplateRepository rappTemplateRepository,
                        GameProperties gameProperties) {
        this.gameEventRepository = gameEventRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.basestationRepository = basestationRepository;
        this.rappDeploymentRepository = rappDeploymentRepository;
        this.rappTemplateRepository = rappTemplateRepository;
        this.gameProperties = gameProperties;
    }

    /**
     * Creates a new game event on a basestation within a session.
     */
    @Transactional
    public GameEvent createEvent(String sessionCode, Long basestationId, String eventType,
                                 String severity, String description, ImpactDto impact) {
        GameSession session = gameSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionCode));

        if (session.getState() != GameSessionState.ACTIVE) {
            throw new InvalidStateException("Session is not active");
        }

        Basestation basestation = basestationRepository.findById(basestationId)
                .orElseThrow(() -> new EntityNotFoundException("Basestation not found: " + basestationId));

        GameEvent event = GameEvent.builder()
                .sessionId(session.getId())
                .basestationId(basestationId)
                .eventType(eventType)
                .severity(EventSeverity.valueOf(severity))
                .description(description)
                .impactHealth(impact != null && impact.health() != null ? impact.health() : BigDecimal.ZERO)
                .impactCustomerExperience(impact != null && impact.customerExperience() != null ? impact.customerExperience() : BigDecimal.ZERO)
                .impactCost(impact != null && impact.cost() != null ? impact.cost() : BigDecimal.ZERO)
                .impactEnergyEfficiency(impact != null && impact.energyEfficiency() != null ? impact.energyEfficiency() : BigDecimal.ZERO)
                .impactAutomationReliability(impact != null && impact.automationReliability() != null ? impact.automationReliability() : BigDecimal.ZERO)
                .impactSlaCompliance(impact != null && impact.slaCompliance() != null ? impact.slaCompliance() : BigDecimal.ZERO)
                .resolved(false)
                .escalationLevel(0)
                .ticksAtMaxEscalation(0)
                .createdAt(LocalDateTime.now())
                .build();

        return gameEventRepository.save(event);
    }

    /**
     * Resolves an event by marking it as resolved with a timestamp.
     */
    @Transactional
    public GameEvent resolveEvent(Long eventId) {
        GameEvent event = gameEventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        event.setResolved(true);
        event.setResolvedAt(LocalDateTime.now());
        return gameEventRepository.save(event);
    }

    /**
     * Escalates an event. If below max level, increments escalation level.
     * If already at max level, increments ticksAtMaxEscalation.
     */
    @Transactional
    public GameEvent escalateEvent(Long eventId) {
        GameEvent event = gameEventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        if (event.isResolved()) {
            throw new InvalidStateException("Cannot escalate a resolved event");
        }

        int maxLevel = gameProperties.getEscalation().getMaxLevel();

        if (event.getEscalationLevel() < maxLevel) {
            event.setEscalationLevel(event.getEscalationLevel() + 1);
        } else {
            event.setTicksAtMaxEscalation(event.getTicksAtMaxEscalation() + 1);
        }

        return gameEventRepository.save(event);
    }

    /**
     * Checks if an event should auto-resolve (at max escalation for autoResolveAfter ticks).
     * If so, resolves the event and applies permanent -10 damage to affected metrics.
     *
     * @return true if the event was auto-resolved
     */
    @Transactional
    public boolean checkAutoResolve(Long eventId) {
        GameEvent event = gameEventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        int maxLevel = gameProperties.getEscalation().getMaxLevel();
        int autoResolveAfter = gameProperties.getEscalation().getAutoResolveAfter();

        if (event.getEscalationLevel() == maxLevel && event.getTicksAtMaxEscalation() >= autoResolveAfter) {
            // Auto-resolve with permanent damage
            event.setResolved(true);
            event.setResolvedAt(LocalDateTime.now());
            gameEventRepository.save(event);

            // Apply permanent -10 damage to affected basestation metrics
            Basestation basestation = basestationRepository.findById(event.getBasestationId())
                    .orElseThrow(() -> new EntityNotFoundException("Basestation not found: " + event.getBasestationId()));

            applyAutoResolveDamage(basestation);
            basestationRepository.save(basestation);

            return true;
        }

        return false;
    }

    /**
     * Returns all unresolved events for a session.
     */
    @Transactional(readOnly = true)
    public List<GameEvent> getUnresolvedEvents(Long sessionId) {
        return gameEventRepository.findBySessionIdAndResolvedFalse(sessionId);
    }

    /**
     * Checks if any deployed rApps on a basestation are effective against its unresolved events.
     * Returns the list of events that should be resolved (effective rApp is deployed).
     */
    @Transactional(readOnly = true)
    public List<GameEvent> checkEventResolution(Long basestationId) {
        List<GameEvent> unresolvedEvents = gameEventRepository.findByBasestationIdAndResolvedFalse(basestationId);
        if (unresolvedEvents.isEmpty()) {
            return Collections.emptyList();
        }

        // Get active rApp deployments on this basestation
        List<RappDeployment> activeDeployments = rappDeploymentRepository
                .findByBasestationIdAndStatus(basestationId, DeploymentStatus.ACTIVE);

        if (activeDeployments.isEmpty()) {
            return Collections.emptyList();
        }

        // Get template names for active deployments
        Set<String> activeRappNames = new HashSet<>();
        for (RappDeployment deployment : activeDeployments) {
            rappTemplateRepository.findById(deployment.getTemplateId())
                    .ifPresent(template -> activeRappNames.add(template.getName()));
        }

        // Check which events can be resolved
        List<GameEvent> resolvableEvents = new ArrayList<>();
        for (GameEvent event : unresolvedEvents) {
            List<String> effectiveRapps = EVENT_RAPP_EFFECTIVENESS.getOrDefault(event.getEventType(), Collections.emptyList());
            for (String effectiveRapp : effectiveRapps) {
                if (activeRappNames.contains(effectiveRapp)) {
                    resolvableEvents.add(event);
                    break;
                }
            }
        }

        return resolvableEvents;
    }

    /**
     * Returns the effectiveness mapping (event type → effective rApp names).
     */
    public static Map<String, List<String>> getEventRappEffectiveness() {
        return EVENT_RAPP_EFFECTIVENESS;
    }

    private void applyAutoResolveDamage(Basestation basestation) {
        basestation.setHealth(clampPercentage(basestation.getHealth().add(AUTO_RESOLVE_DAMAGE)));
        basestation.setCustomerExperience(clampPercentage(basestation.getCustomerExperience().add(AUTO_RESOLVE_DAMAGE)));
        basestation.setEnergyEfficiency(clampPercentage(basestation.getEnergyEfficiency().add(AUTO_RESOLVE_DAMAGE)));
        basestation.setAutomationReliability(clampPercentage(basestation.getAutomationReliability().add(AUTO_RESOLVE_DAMAGE)));
        basestation.setSlaCompliance(clampPercentage(basestation.getSlaCompliance().add(AUTO_RESOLVE_DAMAGE)));
    }

    private BigDecimal clampPercentage(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(new BigDecimal("100.00")) > 0) {
            return new BigDecimal("100.00");
        }
        return value;
    }
}
