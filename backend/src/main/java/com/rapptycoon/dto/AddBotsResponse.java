package com.rapptycoon.dto;

import java.util.List;

public record AddBotsResponse(
        List<BotPlayerDto> bots
) {}
