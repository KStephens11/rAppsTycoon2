package com.rapptycoon.dto;

import java.time.LocalDateTime;

public record CreateSessionResponse(
        String sessionCode,
        Long sessionId,
        PlayerDto hostPlayer,
        String state,
        int maxPlayers,
        LocalDateTime createdAt
) {}
