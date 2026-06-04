package com.rapptycoon.dto;

import java.math.BigDecimal;

public record RappTemplateResponse(
        Long id,
        String name,
        String purpose,
        BigDecimal cost,
        String benefit,
        BigDecimal risk,
        BigDecimal confidence,
        String sideEffects,
        ImpactDto impact
) {}
