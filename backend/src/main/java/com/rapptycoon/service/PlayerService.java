package com.rapptycoon.service;

import com.rapptycoon.dto.*;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Basestation;
import com.rapptycoon.model.GameEvent;
import com.rapptycoon.model.Player;
import com.rapptycoon.model.RappDeployment;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameEventRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.repository.RappDeploymentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class PlayerService {

    private static final int TOKEN_LENGTH = 64;

    private final PlayerRepository playerRepository;
    private final BasestationRepository basestationRepository;
    private final RappDeploymentRepository rappDeploymentRepository;
    private final GameEventRepository gameEventRepository;
    private final SecureRandom secureRandom;

    public PlayerService(PlayerRepository playerRepository,
                         BasestationRepository basestationRepository,
                         RappDeploymentRepository rappDeploymentRepository,
                         GameEventRepository gameEventRepository) {
        this.playerRepository = playerRepository;
        this.basestationRepository = basestationRepository;
        this.rappDeploymentRepository = rappDeploymentRepository;
        this.gameEventRepository = gameEventRepository;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a unique 64-character hex session token using SecureRandom.
     */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_LENGTH / 2];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Validates a session token by looking up the player.
     * @param token the session token to validate
     * @return the Player entity associated with the token
     * @throws UnauthorizedException if no player is found with the given token
     */
    public Player validateToken(String token) {
        return playerRepository.findBySessionToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid session token"));
    }

    /**
     * Marks a player as disconnected.
     * @param playerId the ID of the player to disconnect
     * @return the updated Player entity
     * @throws EntityNotFoundException if no player is found with the given ID
     */
    @Transactional
    public Player disconnect(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id: " + playerId));
        player.setConnected(false);
        return playerRepository.save(player);
    }

    /**
     * Reconnects a player using their session token and restores full game state.
     * @param token the session token
     * @return ReconnectResponse containing player info and full basestation state
     * @throws UnauthorizedException if the token is invalid
     */
    @Transactional
    public ReconnectResponse reconnect(String token) {
        Player player = validateToken(token);
        player.setConnected(true);
        player = playerRepository.save(player);

        List<Basestation> basestations = basestationRepository.findByPlayerId(player.getId());

        List<BasestationStateDto> basestationStates = basestations.stream()
                .map(bs -> {
                    List<RappDeployment> deployments = rappDeploymentRepository.findByBasestationId(bs.getId());
                    List<GameEvent> events = gameEventRepository.findByBasestationIdAndResolvedFalse(bs.getId());

                    List<DeployedRappDto> deployedRapps = deployments.stream()
                            .map(d -> new DeployedRappDto(
                                    d.getId(),
                                    d.getTemplateId(),
                                    d.getStatus().name(),
                                    d.getVersion(),
                                    d.getDeployedAt()
                            ))
                            .toList();

                    List<ActiveEventDto> activeEvents = events.stream()
                            .map(e -> new ActiveEventDto(
                                    e.getId(),
                                    e.getEventType(),
                                    e.getSeverity().name(),
                                    e.getDescription(),
                                    e.getEscalationLevel(),
                                    e.getCreatedAt()
                            ))
                            .toList();

                    MetricsDto metrics = new MetricsDto(
                            bs.getHealth(),
                            bs.getCustomerExperience(),
                            bs.getCost(),
                            bs.getEnergyEfficiency(),
                            bs.getAutomationReliability(),
                            bs.getSlaCompliance()
                    );

                    return new BasestationStateDto(
                            bs.getId(),
                            bs.getName(),
                            bs.getPositionX(),
                            bs.getPositionY(),
                            metrics,
                            deployedRapps,
                            activeEvents
                    );
                })
                .toList();

        PlayerDto playerDto = new PlayerDto(
                player.getId(),
                player.getDisplayName(),
                player.getSessionToken(),
                false, // isHost is not determined here; caller can set if needed
                player.isConnected()
        );

        return new ReconnectResponse(playerDto, basestationStates);
    }

    /**
     * Returns all players in a given session.
     * @param sessionId the session ID
     * @return list of players in the session
     */
    @Transactional(readOnly = true)
    public List<Player> getPlayersBySession(Long sessionId) {
        return playerRepository.findBySessionId(sessionId);
    }
}
