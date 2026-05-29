package com.rapptycoon.dto;

import jakarta.validation.constraints.NotNull;

public record TuneRequest(
        @NotNull Integer threshold,
        @NotNull String aggressiveness
) {}
