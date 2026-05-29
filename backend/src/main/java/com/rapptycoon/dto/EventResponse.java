package com.rapptycoon.dto;

import java.time.LocalDateTime;

public record EventResponse(
        Long eventId,
        String sessionCode,
        Long basestationId,
        String eventType,
        String severity,
        int escalationLevel,
        boolean resolved,
        LocalDateTime createdAt
) {}
