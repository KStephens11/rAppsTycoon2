package com.rapptycoon.factory;

import com.rapptycoon.model.DeploymentStatus;
import com.rapptycoon.model.RappDeployment;
import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.repository.RappDeploymentRepository;
import com.rapptycoon.repository.RappTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RappFactoryTest {

    @Mock
    private RappTemplateRepository rappTemplateRepository;

    @Mock
    private RappDeploymentRepository rappDeploymentRepository;

    private RappFactory rappFactory;

    @BeforeEach
    void setUp() {
        rappFactory = new RappFactory(rappTemplateRepository, rappDeploymentRepository);
    }

    private RappTemplate createTemplate() {
        return RappTemplate.builder()
                .id(1L)
                .name("Energy Saver")
                .purpose("Reduces power consumption")
                .cost(new BigDecimal("50.00"))
                .benefit("Lowers energy costs")
                .risk(new BigDecimal("25.00"))
                .confidence(new BigDecimal("80.00"))
                .sideEffects("May increase latency")
                .build();
    }

    @Test
    @DisplayName("createDeployment creates deployment with DEPLOYING status")
    void createDeploymentSetsDeployingStatus() {
        RappTemplate template = createTemplate();
        when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(rappDeploymentRepository.save(any(RappDeployment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RappDeployment result = rappFactory.createDeployment(1L, 10L, 5L);

        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.DEPLOYING);
    }

    @Test
    @DisplayName("createDeployment sets version to 1")
    void createDeploymentSetsVersionTo1() {
        RappTemplate template = createTemplate();
        when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(rappDeploymentRepository.save(any(RappDeployment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RappDeployment result = rappFactory.createDeployment(1L, 10L, 5L);

        assertThat(result.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("createDeployment sets deployedAt timestamp")
    void createDeploymentSetsDeployedAt() {
        RappTemplate template = createTemplate();
        when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(rappDeploymentRepository.save(any(RappDeployment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RappDeployment result = rappFactory.createDeployment(1L, 10L, 5L);

        assertThat(result.getDeployedAt()).isNotNull();
    }

    @Test
    @DisplayName("createDeployment sets correct templateId, basestationId, and playerId")
    void createDeploymentSetsCorrectIds() {
        RappTemplate template = createTemplate();
        when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(rappDeploymentRepository.save(any(RappDeployment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RappDeployment result = rappFactory.createDeployment(1L, 10L, 5L);

        assertThat(result.getTemplateId()).isEqualTo(1L);
        assertThat(result.getBasestationId()).isEqualTo(10L);
        assertThat(result.getPlayerId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("createDeployment saves the deployment via repository")
    void createDeploymentSavesViaRepository() {
        RappTemplate template = createTemplate();
        when(rappTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(rappDeploymentRepository.save(any(RappDeployment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        rappFactory.createDeployment(1L, 10L, 5L);

        ArgumentCaptor<RappDeployment> captor = ArgumentCaptor.forClass(RappDeployment.class);
        verify(rappDeploymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DeploymentStatus.DEPLOYING);
    }

    @Test
    @DisplayName("createDeployment throws EntityNotFoundException for non-existent template")
    void createDeploymentThrowsForNonExistentTemplate() {
        when(rappTemplateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rappFactory.createDeployment(999L, 10L, 5L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("RappTemplate not found");
    }
}
