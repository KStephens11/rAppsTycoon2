package com.rapptycoon.repository;

import com.rapptycoon.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RappDeploymentRepositoryTest {

    @Autowired
    private RappDeploymentRepository rappDeploymentRepository;

    @Autowired
    private BasestationRepository basestationRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private RappTemplateRepository rappTemplateRepository;

    private Long playerId;
    private Long basestationId;
    private Long templateId;

    @BeforeEach
    void setUp() {
        GameSession session = GameSession.builder()
                .sessionCode("DEPL0001")
                .state(GameSessionState.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        Long sessionId = gameSessionRepository.save(session).getId();

        Player player = Player.builder()
                .sessionId(sessionId)
                .displayName("Deployer")
                .sessionToken("token-deployer-001")
                .build();
        playerId = playerRepository.save(player).getId();

        Basestation bs = Basestation.builder()
                .playerId(playerId)
                .name("Deploy Tower")
                .positionX(1)
                .positionY(1)
                .build();
        basestationId = basestationRepository.save(bs).getId();

        RappTemplate template = RappTemplate.builder()
                .name("Test rApp")
                .purpose("Testing")
                .cost(new BigDecimal("50.00"))
                .benefit("Improves health")
                .risk(new BigDecimal("0.10"))
                .confidence(new BigDecimal("0.90"))
                .build();
        templateId = rappTemplateRepository.save(template).getId();
    }

    @Test
    void saveAndFindById() {
        RappDeployment deployment = RappDeployment.builder()
                .templateId(templateId)
                .basestationId(basestationId)
                .playerId(playerId)
                .status(DeploymentStatus.DEPLOYING)
                .deployedAt(LocalDateTime.now())
                .build();

        RappDeployment saved = rappDeploymentRepository.save(deployment);

        Optional<RappDeployment> found = rappDeploymentRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(DeploymentStatus.DEPLOYING);
        assertThat(found.get().getVersion()).isEqualTo(1);
    }

    @Test
    void findByBasestationIdReturnsDeploymentsForBasestation() {
        RappDeployment dep1 = RappDeployment.builder()
                .templateId(templateId)
                .basestationId(basestationId)
                .playerId(playerId)
                .status(DeploymentStatus.ACTIVE)
                .deployedAt(LocalDateTime.now())
                .build();
        RappDeployment dep2 = RappDeployment.builder()
                .templateId(templateId)
                .basestationId(basestationId)
                .playerId(playerId)
                .status(DeploymentStatus.DEPLOYING)
                .deployedAt(LocalDateTime.now())
                .build();
        rappDeploymentRepository.save(dep1);
        rappDeploymentRepository.save(dep2);

        List<RappDeployment> deployments = rappDeploymentRepository.findByBasestationId(basestationId);
        assertThat(deployments).hasSize(2);
    }

    @Test
    void findByPlayerIdAndStatusFiltersCorrectly() {
        RappDeployment active = RappDeployment.builder()
                .templateId(templateId)
                .basestationId(basestationId)
                .playerId(playerId)
                .status(DeploymentStatus.ACTIVE)
                .deployedAt(LocalDateTime.now())
                .build();
        RappDeployment deploying = RappDeployment.builder()
                .templateId(templateId)
                .basestationId(basestationId)
                .playerId(playerId)
                .status(DeploymentStatus.DEPLOYING)
                .deployedAt(LocalDateTime.now())
                .build();
        RappDeployment disabled = RappDeployment.builder()
                .templateId(templateId)
                .basestationId(basestationId)
                .playerId(playerId)
                .status(DeploymentStatus.DISABLED)
                .deployedAt(LocalDateTime.now())
                .build();
        rappDeploymentRepository.save(active);
        rappDeploymentRepository.save(deploying);
        rappDeploymentRepository.save(disabled);

        List<RappDeployment> activeDeployments = rappDeploymentRepository
                .findByPlayerIdAndStatus(playerId, DeploymentStatus.ACTIVE);
        assertThat(activeDeployments).hasSize(1);
        assertThat(activeDeployments.get(0).getStatus()).isEqualTo(DeploymentStatus.ACTIVE);

        List<RappDeployment> deployingDeployments = rappDeploymentRepository
                .findByPlayerIdAndStatus(playerId, DeploymentStatus.DEPLOYING);
        assertThat(deployingDeployments).hasSize(1);
    }
}
