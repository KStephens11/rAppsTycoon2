package com.rapptycoon.dto;

import java.math.BigDecimal;

public record LeaderboardEntryDto(
        int rank,
        Long playerId,
        String displayName,
        ScoreDto scores,
        BigDecimal compositeScore
) {}
