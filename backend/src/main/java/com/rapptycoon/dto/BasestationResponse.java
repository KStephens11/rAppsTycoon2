package com.rapptycoon.dto;

import java.util.List;

public record BasestationResponse(
        List<BasestationStateDto> basestations
) {}
