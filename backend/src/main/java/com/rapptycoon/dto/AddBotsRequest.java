package com.rapptycoon.dto;

import com.rapptycoon.model.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddBotsRequest(
        @Min(1) @Max(5) int count,
        @NotNull Difficulty difficulty
) {}
