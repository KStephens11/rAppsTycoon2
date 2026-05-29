package com.rapptycoon.controller;

import com.rapptycoon.dto.BasestationResponse;
import com.rapptycoon.dto.BasestationStateDto;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Player;
import com.rapptycoon.service.BasestationService;
import com.rapptycoon.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class BasestationController {

    private final BasestationService basestationService;
    private final PlayerService playerService;

    public BasestationController(BasestationService basestationService, PlayerService playerService) {
        this.basestationService = basestationService;
        this.playerService = playerService;
    }

    @GetMapping("/{code}/basestations")
    public ResponseEntity<BasestationResponse> getPlayerBasestations(
            @PathVariable String code,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing session token");
        }

        Player player = playerService.validateToken(token);
        List<BasestationStateDto> basestations = basestationService.getPlayerBasestations(player.getId());

        return ResponseEntity.ok(new BasestationResponse(basestations));
    }
}
