package com.rapptycoon.service;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.dto.*;
import com.rapptycoon.exception.*;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class GameSessionService {

    private static final String CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final int TOKEN_LENGTH = 64;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final GameProperties gameProperties;
    private final SecureRandom secureRandom;

    public GameSessionService(GameSessionRepository gameSessionRepository,
                              PlayerRepository playerRepository,
                              GameProperties gameProperties) {
        this.gameSessionRepository = gameSessionRepository;
        this.playerRepository = playerRepository;
        this.gameProperties = gameProperties;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public CreateSessionResponse createSession(String hostName) {
        String sessionCode = generateUniqueSessionCode();
        LocalDateTime now = LocalDateTime.now();

        GameSession session = GameSession.builder()
                .sessionCode(sessionCode)
                .state(GameSessionState.LOBBY)
                .maxPlayers(gameProperties.getPlayers().getMax())
                .createdAt(now)
                .build();
        session = gameSessionRepository.save(session);

        String token = generateToken();
        Player hostPlayer = Player.builder()
                .sessionId(session.getId())
                .displayName(hostName)
                .sessionToken(token)
                .connected(true)
                .build();
        hostPlayer = playerRepository.save(hostPlayer);

        session.setHostPlayerId(hostPlayer.getId());
        session = gameSessionRepository.save(session);

        PlayerDto hostPlayerDto = new PlayerDto(
                hostPlayer.getId(),
                hostPlayer.getDisplayName(),
                hostPlayer.getSessionToken(),
                true,
                hostPlayer.isConnected()
        );

        return new CreateSessionResponse(
                session.getSessionCode(),
                session.getId(),
                hostPlayerDto,
                session.getState().name(),
                session.getMaxPlayers(),
                session.getCreatedAt()
        );
    }

    @Transactional
    public JoinResponse joinSession(String code, String displayName) {
        GameSession session = findSessionByCode(code);

        if (session.getState() != GameSessionState.LOBBY) {
            throw new InvalidStateException("Session is not in LOBBY state");
        }

        List<Player> players = playerRepository.findBySessionId(session.getId());
        if (players.size() >= gameProperties.getPlayers().getMax()) {
            throw new SessionFullException(code);
        }

        String token = generateToken();
        Player newPlayer = Player.builder()
                .sessionId(session.getId())
                .displayName(displayName)
                .sessionToken(token)
                .connected(true)
                .build();
        newPlayer = playerRepository.save(newPlayer);

        // Refresh player list after adding new player
        players = playerRepository.findBySessionId(session.getId());

        PlayerDto playerDto = new PlayerDto(
                newPlayer.getId(),
                newPlayer.getDisplayName(),
                newPlayer.getSessionToken(),
                false,
                newPlayer.isConnected()
        );

        SessionResponse sessionResponse = buildSessionResponse(session, players);

        return new JoinResponse(playerDto, sessionResponse);
    }

    @Transactional
    public SessionResponse startSession(String code, String token) {
        GameSession session = findSessionByCode(code);

        Player player = playerRepository.findBySessionToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid session token"));

        if (!player.getId().equals(session.getHostPlayerId())) {
            throw new ForbiddenException("Only the host can start the session");
        }

        if (session.getState() != GameSessionState.LOBBY) {
            throw new InvalidStateException("Session is not in LOBBY state");
        }

        List<Player> players = playerRepository.findBySessionId(session.getId());
        if (players.size() < gameProperties.getPlayers().getMin()) {
            throw new InvalidStateException("Not enough players to start (minimum " + gameProperties.getPlayers().getMin() + ")");
        }

        session.setState(GameSessionState.ACTIVE);
        session.setStartedAt(LocalDateTime.now());
        session = gameSessionRepository.save(session);

        return buildSessionResponse(session, players);
    }

    @Transactional
    public SessionResponse endSession(String code) {
        GameSession session = findSessionByCode(code);

        if (session.getState() != GameSessionState.ACTIVE) {
            throw new InvalidStateException("Session is not in ACTIVE state");
        }

        session.setState(GameSessionState.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        session = gameSessionRepository.save(session);

        List<Player> players = playerRepository.findBySessionId(session.getId());
        return buildSessionResponse(session, players);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(String code) {
        GameSession session = findSessionByCode(code);
        List<Player> players = playerRepository.findBySessionId(session.getId());
        return buildSessionResponse(session, players);
    }

    private GameSession findSessionByCode(String code) {
        return gameSessionRepository.findBySessionCode(code)
                .orElseThrow(() -> new SessionNotFoundException(code));
    }

    private SessionResponse buildSessionResponse(GameSession session, List<Player> players) {
        List<PlayerDto> playerDtos = players.stream()
                .map(p -> new PlayerDto(
                        p.getId(),
                        p.getDisplayName(),
                        null, // Don't expose tokens in session responses
                        p.getId().equals(session.getHostPlayerId()),
                        p.isConnected()
                ))
                .toList();

        return new SessionResponse(
                session.getSessionCode(),
                session.getState().name(),
                session.getMaxPlayers(),
                session.getCreatedAt(),
                session.getStartedAt(),
                session.getEndedAt(),
                playerDtos
        );
    }

    String generateUniqueSessionCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = generateSessionCode();
            if (gameSessionRepository.findBySessionCode(code).isEmpty()) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate unique session code after " + MAX_CODE_GENERATION_ATTEMPTS + " attempts");
    }

    private String generateSessionCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(CODE_CHARACTERS.length());
            sb.append(CODE_CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

    String generateToken() {
        byte[] bytes = new byte[TOKEN_LENGTH / 2];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
