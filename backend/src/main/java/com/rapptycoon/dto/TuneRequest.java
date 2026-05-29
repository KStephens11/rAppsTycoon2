package com.rapptycoon.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TuneRequest(
        @NotNull @Min(1) @Max(100) Integer threshold,
        @NotNull @NotBlank String aggressiveness
) {}
