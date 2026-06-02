package com.rapptycoon.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SessionResponse(
        String sessionCode,
        String state,
        int maxPlayers,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        List<PlayerDto> players
) {}
