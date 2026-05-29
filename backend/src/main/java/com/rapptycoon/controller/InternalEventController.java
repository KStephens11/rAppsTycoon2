package com.rapptycoon.controller;

import com.rapptycoon.dto.*;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Basestation;
import com.rapptycoon.model.GameEvent;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.service.EventService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/internal")
public class InternalEventController {

    private final EventService eventService;
    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final BasestationRepository basestationRepository;
    private final String internalApiKey;

    public InternalEventController(EventService eventService,
                                   GameSessionRepository gameSessionRepository,
                                   PlayerRepository playerRepository,
                                   BasestationRepository basestationRepository,
                                   @Value("${internal.api-key}") String internalApiKey) {
        this.eventService = eventService;
        this.gameSessionRepository = gameSessionRepository;
        this.playerRepository = playerRepository;
        this.basestationRepository = basestationRepository;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/sessions/{code}/events")
    public ResponseEntity<EventResponse> pushEvent(
            @PathVariable String code,
            @RequestHeader(value = "X-Internal-Key", required = false) String apiKey,
            @Valid @RequestBody CreateEventRequest request) {
        validateInternalKey(apiKey);

        GameEvent event = eventService.createEvent(
                code,
                request.basestationId(),
                request.eventType(),
                request.severity(),
                request.description(),
                request.impact()
        );

        EventResponse response = new EventResponse(
                event.getId(),
                code,
                event.getBasestationId(),
                event.getEventType(),
                event.getSeverity().name(),
                event.getEscalationLevel(),
                event.isResolved(),
                event.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<ActiveSessionsResponse> getActiveSessions(
            @RequestHeader(value = "X-Internal-Key", required = false) String apiKey) {
        validateInternalKey(apiKey);

        List<GameSession> activeSessions = gameSessionRepository.findByState(GameSessionState.ACTIVE);

        List<ActiveSessionDto> sessionDtos = activeSessions.stream()
                .map(session -> {
                    int playerCount = playerRepository.findBySessionId(session.getId()).size();
                    List<Long> basestationIds = basestationRepository.findByPlayerId(session.getId()).isEmpty()
                            ? getAllBasestationIdsForSession(session.getId())
                            : getAllBasestationIdsForSession(session.getId());

                    return new ActiveSessionDto(
                            session.getSessionCode(),
                            playerCount,
                            basestationIds,
                            session.getStartedAt()
                    );
                })
                .toList();

        return ResponseEntity.ok(new ActiveSessionsResponse(sessionDtos));
    }

    private List<Long> getAllBasestationIdsForSession(Long sessionId) {
        return playerRepository.findBySessionId(sessionId).stream()
                .flatMap(player -> basestationRepository.findByPlayerId(player.getId()).stream())
                .map(Basestation::getId)
                .toList();
    }

    private void validateInternalKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || !apiKey.equals(internalApiKey)) {
            throw new UnauthorizedException("Invalid or missing internal API key");
        }
    }
}
