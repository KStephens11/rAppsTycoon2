package com.rapptycoon.service;

import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.repository.RappTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RappCatalogueServiceTest {

    @Mock
    private RappTemplateRepository rappTemplateRepository;

    private RappCatalogueService rappCatalogueService;

    @BeforeEach
    void setUp() {
        rappCatalogueService = new RappCatalogueService(rappTemplateRepository);
    }

    @Test
    @DisplayName("getCatalogue returns all templates from repository")
    void getCatalogueReturnsAllTemplates() {
        RappTemplate template1 = RappTemplate.builder()
                .id(1L)
                .name("Energy Saver")
                .purpose("Reduces power consumption")
                .cost(new BigDecimal("50.00"))
                .benefit("Lowers energy costs")
                .risk(new BigDecimal("25.00"))
                .confidence(new BigDecimal("80.00"))
                .sideEffects("May increase latency")
                .build();

        RappTemplate template2 = RappTemplate.builder()
                .id(2L)
                .name("Capacity Optimiser")
                .purpose("Dynamically allocates capacity")
                .cost(new BigDecimal("75.00"))
                .benefit("Improves customer experience")
                .risk(new BigDecimal("15.00"))
                .confidence(new BigDecimal("85.00"))
                .sideEffects("Higher operational cost")
                .build();

        when(rappTemplateRepository.findAll()).thenReturn(List.of(template1, template2));

        List<RappTemplate> result = rappCatalogueService.getCatalogue();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Energy Saver");
        assertThat(result.get(1).getName()).isEqualTo("Capacity Optimiser");
    }

    @Test
    @DisplayName("getCatalogue returns empty list when no templates exist")
    void getCatalogueReturnsEmptyList() {
        when(rappTemplateRepository.findAll()).thenReturn(Collections.emptyList());

        List<RappTemplate> result = rappCatalogueService.getCatalogue();

        assertThat(result).isEmpty();
    }
}
