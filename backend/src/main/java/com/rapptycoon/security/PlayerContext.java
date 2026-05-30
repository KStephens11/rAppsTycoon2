package com.rapptycoon.security;

import com.rapptycoon.model.Player;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped bean that holds the authenticated player for the current request.
 * This provides a centralized way to access the current player context
 * without passing the player object through method parameters.
 */
@Component
@RequestScope
public class PlayerContext {

    private Player player;

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    /**
     * Returns true if a player has been authenticated for this request.
     */
    public boolean isAuthenticated() {
        return player != null;
    }
}
