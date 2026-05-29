package com.rapptycoon.service;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.dto.BasestationStateDto;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameEventRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.repository.RappDeploymentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BasestationServiceTest {

    @Mock
    private BasestationRepository basestationRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private RappDeploymentRepository rappDeploymentRepository;

    @Mock
    private GameEventRepository gameEventRepository;

    private GameProperties gameProperties;

    private BasestationService basestationService;

    @BeforeEach
    void setUp() {
        gameProperties = new GameProperties();
        gameProperties.getBasestations().setPerPlayer(3);

        basestationService = new BasestationService(
                basestationRepository,
                playerRepository,
                rappDeploymentRepository,
                gameEventRepository,
                gameProperties
        );
    }

    @Nested
    @DisplayName("assignBasestations")
    class AssignBasestations {

        @Test
        @DisplayName("creates 3 basestations per player")
        void creates3BasestationsPerPlayer() {
            Player player1 = Player.builder().id(1L).sessionId(10L).displayName("P1").sessionToken("t1").build();
            Player player2 = Player.builder().id(2L).sessionId(10L).displayName("P2").sessionToken("t2").build();

            when(playerRepository.findBySessionId(10L)).thenReturn(List.of(player1, player2));
            when(basestationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            List<Basestation> result = basestationService.assignBasestations(10L);

            assertThat(result).hasSize(6);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Basestation>> captor = ArgumentCaptor.forClass(List.class);
            verify(basestationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(6);
        }

        @Test
        @DisplayName("uses unique names for all basestations")
        void usesUniqueNames() {
            Player player1 = Player.builder().id(1L).sessionId(10L).displayName("P1").sessionToken("t1").build();
            Player player2 = Player.builder().id(2L).sessionId(10L).displayName("P2").sessionToken("t2").build();

            when(playerRepository.findBySessionId(10L)).thenReturn(List.of(player1, player2));
            when(basestationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            List<Basestation> result = basestationService.assignBasestations(10L);

            List<String> names = result.stream().map(Basestation::getName).toList();
            assertThat(names).doesNotHaveDuplicates();
            assertThat(names).allMatch(name -> name.startsWith("BS-"));
        }

        @Test
        @DisplayName("sets default metrics for all basestations")
        void setsDefaultMetrics() {
            Player player1 = Player.builder().id(1L).sessionId(10L).displayName("P1").sessionToken("t1").build();

            when(playerRepository.findBySessionId(10L)).thenReturn(List.of(player1));
            when(basestationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            List<Basestation> result = basestationService.assignBasestations(10L);

            for (Basestation bs : result) {
                assertThat(bs.getHealth()).isEqualByComparingTo(new BigDecimal("100.00"));
                assertThat(bs.getCustomerExperience()).isEqualByComparingTo(new BigDecimal("100.00"));
                assertThat(bs.getCost()).isEqualByComparingTo(new BigDecimal("0.00"));
                assertThat(bs.getEnergyEfficiency()).isEqualByComparingTo(new BigDecimal("100.00"));
                assertThat(bs.getAutomationReliability()).isEqualByComparingTo(new BigDecimal("100.00"));
                assertThat(bs.getSlaCompliance()).isEqualByComparingTo(new BigDecimal("100.00"));
            }
        }
    }

    @Nested
    @DisplayName("getPlayerBasestations")
    class GetPlayerBasestations {

        @Test
        @DisplayName("returns state with deployed rApps and active events")
        void returnsStateWithRappsAndEvents() {
            Basestation bs = Basestation.builder()
                    .id(1L)
                    .playerId(5L)
                    .name("BS-Alpha")
                    .positionX(100)
                    .positionY(200)
                    .health(new BigDecimal("85.00"))
                    .customerExperience(new BigDecimal("90.00"))
                    .cost(new BigDecimal("50.00"))
                    .energyEfficiency(new BigDecimal("75.00"))
                    .automationReliability(new BigDecimal("95.00"))
                    .slaCompliance(new BigDecimal("88.00"))
                    .build();

            RappDeployment deployment = RappDeployment.builder()
                    .id(10L)
                    .templateId(3L)
                    .basestationId(1L)
                    .playerId(5L)
                    .status(DeploymentStatus.ACTIVE)
                    .version(1)
                    .deployedAt(LocalDateTime.of(2025, 1, 15, 10, 0))
                    .build();

            GameEvent event = GameEvent.builder()
                    .id(20L)
                    .sessionId(1L)
                    .basestationId(1L)
                    .eventType("POWER_OUTAGE")
                    .severity(EventSeverity.HIGH)
                    .description("Power failure")
                    .escalationLevel(1)
                    .createdAt(LocalDateTime.of(2025, 1, 15, 10, 5))
                    .resolved(false)
                    .build();

            when(basestationRepository.findByPlayerId(5L)).thenReturn(List.of(bs));
            when(rappDeploymentRepository.findByBasestationId(1L)).thenReturn(List.of(deployment));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(1L)).thenReturn(List.of(event));

            List<BasestationStateDto> result = basestationService.getPlayerBasestations(5L);

            assertThat(result).hasSize(1);
            BasestationStateDto state = result.get(0);
            assertThat(state.id()).isEqualTo(1L);
            assertThat(state.name()).isEqualTo("BS-Alpha");
            assertThat(state.metrics().health()).isEqualByComparingTo(new BigDecimal("85.00"));
            assertThat(state.deployedRapps()).hasSize(1);
            assertThat(state.deployedRapps().get(0).templateId()).isEqualTo(3L);
            assertThat(state.deployedRapps().get(0).status()).isEqualTo("ACTIVE");
            assertThat(state.activeEvents()).hasSize(1);
            assertThat(state.activeEvents().get(0).eventType()).isEqualTo("POWER_OUTAGE");
            assertThat(state.activeEvents().get(0).escalationLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns empty list when player has no basestations")
        void returnsEmptyForNoBasestations() {
            when(basestationRepository.findByPlayerId(99L)).thenReturn(Collections.emptyList());

            List<BasestationStateDto> result = basestationService.getPlayerBasestations(99L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateMetrics")
    class UpdateMetrics {

        private Basestation createBasestation(BigDecimal health, BigDecimal customerExperience,
                                              BigDecimal cost, BigDecimal energyEfficiency,
                                              BigDecimal automationReliability, BigDecimal slaCompliance) {
            return Basestation.builder()
                    .id(1L)
                    .playerId(1L)
                    .name("BS-Alpha")
                    .positionX(100)
                    .positionY(200)
                    .health(health)
                    .customerExperience(customerExperience)
                    .cost(cost)
                    .energyEfficiency(energyEfficiency)
                    .automationReliability(automationReliability)
                    .slaCompliance(slaCompliance)
                    .build();
        }

        @Test
        @DisplayName("applies positive deltas correctly")
        void appliesPositiveDeltas() {
            Basestation bs = createBasestation(
                    new BigDecimal("50.00"), new BigDecimal("60.00"),
                    new BigDecimal("100.00"), new BigDecimal("70.00"),
                    new BigDecimal("80.00"), new BigDecimal("75.00")
            );

            MetricDeltas deltas = new MetricDeltas(
                    new BigDecimal("10.00"), new BigDecimal("5.00"),
                    new BigDecimal("20.00"), new BigDecimal("15.00"),
                    new BigDecimal("8.00"), new BigDecimal("12.00")
            );

            when(basestationRepository.findById(1L)).thenReturn(Optional.of(bs));
            when(basestationRepository.save(any(Basestation.class))).thenAnswer(inv -> inv.getArgument(0));

            Basestation result = basestationService.updateMetrics(1L, deltas);

            assertThat(result.getHealth()).isEqualByComparingTo(new BigDecimal("60.00"));
            assertThat(result.getCustomerExperience()).isEqualByComparingTo(new BigDecimal("65.00"));
            assertThat(result.getCost()).isEqualByComparingTo(new BigDecimal("120.00"));
            assertThat(result.getEnergyEfficiency()).isEqualByComparingTo(new BigDecimal("85.00"));
            assertThat(result.getAutomationReliability()).isEqualByComparingTo(new BigDecimal("88.00"));
            assertThat(result.getSlaCompliance()).isEqualByComparingTo(new BigDecimal("87.00"));
        }

        @Test
        @DisplayName("applies negative deltas correctly")
        void appliesNegativeDeltas() {
            Basestation bs = createBasestation(
                    new BigDecimal("80.00"), new BigDecimal("90.00"),
                    new BigDecimal("200.00"), new BigDecimal("85.00"),
                    new BigDecimal("95.00"), new BigDecimal("88.00")
            );

            MetricDeltas deltas = new MetricDeltas(
                    new BigDecimal("-15.00"), new BigDecimal("-10.00"),
                    new BigDecimal("-50.00"), new BigDecimal("-20.00"),
                    new BigDecimal("-5.00"), new BigDecimal("-8.00")
            );

            when(basestationRepository.findById(1L)).thenReturn(Optional.of(bs));
            when(basestationRepository.save(any(Basestation.class))).thenAnswer(inv -> inv.getArgument(0));

            Basestation result = basestationService.updateMetrics(1L, deltas);

            assertThat(result.getHealth()).isEqualByComparingTo(new BigDecimal("65.00"));
            assertThat(result.getCustomerExperience()).isEqualByComparingTo(new BigDecimal("80.00"));
            assertThat(result.getCost()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(result.getEnergyEfficiency()).isEqualByComparingTo(new BigDecimal("65.00"));
            assertThat(result.getAutomationReliability()).isEqualByComparingTo(new BigDecimal("90.00"));
            assertThat(result.getSlaCompliance()).isEqualByComparingTo(new BigDecimal("80.00"));
        }

        @Test
        @DisplayName("clamps health minimum to 0")
        void clampsHealthMinimumToZero() {
            Basestation bs = createBasestation(
                    new BigDecimal("10.00"), new BigDecimal("100.00"),
                    new BigDecimal("0.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00")
            );

            MetricDeltas deltas = new MetricDeltas(
                    new BigDecimal("-50.00"), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO
            );

            when(basestationRepository.findById(1L)).thenReturn(Optional.of(bs));
            when(basestationRepository.save(any(Basestation.class))).thenAnswer(inv -> inv.getArgument(0));

            Basestation result = basestationService.updateMetrics(1L, deltas);

            assertThat(result.getHealth()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("clamps health maximum to 100")
        void clampsHealthMaximumTo100() {
            Basestation bs = createBasestation(
                    new BigDecimal("95.00"), new BigDecimal("100.00"),
                    new BigDecimal("0.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00")
            );

            MetricDeltas deltas = new MetricDeltas(
                    new BigDecimal("20.00"), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO
            );

            when(basestationRepository.findById(1L)).thenReturn(Optional.of(bs));
            when(basestationRepository.save(any(Basestation.class))).thenAnswer(inv -> inv.getArgument(0));

            Basestation result = basestationService.updateMetrics(1L, deltas);

            assertThat(result.getHealth()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("clamps cost minimum to 0")
        void clampsCostMinimumToZero() {
            Basestation bs = createBasestation(
                    new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("30.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00")
            );

            MetricDeltas deltas = new MetricDeltas(
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("-100.00"), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO
            );

            when(basestationRepository.findById(1L)).thenReturn(Optional.of(bs));
            when(basestationRepository.save(any(Basestation.class))).thenAnswer(inv -> inv.getArgument(0));

            Basestation result = basestationService.updateMetrics(1L, deltas);

            assertThat(result.getCost()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("allows cost above 100")
        void allowsCostAbove100() {
            Basestation bs = createBasestation(
                    new BigDecimal("100.00"), new BigDecimal("100.00"),
                    new BigDecimal("90.00"), new BigDecimal("100.00"),
                    new BigDecimal("100.00"), new BigDecimal("100.00")
            );

            MetricDeltas deltas = new MetricDeltas(
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("500.00"), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO
            );

            when(basestationRepository.findById(1L)).thenReturn(Optional.of(bs));
            when(basestationRepository.save(any(Basestation.class))).thenAnswer(inv -> inv.getArgument(0));

            Basestation result = basestationService.updateMetrics(1L, deltas);

            assertThat(result.getCost()).isEqualByComparingTo(new BigDecimal("590.00"));
        }

        @Test
        @DisplayName("throws EntityNotFoundException for non-existent basestation")
        void throwsForNonExistentBasestation() {
            when(basestationRepository.findById(999L)).thenReturn(Optional.empty());

            MetricDeltas deltas = new MetricDeltas(
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO
            );

            assertThatThrownBy(() -> basestationService.updateMetrics(999L, deltas))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Basestation not found");
        }
    }
}
