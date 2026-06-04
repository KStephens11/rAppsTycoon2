package com.rapptycoon.service;

import com.rapptycoon.dto.ActiveEventDto;
import com.rapptycoon.dto.BasestationStateDto;
import com.rapptycoon.dto.DeployedRappDto;
import com.rapptycoon.dto.MetricsDto;
import com.rapptycoon.model.Basestation;
import com.rapptycoon.model.GameEvent;
import com.rapptycoon.model.RappDeployment;
import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.repository.GameEventRepository;
import com.rapptycoon.repository.RappDeploymentRepository;
import com.rapptycoon.repository.RappTemplateRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared mapper that converts Basestation entities (with their deployments and events)
 * into BasestationStateDto objects. Used by both PlayerService and BasestationService
 * to avoid duplicating the mapping logic.
 */
@Component
public class BasestationStateMapper {

    private final RappDeploymentRepository rappDeploymentRepository;
    private final GameEventRepository gameEventRepository;
    private final RappTemplateRepository rappTemplateRepository;

    public BasestationStateMapper(RappDeploymentRepository rappDeploymentRepository,
                                  GameEventRepository gameEventRepository,
                                  RappTemplateRepository rappTemplateRepository) {
        this.rappDeploymentRepository = rappDeploymentRepository;
        this.gameEventRepository = gameEventRepository;
        this.rappTemplateRepository = rappTemplateRepository;
    }

    /**
     * Maps a list of basestations to their full DTO representation,
     * including deployed rApps, active events, and current metrics.
     */
    public List<BasestationStateDto> toStateDtos(List<Basestation> basestations) {
        return basestations.stream()
                .map(this::toStateDto)
                .toList();
    }

    /**
     * Maps a single basestation to its full DTO representation.
     */
    public BasestationStateDto toStateDto(Basestation bs) {
        List<RappDeployment> deployments = rappDeploymentRepository.findByBasestationId(bs.getId());
        List<GameEvent> events = gameEventRepository.findByBasestationIdAndResolvedFalse(bs.getId());

        List<DeployedRappDto> deployedRapps = deployments.stream()
                .map(this::toDeployedRappDto)
                .toList();

        List<ActiveEventDto> activeEvents = events.stream()
                .map(this::toActiveEventDto)
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
    }

    private DeployedRappDto toDeployedRappDto(RappDeployment d) {
        String rappName = rappTemplateRepository.findById(d.getTemplateId())
                .map(RappTemplate::getName)
                .orElse("Unknown rApp");
        return new DeployedRappDto(
                d.getId(),
                d.getTemplateId(),
                rappName,
                d.getStatus().name(),
                d.getVersion(),
                d.getDeployedAt()
        );
    }

    private ActiveEventDto toActiveEventDto(GameEvent e) {
        return new ActiveEventDto(
                e.getId(),
                e.getEventType(),
                e.getSeverity().name(),
                e.getDescription(),
                e.getEscalationLevel(),
                e.getCreatedAt()
        );
    }
}
