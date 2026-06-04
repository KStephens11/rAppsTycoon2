package com.rapptycoon.dto;

import java.util.List;

public record ReconnectResponse(
        PlayerDto player,
        List<BasestationStateDto> basestations
) {}
