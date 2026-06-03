package com.rapptycoon.service;

import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
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
