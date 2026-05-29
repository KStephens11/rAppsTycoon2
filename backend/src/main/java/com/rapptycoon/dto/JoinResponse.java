package com.rapptycoon.dto;

public record JoinResponse(
        PlayerDto player,
        SessionResponse session
) {}
