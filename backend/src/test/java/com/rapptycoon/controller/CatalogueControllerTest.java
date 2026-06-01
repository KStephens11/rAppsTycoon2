package com.rapptycoon.controller;

import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Player;
import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.service.PlayerService;
import com.rapptycoon.service.RappCatalogueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogueController.class)
class CatalogueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RappCatalogueService rappCatalogueService;

    @MockitoBean
    private PlayerService playerService;

    @Test
    @DisplayName("GET /api/rapps/catalogue returns 200 with list of rApps")
    void returns200WithListOfRapps() throws Exception {
        Player player = Player.builder()
                .id(1L)
                .sessionId(1L)
                .displayName("TestPlayer")
                .sessionToken("valid-token-123")
                .connected(true)
                .build();

        RappTemplate template = RappTemplate.builder()
                .id(1L)
                .name("Energy Saver")
                .purpose("Reduces power consumption across basestation cells")
                .cost(new BigDecimal("50.00"))
                .benefit("Lowers energy costs by optimising power usage")
                .risk(new BigDecimal("25.00"))
                .confidence(new BigDecimal("80.00"))
                .sideEffects("May increase latency in high-load cells")
                .impactHealth(new BigDecimal("0.00"))
                .impactCustomerExperience(new BigDecimal("-5.00"))
                .impactCost(new BigDecimal("-30.00"))
                .impactEnergyEfficiency(new BigDecimal("20.00"))
                .impactAutomationReliability(new BigDecimal("0.00"))
                .impactSlaCompliance(new BigDecimal("-3.00"))
                .build();

        when(playerService.validateToken("valid-token-123")).thenReturn(player);
        when(rappCatalogueService.getCatalogue()).thenReturn(List.of(template));

        mockMvc.perform(get("/api/rapps/catalogue")
                        .header("X-Session-Token", "valid-token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rapps").isArray())
                .andExpect(jsonPath("$.rapps[0].id").value(1))
                .andExpect(jsonPath("$.rapps[0].name").value("Energy Saver"))
                .andExpect(jsonPath("$.rapps[0].purpose").value("Reduces power consumption across basestation cells"))
                .andExpect(jsonPath("$.rapps[0].cost").value(50.00))
                .andExpect(jsonPath("$.rapps[0].impact.health").value(0.00))
                .andExpect(jsonPath("$.rapps[0].impact.customerExperience").value(-5.00))
                .andExpect(jsonPath("$.rapps[0].impact.energyEfficiency").value(20.00));
    }

    @Test
    @DisplayName("GET /api/rapps/catalogue returns 401 when token missing")
    void returns401WhenTokenMissing() throws Exception {
        mockMvc.perform(get("/api/rapps/catalogue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/rapps/catalogue returns 401 when token invalid")
    void returns401WhenTokenInvalid() throws Exception {
        when(playerService.validateToken("bad-token"))
                .thenThrow(new UnauthorizedException("Invalid session token"));

        mockMvc.perform(get("/api/rapps/catalogue")
                        .header("X-Session-Token", "bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
