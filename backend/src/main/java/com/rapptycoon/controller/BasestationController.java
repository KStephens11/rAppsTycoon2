package com.rapptycoon.controller;

import com.rapptycoon.dto.BasestationResponse;
import com.rapptycoon.dto.BasestationStateDto;
import com.rapptycoon.exception.ForbiddenException;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
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
    private final GameSessionRepository gameSessionRepository;

    public BasestationController(BasestationService basestationService,
                                 PlayerService playerService,
                                 GameSessionRepository gameSessionRepository) {
        this.basestationService = basestationService;
        this.playerService = playerService;
        this.gameSessionRepository = gameSessionRepository;
    }

    @GetMapping("/{code}/basestations")
    public ResponseEntity<BasestationResponse> getPlayerBasestations(
            @PathVariable String code,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing session token");
        }

        Player player = playerService.validateToken(token);

        GameSession session = gameSessionRepository.findBySessionCode(code)
                .orElseThrow(() -> new SessionNotFoundException(code));
        if (!player.getSessionId().equals(session.getId())) {
            throw new ForbiddenException("Player is not a member of this session");
        }

        List<BasestationStateDto> basestations = basestationService.getPlayerBasestations(player.getId());

        return ResponseEntity.ok(new BasestationResponse(basestations));
    }
}
