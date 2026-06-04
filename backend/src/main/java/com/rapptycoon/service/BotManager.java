package com.rapptycoon.service;

/**
 * Manages the lifecycle of bot player Kubernetes pods.
 * Provisions pods at game start and cleans them up at game end.
 */
public interface BotManager {

    /**
     * Provision a Kubernetes pod for each bot player registered in the given session.
     * Each pod receives the bot's session token, session code, difficulty, and backend URL
     * as environment variables.
     *
     * @param sessionCode the session code identifying the game session
     */
    void provisionBots(String sessionCode);

    /**
     * Clean up (delete) all bot-related Kubernetes pods for the given session.
     * Used as a fallback when the session transitions to COMPLETED state.
     *
     * @param sessionCode the session code identifying the game session
     */
    void cleanupBots(String sessionCode);
}
