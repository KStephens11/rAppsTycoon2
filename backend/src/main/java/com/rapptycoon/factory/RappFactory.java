package com.rapptycoon.factory;

import com.rapptycoon.model.DeploymentStatus;
import com.rapptycoon.model.RappDeployment;
import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.repository.RappDeploymentRepository;
import com.rapptycoon.repository.RappTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RappFactory {

    private final RappTemplateRepository rappTemplateRepository;
    private final RappDeploymentRepository rappDeploymentRepository;

    public RappFactory(RappTemplateRepository rappTemplateRepository,
                       RappDeploymentRepository rappDeploymentRepository) {
        this.rappTemplateRepository = rappTemplateRepository;
        this.rappDeploymentRepository = rappDeploymentRepository;
    }

    public RappDeployment createDeployment(Long templateId, Long basestationId, Long playerId) {
        RappTemplate template = rappTemplateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("RappTemplate not found with id: " + templateId));

        RappDeployment deployment = RappDeployment.builder()
                .templateId(template.getId())
                .basestationId(basestationId)
                .playerId(playerId)
                .status(DeploymentStatus.DEPLOYING)
                .version(1)
                .deployedAt(LocalDateTime.now())
                .build();

        return rappDeploymentRepository.save(deployment);
    }
}
