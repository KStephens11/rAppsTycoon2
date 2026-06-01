package com.rapptycoon.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerDto(
        Long id,
        String displayName,
        String sessionToken,
        boolean isHost,
        boolean connected
) {}
