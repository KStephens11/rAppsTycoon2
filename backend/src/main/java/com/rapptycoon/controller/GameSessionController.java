package com.rapptycoon.controller;

import com.rapptycoon.dto.*;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.service.GameSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class GameSessionController {

    private final GameSessionService gameSessionService;

    public GameSessionController(GameSessionService gameSessionService) {
        this.gameSessionService = gameSessionService;
    }

    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        CreateSessionResponse response = gameSessionService.createSession(request.hostName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<JoinResponse> joinSession(
            @PathVariable String code,
            @Valid @RequestBody JoinSessionRequest request) {
        JoinResponse response = gameSessionService.joinSession(code, request.displayName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<SessionResponse> startSession(
            @PathVariable String code,
            @RequestHeader("X-Session-Token") String token,
            @RequestBody(required = false) StartSessionRequest request) {
        int durationMinutes = (request != null && request.durationMinutes() != null)
                ? request.durationMinutes() : 5;
        SessionResponse response = gameSessionService.startSession(code, token, durationMinutes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable String code,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing session token");
        }
        SessionResponse response = gameSessionService.getSession(code, token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<Void> leaveSession(
            @PathVariable String code,
            @RequestHeader("X-Session-Token") String token) {
        gameSessionService.leaveSession(code, token);
        return ResponseEntity.noContent().build();
    }
}
