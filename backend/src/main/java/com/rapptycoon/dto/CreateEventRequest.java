package com.rapptycoon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
        @NotNull Long basestationId,
        @NotBlank String eventType,
        @NotBlank String severity,
        String description,
        ImpactDto impact
) {}
