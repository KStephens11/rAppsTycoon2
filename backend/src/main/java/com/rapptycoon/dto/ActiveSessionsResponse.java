package com.rapptycoon.dto;

import java.util.List;

public record ActiveSessionsResponse(List<ActiveSessionDto> sessions) {}
