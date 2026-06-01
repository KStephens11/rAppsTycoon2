package com.rapptycoon.dto;

import jakarta.validation.constraints.NotNull;

public record DeployRequest(
        @NotNull Long templateId,
        @NotNull Long basestationId
) {}
