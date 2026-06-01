package com.rapptycoon.service;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.dto.LeaderboardEntryDto;
import com.rapptycoon.dto.LeaderboardResponse;
import com.rapptycoon.model.Basestation;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private BasestationRepository basestationRepository;

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private GameProperties gameProperties;

    @InjectMocks
    private ScoreService scoreService;

    private GameProperties.Score score;
    private GameProperties.Score.Weight weight;

    @BeforeEach
    void setUp() {
        score = new GameProperties.Score();
        weight = new GameProperties.Score.Weight();
        weight.setMoney(0.30);
        weight.setSatisfaction(0.35);
        weight.setStability(0.35);
        score.setWeight(weight);
    }

    @Test
    @DisplayName("calculateCompositeScore: correct formula with known values")
    void calculateCompositeScore_correctFormula() {
        Player player = Player.builder()
                .id(1L)
                .sessionId(1L)
                .displayName("TestPlayer")
                .sessionToken("token123")
                .scoreMoney(new BigDecimal("800.00"))
                .scoreSatisfaction(BigDecimal.ZERO)
                .scoreStability(BigDecimal.ZERO)
                .compositeScore(BigDecimal.ZERO)
                .build();

        Basestation bs1 = Basestation.builder()
                .id(1L)
                .playerId(1L)
                .name("BS-Alpha")
                .health(new BigDecimal("90.00"))
                .customerExperience(new BigDecimal("85.00"))
                .automationReliability(new BigDecimal("95.00"))
                .slaCompliance(new BigDecimal("88.00"))
                .build();

        Basestation bs2 = Basestation.builder()
                .id(2L)
                .playerId(1L)
                .name("BS-Beta")
                .health(new BigDecimal("80.00"))
                .customerExperience(new BigDecimal("75.00"))
                .automationReliability(new BigDecimal("85.00"))
                .slaCompliance(new BigDecimal("92.00"))
                .build();

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs1, bs2));
        when(gameProperties.getScore()).thenReturn(score);
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        Player result = scoreService.calculateCompositeScore(1L);

        // customerSatisfaction = avg(85, 75) = 80.00
        assertThat(result.getScoreSatisfaction()).isEqualByComparingTo(new BigDecimal("80.00"));

        // networkStability:
        // bs1: (90 + 95 + 88) / 3 = 91.00
        // bs2: (80 + 85 + 92) / 3 = 85.67
        // avg = (91.00 + 85.67) / 2 = 88.34 (rounded)
        BigDecimal bs1Stability = new BigDecimal("90.00").add(new BigDecimal("95.00")).add(new BigDecimal("88.00"))
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal bs2Stability = new BigDecimal("80.00").add(new BigDecimal("85.00")).add(new BigDecimal("92.00"))
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal expectedStability = bs1Stability.add(bs2Stability)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        assertThat(result.getScoreStability()).isEqualByComparingTo(expectedStability);

        // compositeScore = (800 * 0.30) + (80 * 0.35) + (88.34 * 0.35)
        BigDecimal expectedComposite = new BigDecimal("800.00").multiply(BigDecimal.valueOf(0.30))
                .add(new BigDecimal("80.00").multiply(BigDecimal.valueOf(0.35)))
                .add(expectedStability.multiply(BigDecimal.valueOf(0.35)))
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(result.getCompositeScore()).isEqualByComparingTo(expectedComposite);
    }

    @Test
    @DisplayName("calculateCompositeScore: handles player with no basestations")
    void calculateCompositeScore_noBasestations() {
        Player player = Player.builder()
                .id(1L)
                .sessionId(1L)
                .displayName("TestPlayer")
                .sessionToken("token123")
                .scoreMoney(new BigDecimal("1000.00"))
                .scoreSatisfaction(BigDecimal.ZERO)
                .scoreStability(BigDecimal.ZERO)
                .compositeScore(BigDecimal.ZERO)
                .build();

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(Collections.emptyList());
        when(gameProperties.getScore()).thenReturn(score);
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        Player result = scoreService.calculateCompositeScore(1L);

        assertThat(result.getScoreSatisfaction()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getScoreStability()).isEqualByComparingTo(BigDecimal.ZERO);
        // compositeScore = (1000 * 0.30) + (0 * 0.35) + (0 * 0.35) = 300.00
        assertThat(result.getCompositeScore()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("calculateCompositeScore: averages across multiple basestations correctly")
    void calculateCompositeScore_multipleBasestations() {
        Player player = Player.builder()
                .id(1L)
                .sessionId(1L)
                .displayName("TestPlayer")
                .sessionToken("token123")
                .scoreMoney(new BigDecimal("500.00"))
                .scoreSatisfaction(BigDecimal.ZERO)
                .scoreStability(BigDecimal.ZERO)
                .compositeScore(BigDecimal.ZERO)
                .build();

        Basestation bs1 = Basestation.builder().id(1L).playerId(1L).name("BS-1")
                .health(new BigDecimal("100.00"))
                .customerExperience(new BigDecimal("100.00"))
                .automationReliability(new BigDecimal("100.00"))
                .slaCompliance(new BigDecimal("100.00"))
                .build();

        Basestation bs2 = Basestation.builder().id(2L).playerId(1L).name("BS-2")
                .health(new BigDecimal("50.00"))
                .customerExperience(new BigDecimal("50.00"))
                .automationReliability(new BigDecimal("50.00"))
                .slaCompliance(new BigDecimal("50.00"))
                .build();

        Basestation bs3 = Basestation.builder().id(3L).playerId(1L).name("BS-3")
                .health(new BigDecimal("75.00"))
                .customerExperience(new BigDecimal("75.00"))
                .automationReliability(new BigDecimal("75.00"))
                .slaCompliance(new BigDecimal("75.00"))
                .build();

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs1, bs2, bs3));
        when(gameProperties.getScore()).thenReturn(score);
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        Player result = scoreService.calculateCompositeScore(1L);

        // customerSatisfaction = avg(100, 50, 75) = 75.00
        assertThat(result.getScoreSatisfaction()).isEqualByComparingTo(new BigDecimal("75.00"));

        // networkStability:
        // bs1: (100 + 100 + 100) / 3 = 100.00
        // bs2: (50 + 50 + 50) / 3 = 50.00
        // bs3: (75 + 75 + 75) / 3 = 75.00
        // avg = (100 + 50 + 75) / 3 = 75.00
        assertThat(result.getScoreStability()).isEqualByComparingTo(new BigDecimal("75.00"));

        // compositeScore = (500 * 0.30) + (75 * 0.35) + (75 * 0.35) = 150 + 26.25 + 26.25 = 202.50
        assertThat(result.getCompositeScore()).isEqualByComparingTo(new BigDecimal("202.50"));
    }

    @Test
    @DisplayName("recalculateAllScores: updates all players in session")
    void recalculateAllScores_updatesAllPlayers() {
        Player player1 = Player.builder()
                .id(1L).sessionId(1L).displayName("P1").sessionToken("t1")
                .scoreMoney(new BigDecimal("1000.00"))
                .scoreSatisfaction(BigDecimal.ZERO)
                .scoreStability(BigDecimal.ZERO)
                .compositeScore(BigDecimal.ZERO)
                .build();

        Player player2 = Player.builder()
                .id(2L).sessionId(1L).displayName("P2").sessionToken("t2")
                .scoreMoney(new BigDecimal("800.00"))
                .scoreSatisfaction(BigDecimal.ZERO)
                .scoreStability(BigDecimal.ZERO)
                .compositeScore(BigDecimal.ZERO)
                .build();

        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player1, player2));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player1));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(player2));
        when(basestationRepository.findByPlayerId(anyLong())).thenReturn(Collections.emptyList());
        when(gameProperties.getScore()).thenReturn(score);
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Player> result = scoreService.recalculateAllScores(1L);

        assertThat(result).hasSize(2);
        verify(playerRepository, times(2)).save(any(Player.class));
    }

    @Test
    @DisplayName("getLeaderboard: returns players sorted by composite score descending")
    void getLeaderboard_sortedByCompositeScore() {
        GameSession session = GameSession.builder()
                .id(1L).sessionCode("ABCD1234").state(GameSessionState.ACTIVE).build();

        Player player1 = Player.builder()
                .id(1L).sessionId(1L).displayName("LowScore").sessionToken("t1")
                .scoreMoney(new BigDecimal("500.00"))
                .scoreSatisfaction(new BigDecimal("60.00"))
                .scoreStability(new BigDecimal("60.00"))
                .compositeScore(new BigDecimal("192.00"))
                .build();

        Player player2 = Player.builder()
                .id(2L).sessionId(1L).displayName("HighScore").sessionToken("t2")
                .scoreMoney(new BigDecimal("900.00"))
                .scoreSatisfaction(new BigDecimal("90.00"))
                .scoreStability(new BigDecimal("90.00"))
                .compositeScore(new BigDecimal("333.00"))
                .build();

        when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player1, player2));

        LeaderboardResponse response = scoreService.getLeaderboard("ABCD1234");

        assertThat(response.leaderboard()).hasSize(2);
        assertThat(response.leaderboard().get(0).rank()).isEqualTo(1);
        assertThat(response.leaderboard().get(0).displayName()).isEqualTo("HighScore");
        assertThat(response.leaderboard().get(1).rank()).isEqualTo(2);
        assertThat(response.leaderboard().get(1).displayName()).isEqualTo("LowScore");
        assertThat(response.gameState()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getLeaderboard: applies tiebreaker (satisfaction > stability > money)")
    void getLeaderboard_appliesTiebreakers() {
        GameSession session = GameSession.builder()
                .id(1L).sessionCode("ABCD1234").state(GameSessionState.ACTIVE).build();

        // Same composite score, different satisfaction
        Player player1 = Player.builder()
                .id(1L).sessionId(1L).displayName("LowerSatisfaction").sessionToken("t1")
                .scoreMoney(new BigDecimal("800.00"))
                .scoreSatisfaction(new BigDecimal("70.00"))
                .scoreStability(new BigDecimal("80.00"))
                .compositeScore(new BigDecimal("292.50"))
                .build();

        Player player2 = Player.builder()
                .id(2L).sessionId(1L).displayName("HigherSatisfaction").sessionToken("t2")
                .scoreMoney(new BigDecimal("800.00"))
                .scoreSatisfaction(new BigDecimal("85.00"))
                .scoreStability(new BigDecimal("80.00"))
                .compositeScore(new BigDecimal("292.50"))
                .build();

        when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player1, player2));

        LeaderboardResponse response = scoreService.getLeaderboard("ABCD1234");

        assertThat(response.leaderboard().get(0).displayName()).isEqualTo("HigherSatisfaction");
        assertThat(response.leaderboard().get(1).displayName()).isEqualTo("LowerSatisfaction");
    }

    @Test
    @DisplayName("determineWinner: returns player with highest score")
    void determineWinner_returnsHighestScore() {
        GameSession session = GameSession.builder()
                .id(1L).sessionCode("ABCD1234").state(GameSessionState.COMPLETED).build();

        Player player1 = Player.builder()
                .id(1L).sessionId(1L).displayName("Winner").sessionToken("t1")
                .scoreMoney(new BigDecimal("900.00"))
                .scoreSatisfaction(new BigDecimal("95.00"))
                .scoreStability(new BigDecimal("92.00"))
                .compositeScore(new BigDecimal("335.45"))
                .build();

        Player player2 = Player.builder()
                .id(2L).sessionId(1L).displayName("Loser").sessionToken("t2")
                .scoreMoney(new BigDecimal("600.00"))
                .scoreSatisfaction(new BigDecimal("70.00"))
                .scoreStability(new BigDecimal("65.00"))
                .compositeScore(new BigDecimal("227.25"))
                .build();

        when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
        when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player1, player2));

        LeaderboardEntryDto winner = scoreService.determineWinner("ABCD1234");

        assertThat(winner.displayName()).isEqualTo("Winner");
        assertThat(winner.rank()).isEqualTo(1);
        assertThat(winner.playerId()).isEqualTo(1L);
    }
}
