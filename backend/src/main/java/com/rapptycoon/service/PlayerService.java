package com.rapptycoon.service;

import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class PlayerService {

    private static final int TOKEN_LENGTH = 64;

    private final PlayerRepository playerRepository;
    private final SecureRandom secureRandom;

    public PlayerService(PlayerRepository playerRepository,
                         BasestationRepository basestationRepository,
                         BasestationStateMapper basestationStateMapper) {
        this.playerRepository = playerRepository;
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

}
