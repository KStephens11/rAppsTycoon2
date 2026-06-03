package com.rapptycoon.service;

import com.rapptycoon.dto.AddBotsRequest;
import com.rapptycoon.dto.AddBotsResponse;
import com.rapptycoon.dto.BotPlayerDto;
import com.rapptycoon.exception.ForbiddenException;
import com.rapptycoon.exception.InvalidStateException;
import com.rapptycoon.exception.SessionFullException;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
public class BotService {

    private static final List<String> BOT_NAMES = List.of(
            "Bot-Alpha", "Bot-Beta", "Bot-Gamma", "Bot-Delta", "Bot-Epsilon"
    );

    private static final int TOKEN_LENGTH = 64;
    private static final int MAX_PLAYERS = 6;

    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final SecureRandom secureRandom;

    public BotService(GameSessionRepository gameSessionRepository,
                      PlayerRepository playerRepository) {
        this.gameSessionRepository = gameSessionRepository;
        this.playerRepository = playerRepository;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public AddBotsResponse addBots(String code, String token, AddBotsRequest request) {
        // Validate token
        Player requestingPlayer = playerRepository.findBySessionToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid session token"));

        // Find session
        GameSession session = gameSessionRepository.findBySessionCode(code)
                .orElseThrow(() -> new SessionNotFoundException(code));

        // Validate requesting player is the host
        if (!requestingPlayer.getId().equals(session.getHostPlayerId())) {
            throw new ForbiddenException("Only the host can add bots to the session");
        }

        // Validate session is in LOBBY state
        if (session.getState() != GameSessionState.LOBBY) {
            throw new InvalidStateException("Session is not in LOBBY state");
        }

        // Validate total player count
        List<Player> existingPlayers = playerRepository.findBySessionId(session.getId());
        int totalAfterAdd = existingPlayers.size() + request.count();
        if (totalAfterAdd > MAX_PLAYERS) {
            throw new SessionFullException(code);
        }

        // Determine how many bots already exist to pick the right names
        long existingBotCount = existingPlayers.stream()
                .filter(Player::isBot)
                .count();

        // Create bot players
        List<BotPlayerDto> createdBots = new ArrayList<>();
        for (int i = 0; i < request.count(); i++) {
            int nameIndex = (int) existingBotCount + i;
            String displayName = nameIndex < BOT_NAMES.size()
                    ? BOT_NAMES.get(nameIndex)
                    : "Bot-" + (nameIndex + 1);

            Player botPlayer = Player.builder()
                    .sessionId(session.getId())
                    .displayName(displayName)
                    .sessionToken(generateToken())
                    .isBot(true)
                    .difficulty(request.difficulty().name())
                    .connected(false)
                    .build();
            botPlayer = playerRepository.save(botPlayer);

            createdBots.add(new BotPlayerDto(
                    botPlayer.getId(),
                    botPlayer.getDisplayName(),
                    botPlayer.isBot()
            ));
        }

        return new AddBotsResponse(createdBots);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_LENGTH / 2];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
