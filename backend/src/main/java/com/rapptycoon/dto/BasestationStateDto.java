package com.rapptycoon.dto;

import java.util.List;

public record BasestationStateDto(
        Long id,
        String name,
        int positionX,
        int positionY,
        MetricsDto metrics,
        List<DeployedRappDto> deployedRapps,
        List<ActiveEventDto> activeEvents
) {}
