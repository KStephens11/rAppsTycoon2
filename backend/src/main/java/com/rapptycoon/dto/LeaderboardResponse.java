package com.rapptycoon.dto;

import java.util.List;

public record LeaderboardResponse(
        List<LeaderboardEntryDto> leaderboard,
        String gameState
) {}
