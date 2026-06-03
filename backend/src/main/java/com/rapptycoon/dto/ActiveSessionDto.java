package com.rapptycoon.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ActiveSessionDto(
        String sessionCode,
        int playerCount,
        List<Long> basestationIds,
        List<List<Long>> basestationIdsByPlayer,
        LocalDateTime startedAt
) {}
