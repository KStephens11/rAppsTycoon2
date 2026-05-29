package com.rapptycoon.dto;

import java.time.LocalDateTime;

public record DeployedRappDto(
        Long id,
        Long templateId,
        String status,
        int version,
        LocalDateTime deployedAt
) {}
