package com.rapptycoon.dto;

import java.time.LocalDateTime;

public record ActiveEventDto(
        Long id,
        String eventType,
        String severity,
        String description,
        int escalationLevel,
        LocalDateTime createdAt
) {}
