package com.rapptycoon.service;

import com.rapptycoon.dto.DeploymentResponse;
import com.rapptycoon.exception.ForbiddenException;
import com.rapptycoon.exception.InvalidStateException;
import com.rapptycoon.factory.EnergySaverBehaviour;
import com.rapptycoon.factory.RappBehaviourRegistry;
import com.rapptycoon.factory.RappFactory;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.repository.RappDeploymentRepository;
import com.rapptycoon.repository.RappTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RappServiceTest {

    @Mock
    private RappDeploymentRepository rappDeploymentRepository;
    @Mock
    private RappTemplateRepository rappTemplateRepository;
    @Mock
    private BasestationRepository basestationRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerService playerService;
    @Mock
    private BasestationService basestationService;
    @Mock
    private RappFactory rappFactory;
    @Mock
    private RappBehaviourRegistry rappBehaviourRegistry;

    @InjectMocks
    private RappService rappService;

    private Player player;
    private Basestation basestation;
    private RappTemplate energySaverTemplate;
    private RappDeployment deployment;

    @BeforeEach
    void setUp() {
        player = Player.builder()
                .id(1L)
                .sessionId(1L)
                .displayName("TestPlayer")
                .sessionToken("valid-token")
                .scoreMoney(new BigDecimal("1000.00"))
                .build();

        basestation = Basestation.builder()
                .id(10L)
                .playerId(1L)
                .name("BS-Alpha")
                .positionX(100)
                .positionY(200)
                .health(new BigDecimal("100.00"))
                .customerExperience(new BigDecimal("100.00"))
                .cost(new BigDecimal("0.00"))
                .energyEfficiency(new BigDecimal("100.00"))
                .automationReliability(new BigDecimal("100.00"))
                .slaCompliance(new BigDecimal("100.00"))
                .build();

        energySaverTemplate = RappTemplate.builder()
                .id(1L)
                .name("Energy Saver")
                .purpose("Reduces power consumption")
                .cost(new BigDecimal("50.00"))
                .benefit("Lowers energy costs")
                .risk(new BigDecimal("25.00"))
                .confidence(new BigDecimal("80.00"))
                .build();

        deployment = RappDeployment.builder()
                .id(100L)
                .templateId(1L)
                .basestationId(10L)
                .playerId(1L)
                .status(DeploymentStatus.DEPLOYING)
                .version(1)
                .configuration("{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}")
                .deployedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("deploy")
    class DeployTests {

        @Test
        @DisplayName("successfully creates deployment and deducts cost")
        void successfullyCreatesDeploymentAndDeductsCost() {
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(basestationRepository.findById(10L)).thenReturn(Optional.of(basestation));
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(energySaverTemplate));
            when(playerRepository.save(any(Player.class))).thenReturn(player);
            when(rappFactory.createDeployment(1L, 10L, 1L)).thenReturn(deployment);
            when(rappDeploymentRepository.save(any(RappDeployment.class))).thenReturn(deployment);

            DeploymentResponse response = rappService.deploy("ABCD1234", "valid-token", 1L, 10L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.status()).isEqualTo("DEPLOYING");
            assertThat(response.version()).isEqualTo(1);
            assertThat(response.updatedMetrics()).isNull();

            // Verify cost was deducted
            ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
            verify(playerRepository).save(playerCaptor.capture());
            assertThat(playerCaptor.getValue().getScoreMoney()).isEqualByComparingTo(new BigDecimal("950.00"));
        }

        @Test
        @DisplayName("throws ForbiddenException when player doesn't own basestation")
        void throwsWhenPlayerDoesNotOwnBasestation() {
            basestation.setPlayerId(99L); // Different player
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(basestationRepository.findById(10L)).thenReturn(Optional.of(basestation));

            assertThatThrownBy(() -> rappService.deploy("ABCD1234", "valid-token", 1L, 10L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("does not own");
        }

        @Test
        @DisplayName("throws EntityNotFoundException when template not found")
        void throwsWhenTemplateNotFound() {
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(basestationRepository.findById(10L)).thenReturn(Optional.of(basestation));
            when(rappTemplateRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rappService.deploy("ABCD1234", "valid-token", 999L, 10L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("RappTemplate not found");
        }
    }

    @Nested
    @DisplayName("activate")
    class ActivateTests {

        @Test
        @DisplayName("transitions DEPLOYING → ACTIVE and applies impact")
        void transitionsDeployingToActiveAndAppliesImpact() {
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(energySaverTemplate));
            when(rappBehaviourRegistry.getBehaviour("Energy Saver")).thenReturn(new EnergySaverBehaviour());
            when(basestationService.updateMetrics(eq(10L), any(MetricDeltas.class))).thenReturn(basestation);
            when(rappDeploymentRepository.findByBasestationIdAndStatus(10L, DeploymentStatus.ACTIVE))
                    .thenReturn(List.of());
            when(rappDeploymentRepository.save(any(RappDeployment.class))).thenAnswer(inv -> inv.getArgument(0));

            DeploymentResponse response = rappService.activate(100L);

            assertThat(response.status()).isEqualTo("ACTIVE");
            verify(basestationService).updateMetrics(eq(10L), any(MetricDeltas.class));
        }

        @Test
        @DisplayName("throws InvalidStateException when not in DEPLOYING status")
        void throwsWhenNotDeploying() {
            deployment.setStatus(DeploymentStatus.ACTIVE);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));

            assertThatThrownBy(() -> rappService.activate(100L))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("not in DEPLOYING status");
        }
    }

    @Nested
    @DisplayName("disable")
    class DisableTests {

        @Test
        @DisplayName("removes impact and sets DISABLED")
        void removesImpactAndSetsDisabled() {
            deployment.setStatus(DeploymentStatus.ACTIVE);
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(energySaverTemplate));
            when(rappBehaviourRegistry.getBehaviour("Energy Saver")).thenReturn(new EnergySaverBehaviour());
            when(basestationService.updateMetrics(eq(10L), any(MetricDeltas.class))).thenReturn(basestation);
            when(rappDeploymentRepository.save(any(RappDeployment.class))).thenAnswer(inv -> inv.getArgument(0));

            DeploymentResponse response = rappService.disable("ABCD1234", "valid-token", 100L);

            assertThat(response.status()).isEqualTo("DISABLED");

            // Verify negated impact was applied
            ArgumentCaptor<MetricDeltas> deltasCaptor = ArgumentCaptor.forClass(MetricDeltas.class);
            verify(basestationService).updateMetrics(eq(10L), deltasCaptor.capture());
            MetricDeltas applied = deltasCaptor.getValue();
            // Energy Saver with MODERATE: energyEfficiency = +20, so negated = -20
            assertThat(applied.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-20.00"));
            // customerExperience = -5, so negated = +5
            assertThat(applied.customerExperience()).isEqualByComparingTo(new BigDecimal("5.00"));
        }

        @Test
        @DisplayName("throws InvalidStateException when not ACTIVE")
        void throwsWhenNotActive() {
            deployment.setStatus(DeploymentStatus.DEPLOYING);
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));

            assertThatThrownBy(() -> rappService.disable("ABCD1234", "valid-token", 100L))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("not ACTIVE");
        }

        @Test
        @DisplayName("throws ForbiddenException when player doesn't own deployment")
        void throwsWhenPlayerDoesNotOwnDeployment() {
            deployment.setStatus(DeploymentStatus.ACTIVE);
            deployment.setPlayerId(99L);
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));

            assertThatThrownBy(() -> rappService.disable("ABCD1234", "valid-token", 100L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("does not own");
        }
    }

    @Nested
    @DisplayName("tune")
    class TuneTests {

        @Test
        @DisplayName("increments version and updates config")
        void incrementsVersionAndUpdatesConfig() {
            deployment.setStatus(DeploymentStatus.ACTIVE);
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(energySaverTemplate));
            when(rappBehaviourRegistry.getBehaviour("Energy Saver")).thenReturn(new EnergySaverBehaviour());
            when(basestationService.updateMetrics(eq(10L), any(MetricDeltas.class))).thenReturn(basestation);
            when(rappDeploymentRepository.save(any(RappDeployment.class))).thenAnswer(inv -> inv.getArgument(0));

            DeploymentResponse response = rappService.tune("ABCD1234", "valid-token", 100L, 75, "HIGH");

            assertThat(response.version()).isEqualTo(2);
            assertThat(response.configuration()).contains("\"threshold\":75");
            assertThat(response.configuration()).contains("\"aggressiveness\":\"HIGH\"");
        }

        @Test
        @DisplayName("applies new aggressiveness multiplier difference")
        void appliesNewAggressivenessMultiplierDifference() {
            deployment.setStatus(DeploymentStatus.ACTIVE);
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(energySaverTemplate));
            when(rappBehaviourRegistry.getBehaviour("Energy Saver")).thenReturn(new EnergySaverBehaviour());
            when(basestationService.updateMetrics(eq(10L), any(MetricDeltas.class))).thenReturn(basestation);
            when(rappDeploymentRepository.save(any(RappDeployment.class))).thenAnswer(inv -> inv.getArgument(0));

            rappService.tune("ABCD1234", "valid-token", 100L, 75, "HIGH");

            // Verify the difference was applied: HIGH(1.5x) - MODERATE(1.0x) = 0.5x of base
            // Energy Saver energyEfficiency base = 20, so diff = 20*1.5 - 20*1.0 = 10
            ArgumentCaptor<MetricDeltas> deltasCaptor = ArgumentCaptor.forClass(MetricDeltas.class);
            verify(basestationService).updateMetrics(eq(10L), deltasCaptor.capture());
            MetricDeltas diff = deltasCaptor.getValue();
            assertThat(diff.energyEfficiency()).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("throws InvalidStateException when not ACTIVE")
        void throwsWhenNotActive() {
            deployment.setStatus(DeploymentStatus.DEPLOYING);
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));

            assertThatThrownBy(() -> rappService.tune("ABCD1234", "valid-token", 100L, 75, "HIGH"))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("not ACTIVE");
        }
    }

    @Nested
    @DisplayName("rollback")
    class RollbackTests {

        @Test
        @DisplayName("reverts to previous version")
        void revertsToPreviousVersion() {
            deployment.setStatus(DeploymentStatus.ACTIVE);
            deployment.setVersion(2);
            deployment.setConfiguration("{\"threshold\":75,\"aggressiveness\":\"HIGH\"}");
            deployment.setPreviousConfiguration("{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}");

            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));
            when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(energySaverTemplate));
            when(rappBehaviourRegistry.getBehaviour("Energy Saver")).thenReturn(new EnergySaverBehaviour());
            when(basestationService.updateMetrics(eq(10L), any(MetricDeltas.class))).thenReturn(basestation);
            when(rappDeploymentRepository.save(any(RappDeployment.class))).thenAnswer(inv -> inv.getArgument(0));

            DeploymentResponse response = rappService.rollback("ABCD1234", "valid-token", 100L);

            assertThat(response.version()).isEqualTo(1);
            assertThat(response.configuration()).contains("\"aggressiveness\":\"MODERATE\"");
        }

        @Test
        @DisplayName("throws InvalidStateException when version is 1")
        void throwsWhenVersionIsOne() {
            deployment.setStatus(DeploymentStatus.ACTIVE);
            deployment.setVersion(1);
            when(playerService.validateToken("valid-token")).thenReturn(player);
            when(rappDeploymentRepository.findById(100L)).thenReturn(Optional.of(deployment));

            assertThatThrownBy(() -> rappService.rollback("ABCD1234", "valid-token", 100L))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("version 1");
        }
    }

    @Nested
    @DisplayName("conflict detection")
    class ConflictDetectionTests {

        @Test
        @DisplayName("detects Energy Saver + Capacity Optimiser conflict")
        void detectsEnergySaverCapacityOptimiserConflict() {
            MetricDeltas penalty = rappService.detectConflict("Energy Saver", "Capacity Optimiser");

            assertThat(penalty).isNotNull();
            assertThat(penalty.customerExperience()).isEqualByComparingTo(new BigDecimal("-10.00"));
            assertThat(penalty.energyEfficiency()).isEqualByComparingTo(new BigDecimal("-5.00"));
        }

        @Test
        @DisplayName("returns null for non-conflicting pair")
        void returnsNullForNonConflictingPair() {
            MetricDeltas penalty = rappService.detectConflict("Energy Saver", "SLA Guardian");

            assertThat(penalty).isNull();
        }
    }
}
