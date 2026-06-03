package com.rapptycoon.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ActiveSessionDto(
        String sessionCode,
        int playerCount,
        List<Long> basestationIds,
        List<List<Long>> basestationIdsByPlayer,
        Map<Long, String> basestationNames,
        LocalDateTime startedAt
) {}
