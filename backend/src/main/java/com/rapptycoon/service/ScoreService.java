package com.rapptycoon.service;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.dto.LeaderboardEntryDto;
import com.rapptycoon.dto.LeaderboardResponse;
import com.rapptycoon.dto.ScoreDto;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.model.Basestation;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ScoreService {

    private final PlayerRepository playerRepository;
    private final BasestationRepository basestationRepository;
    private final GameSessionRepository gameSessionRepository;
    private final GameProperties gameProperties;

    public ScoreService(PlayerRepository playerRepository,
                        BasestationRepository basestationRepository,
                        GameSessionRepository gameSessionRepository,
                        GameProperties gameProperties) {
        this.playerRepository = playerRepository;
        this.basestationRepository = basestationRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.gameProperties = gameProperties;
    }

    /**
     * Calculates the composite score for a player based on their basestations' metrics.
     * Formula: compositeScore = (money × weightMoney) + (satisfaction × weightSatisfaction) + (stability × weightStability)
     *
     * @param playerId the player's ID
     * @return the updated Player with recalculated scores
     */
    @Transactional
    public Player calculateCompositeScore(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id: " + playerId));

        List<Basestation> basestations = basestationRepository.findByPlayerId(playerId);

        BigDecimal customerSatisfaction;
        BigDecimal networkStability;

        if (basestations.isEmpty()) {
            customerSatisfaction = BigDecimal.ZERO;
            networkStability = BigDecimal.ZERO;
        } else {
            // customerSatisfaction = average customerExperience across all basestations
            BigDecimal totalCustomerExperience = basestations.stream()
                    .map(Basestation::getCustomerExperience)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            customerSatisfaction = totalCustomerExperience.divide(
                    BigDecimal.valueOf(basestations.size()), 2, RoundingMode.HALF_UP);

            // networkStability = average of (health + automationReliability + slaCompliance) / 3 across all basestations
            BigDecimal totalStability = BigDecimal.ZERO;
            for (Basestation bs : basestations) {
                BigDecimal stationStability = bs.getHealth()
                        .add(bs.getAutomationReliability())
                        .add(bs.getSlaCompliance())
                        .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
                totalStability = totalStability.add(stationStability);
            }
            networkStability = totalStability.divide(
                    BigDecimal.valueOf(basestations.size()), 2, RoundingMode.HALF_UP);
        }

        // Get weights from configuration
        double weightMoney = gameProperties.getScore().getWeight().getMoney();
        double weightSatisfaction = gameProperties.getScore().getWeight().getSatisfaction();
        double weightStability = gameProperties.getScore().getWeight().getStability();

        // compositeScore = (money × weightMoney) + (satisfaction × weightSatisfaction) + (stability × weightStability)
        BigDecimal moneyComponent = player.getScoreMoney()
                .multiply(BigDecimal.valueOf(weightMoney));
        BigDecimal satisfactionComponent = customerSatisfaction
                .multiply(BigDecimal.valueOf(weightSatisfaction));
        BigDecimal stabilityComponent = networkStability
                .multiply(BigDecimal.valueOf(weightStability));

        BigDecimal compositeScore = moneyComponent
                .add(satisfactionComponent)
                .add(stabilityComponent)
                .setScale(2, RoundingMode.HALF_UP);

        // Update player scores
        player.setScoreSatisfaction(customerSatisfaction);
        player.setScoreStability(networkStability);
        player.setCompositeScore(compositeScore);

        return playerRepository.save(player);
    }

    /**
     * Recalculates scores for all players in a session.
     *
     * @param sessionId the session ID
     * @return list of updated players
     */
    @Transactional
    public List<Player> recalculateAllScores(Long sessionId) {
        List<Player> players = playerRepository.findBySessionId(sessionId);
        List<Player> updatedPlayers = new ArrayList<>();
        for (Player player : players) {
            updatedPlayers.add(calculateCompositeScore(player.getId()));
        }
        return updatedPlayers;
    }

    /**
     * Returns the leaderboard for a session, sorted by composite score descending with tiebreakers.
     *
     * @param sessionCode the session code
     * @return LeaderboardResponse with ranked entries
     */
    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(String sessionCode) {
        GameSession session = gameSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new SessionNotFoundException(sessionCode));

        List<Player> players = playerRepository.findBySessionId(session.getId());

        // Sort by composite score descending, then apply tiebreakers
        List<Player> sorted = players.stream()
                .sorted(leaderboardComparator())
                .toList();

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Player p = sorted.get(i);
            ScoreDto scores = new ScoreDto(
                    p.getScoreMoney(),
                    p.getScoreSatisfaction(),
                    p.getScoreStability()
            );
            entries.add(new LeaderboardEntryDto(
                    i + 1,
                    p.getId(),
                    p.getDisplayName(),
                    scores,
                    p.getCompositeScore()
            ));
        }

        return new LeaderboardResponse(entries, session.getState().name());
    }

    /**
     * Determines the winner of a session (rank 1 on the leaderboard).
     *
     * @param sessionCode the session code
     * @return the LeaderboardEntryDto for the winner
     */
    @Transactional(readOnly = true)
    public LeaderboardEntryDto determineWinner(String sessionCode) {
        LeaderboardResponse leaderboard = getLeaderboard(sessionCode);
        if (leaderboard.leaderboard().isEmpty()) {
            throw new EntityNotFoundException("No players found in session: " + sessionCode);
        }
        return leaderboard.leaderboard().get(0);
    }

    /**
     * Comparator for leaderboard sorting:
     * 1. Higher composite score first
     * 2. Tiebreaker: higher customer satisfaction
     * 3. Tiebreaker: higher network stability
     * 4. Tiebreaker: more remaining money
     */
    private Comparator<Player> leaderboardComparator() {
        return Comparator.comparing(Player::getCompositeScore).reversed()
                .thenComparing(Comparator.comparing(Player::getScoreSatisfaction).reversed())
                .thenComparing(Comparator.comparing(Player::getScoreStability).reversed())
                .thenComparing(Comparator.comparing(Player::getScoreMoney).reversed());
    }
}
