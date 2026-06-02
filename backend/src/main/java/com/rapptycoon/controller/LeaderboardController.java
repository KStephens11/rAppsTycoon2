package com.rapptycoon.controller;

import com.rapptycoon.dto.LeaderboardResponse;
import com.rapptycoon.exception.ForbiddenException;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.service.PlayerService;
import com.rapptycoon.service.ScoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class LeaderboardController {

    private final ScoreService scoreService;
    private final PlayerService playerService;
    private final GameSessionRepository gameSessionRepository;

    public LeaderboardController(ScoreService scoreService,
                                 PlayerService playerService,
                                 GameSessionRepository gameSessionRepository) {
        this.scoreService = scoreService;
        this.playerService = playerService;
        this.gameSessionRepository = gameSessionRepository;
    }

    @GetMapping("/{code}/leaderboard")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
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

        LeaderboardResponse response = scoreService.getLeaderboard(code);
        return ResponseEntity.ok(response);
    }
}
