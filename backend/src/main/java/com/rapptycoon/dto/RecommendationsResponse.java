package com.rapptycoon.dto;

import java.util.List;

public record RecommendationsResponse(
        List<RecommendationDto> recommendations
) {}
