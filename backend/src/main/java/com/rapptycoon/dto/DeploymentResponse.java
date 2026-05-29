package com.rapptycoon.dto;

import java.time.LocalDateTime;

public record DeploymentResponse(
        Long id,
        Long templateId,
        String name,
        Long basestationId,
        String status,
        int version,
        String configuration,
        LocalDateTime deployedAt,
        MetricsDto updatedMetrics
) {}
