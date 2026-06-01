package com.rapptycoon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapptycoon.dto.DeployRequest;
import com.rapptycoon.dto.DeploymentResponse;
import com.rapptycoon.dto.MetricsDto;
import com.rapptycoon.dto.TuneRequest;
import com.rapptycoon.service.RappService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RappController.class)
class RappControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RappService rappService;

    private DeploymentResponse sampleDeploymentResponse() {
        return new DeploymentResponse(
                10L, 1L, "Energy Saver", 1L,
                "DEPLOYING", 1,
                "{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}",
                LocalDateTime.of(2025, 1, 15, 10, 0),
                null
        );
    }

    private DeploymentResponse sampleActiveDeploymentResponse() {
        MetricsDto metrics = new MetricsDto(
                new BigDecimal("85.00"),
                new BigDecimal("90.00"),
                new BigDecimal("50.00"),
                new BigDecimal("75.00"),
                new BigDecimal("95.00"),
                new BigDecimal("88.00")
        );
        return new DeploymentResponse(
                10L, 1L, "Energy Saver", 1L,
                "ACTIVE", 1,
                "{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}",
                LocalDateTime.of(2025, 1, 15, 10, 0),
                metrics
        );
    }

    @Test
    @DisplayName("POST /deploy returns 201 with valid request")
    void deployReturns201() throws Exception {
        when(rappService.deploy(eq("ABCD1234"), eq("valid-token"), eq(1L), eq(1L)))
                .thenReturn(sampleDeploymentResponse());

        DeployRequest request = new DeployRequest(1L, 1L);

        mockMvc.perform(post("/api/sessions/ABCD1234/rapps/deploy")
                        .header("X-Session-Token", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Energy Saver"))
                .andExpect(jsonPath("$.status").value("DEPLOYING"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("POST /deploy returns 400 for missing fields")
    void deployReturns400ForMissingFields() throws Exception {
        String invalidBody = "{}";

        mockMvc.perform(post("/api/sessions/ABCD1234/rapps/deploy")
                        .header("X-Session-Token", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /{id}/disable returns 200")
    void disableReturns200() throws Exception {
        DeploymentResponse response = new DeploymentResponse(
                10L, 1L, "Energy Saver", 1L,
                "DISABLED", 1,
                "{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}",
                LocalDateTime.of(2025, 1, 15, 10, 0),
                new MetricsDto(
                        new BigDecimal("100.00"), new BigDecimal("100.00"),
                        new BigDecimal("0.00"), new BigDecimal("100.00"),
                        new BigDecimal("100.00"), new BigDecimal("100.00")
                )
        );
        when(rappService.disable(eq("ABCD1234"), eq("valid-token"), eq(10L)))
                .thenReturn(response);

        mockMvc.perform(put("/api/sessions/ABCD1234/rapps/10/disable")
                        .header("X-Session-Token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.updatedMetrics").isNotEmpty());
    }

    @Test
    @DisplayName("PUT /{id}/tune returns 200")
    void tuneReturns200() throws Exception {
        DeploymentResponse response = new DeploymentResponse(
                10L, 1L, "Energy Saver", 1L,
                "ACTIVE", 2,
                "{\"threshold\":75,\"aggressiveness\":\"HIGH\"}",
                LocalDateTime.of(2025, 1, 15, 10, 0),
                new MetricsDto(
                        new BigDecimal("100.00"), new BigDecimal("92.50"),
                        new BigDecimal("0.00"), new BigDecimal("100.00"),
                        new BigDecimal("100.00"), new BigDecimal("95.50")
                )
        );
        when(rappService.tune(eq("ABCD1234"), eq("valid-token"), eq(10L), eq(75), eq("HIGH")))
                .thenReturn(response);

        TuneRequest request = new TuneRequest(75, "HIGH");

        mockMvc.perform(put("/api/sessions/ABCD1234/rapps/10/tune")
                        .header("X-Session-Token", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.configuration").value("{\"threshold\":75,\"aggressiveness\":\"HIGH\"}"));
    }

    @Test
    @DisplayName("PUT /{id}/rollback returns 200")
    void rollbackReturns200() throws Exception {
        DeploymentResponse response = new DeploymentResponse(
                10L, 1L, "Energy Saver", 1L,
                "ACTIVE", 1,
                "{\"threshold\":50,\"aggressiveness\":\"MODERATE\"}",
                LocalDateTime.of(2025, 1, 15, 10, 0),
                new MetricsDto(
                        new BigDecimal("100.00"), new BigDecimal("95.00"),
                        new BigDecimal("0.00"), new BigDecimal("100.00"),
                        new BigDecimal("100.00"), new BigDecimal("97.00")
                )
        );
        when(rappService.rollback(eq("ABCD1234"), eq("valid-token"), eq(10L)))
                .thenReturn(response);

        mockMvc.perform(put("/api/sessions/ABCD1234/rapps/10/rollback")
                        .header("X-Session-Token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Returns 401 when token missing")
    void returns401WhenTokenMissing() throws Exception {
        DeployRequest request = new DeployRequest(1L, 1L);

        mockMvc.perform(post("/api/sessions/ABCD1234/rapps/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
