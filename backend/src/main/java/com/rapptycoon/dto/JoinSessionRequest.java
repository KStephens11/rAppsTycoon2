package com.rapptycoon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinSessionRequest(
        @NotBlank @Size(max = 50) String displayName
) {}
