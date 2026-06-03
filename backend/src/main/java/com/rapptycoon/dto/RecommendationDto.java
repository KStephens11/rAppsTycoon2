package com.rapptycoon.dto;

public record RecommendationDto(
        String action,
        Long rappTemplateId,
        Long deploymentId,
        Long basestationId,
        double confidence,
        String reasoning
) {}
