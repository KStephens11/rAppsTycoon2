package com.rapptycoon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapptycoon.dto.*;
import com.rapptycoon.exception.InvalidStateException;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.repository.GameSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Assumptions;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommenderServiceTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private PlayerService playerService;

    @Mock
    private BasestationService basestationService;

    @Mock
    private RappCatalogueService rappCatalogueService;

    private RecommenderService recommenderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        recommenderService = new RecommenderService(
                gameSessionRepository,
                playerService,
                basestationService,
                rappCatalogueService,
                objectMapper
        );
        // Point to the actual recommend.py script
        ReflectionTestUtils.setField(recommenderService, "pythonPath", "python3");
        ReflectionTestUtils.setField(recommenderService, "scriptPath",
                System.getProperty("user.dir") + "/../bot-player/recommend.py");
    }

    private Player createPlayer(Long id, Long sessionId) {
        return Player.builder()
                .id(id)
                .sessionId(sessionId)
                .displayName("TestPlayer")
                .sessionToken("token123")
                .scoreMoney(new BigDecimal("800.00"))
                .difficulty("MEDIUM")
                .build();
    }

    private GameSession createActiveSession() {
        return GameSession.builder()
                .id(1L)
                .sessionCode("ABCD1234")
                .state(GameSessionState.ACTIVE)
                .hostPlayerId(1L)
                .build();
    }

    private List<BasestationStateDto> createBasestationState() {
        MetricsDto metrics = new MetricsDto(
                new BigDecimal("70.00"),
                new BigDecimal("65.00"),
                new BigDecimal("50.00"),
                new BigDecimal("60.00"),
                new BigDecimal("72.00"),
                new BigDecimal("68.00")
        );

        ActiveEventDto event = new ActiveEventDto(
                100L,
                "POWER_OUTAGE",
                "HIGH",
                "Power failure detected",
                1,
                LocalDateTime.now()
        );

        BasestationStateDto bs = new BasestationStateDto(
                10L,
                "BS-Alpha",
                100,
                200,
                metrics,
                Collections.emptyList(),
                List.of(event)
        );

        return List.of(bs);
    }

    private List<RappTemplate> createCatalogue() {
        return List.of(
                RappTemplate.builder()
                        .id(1L).name("Energy Saver").purpose("Reduce energy usage")
                        .cost(new BigDecimal("100.00")).benefit("Saves energy")
                        .risk(new BigDecimal("15.00")).confidence(new BigDecimal("85.00"))
                        .sideEffects("Reduces customer experience slightly")
                        .build(),
                RappTemplate.builder()
                        .id(3L).name("Fault Predictor").purpose("Predict hardware faults")
                        .cost(new BigDecimal("80.00")).benefit("Prevents outages")
                        .risk(new BigDecimal("10.00")).confidence(new BigDecimal("90.00"))
                        .sideEffects("Minor cost increase")
                        .build()
        );
    }

    @Nested
    @DisplayName("getRecommendations - validation")
    class Validation {

        @Test
        @DisplayName("throws SessionNotFoundException for unknown session code")
        void throwsSessionNotFound() {
            Player player = createPlayer(1L, 1L);
            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("INVALID1")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    recommenderService.getRecommendations("INVALID1", "token123"))
                    .isInstanceOf(SessionNotFoundException.class);
        }

        @Test
        @DisplayName("throws InvalidStateException when player not in session")
        void throwsWhenPlayerNotInSession() {
            Player player = createPlayer(1L, 99L); // session 99, not 1
            GameSession session = createActiveSession(); // session id = 1

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));

            assertThatThrownBy(() ->
                    recommenderService.getRecommendations("ABCD1234", "token123"))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        @DisplayName("throws InvalidStateException when session is not ACTIVE")
        void throwsWhenSessionNotActive() {
            Player player = createPlayer(1L, 1L);
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .hostPlayerId(1L)
                    .build();

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));

            assertThatThrownBy(() ->
                    recommenderService.getRecommendations("ABCD1234", "token123"))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("not in ACTIVE state");
        }

        @Test
        @DisplayName("throws InvalidStateException when session is COMPLETED")
        void throwsWhenSessionCompleted() {
            Player player = createPlayer(1L, 1L);
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.COMPLETED)
                    .hostPlayerId(1L)
                    .build();

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));

            assertThatThrownBy(() ->
                    recommenderService.getRecommendations("ABCD1234", "token123"))
                    .isInstanceOf(InvalidStateException.class);
        }
    }

    @Nested
    @DisplayName("getRecommendations - successful invocation")
    class SuccessfulInvocation {

        @BeforeEach
        void requirePython() {
            boolean available = false;
            for (String cmd : new String[]{"python3", "python"}) {
                try {
                    Process p = new ProcessBuilder(cmd, "--version").start();
                    available = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
                    if (available) {
                        ReflectionTestUtils.setField(recommenderService, "pythonPath", cmd);
                        break;
                    }
                } catch (Exception ignored) {}
            }
            Assumptions.assumeTrue(available, "Skipping: python3/python not available in this environment");
        }

        @Test
        @DisplayName("returns recommendation when Python script is available")
        void returnsRecommendationsFromPython() {
            Player player = createPlayer(1L, 1L);
            GameSession session = createActiveSession();

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(basestationService.getPlayerBasestations(1L)).thenReturn(createBasestationState());
            when(rappCatalogueService.getCatalogue()).thenReturn(createCatalogue());

            RecommendationResponse response = recommenderService.getRecommendations("ABCD1234", "token123");

            assertThat(response).isNotNull();
            assertThat(response.recommendation()).isNotNull();

            // Verify recommendation structure
            assertThat(response.recommendation().action()).isNotNull();
            assertThat(response.recommendation().confidence()).isBetween(0.0, 1.0);
            assertThat(response.recommendation().reasoning()).isNotBlank();
        }

        @Test
        @DisplayName("event-resolving recommendation has DEPLOY action")
        void eventResolvingRecsHaveDeployAction() {
            Player player = createPlayer(1L, 1L);
            GameSession session = createActiveSession();

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(basestationService.getPlayerBasestations(1L)).thenReturn(createBasestationState());
            when(rappCatalogueService.getCatalogue()).thenReturn(createCatalogue());

            RecommendationResponse response = recommenderService.getRecommendations("ABCD1234", "token123");

            // With POWER_OUTAGE event, should recommend deploying
            assertThat(response.recommendation()).isNotNull();
            assertThat(response.recommendation().action()).isEqualTo("DEPLOY");
        }

        @Test
        @DisplayName("recommendation targets the correct basestation")
        void recommendationsTargetCorrectBasestation() {
            Player player = createPlayer(1L, 1L);
            GameSession session = createActiveSession();

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(basestationService.getPlayerBasestations(1L)).thenReturn(createBasestationState());
            when(rappCatalogueService.getCatalogue()).thenReturn(createCatalogue());

            RecommendationResponse response = recommenderService.getRecommendations("ABCD1234", "token123");

            // Recommendation should target basestation 10 (the only one)
            assertThat(response.recommendation()).isNotNull();
            assertThat(response.recommendation().basestationId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("getRecommendations - graceful degradation")
    class GracefulDegradation {

        @Test
        @DisplayName("returns null recommendation when Python script path is invalid")
        void returnsEmptyWhenScriptMissing() {
            Player player = createPlayer(1L, 1L);
            GameSession session = createActiveSession();

            // Point to a non-existent script
            ReflectionTestUtils.setField(recommenderService, "scriptPath", "/nonexistent/path/recommend.py");

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(basestationService.getPlayerBasestations(1L)).thenReturn(createBasestationState());
            when(rappCatalogueService.getCatalogue()).thenReturn(createCatalogue());

            RecommendationResponse response = recommenderService.getRecommendations("ABCD1234", "token123");

            // Should not throw — returns null recommendation gracefully
            assertThat(response).isNotNull();
            assertThat(response.recommendation()).isNull();
        }

        @Test
        @DisplayName("returns null recommendation when Python binary is not found")
        void returnsEmptyWhenPythonMissing() {
            Player player = createPlayer(1L, 1L);
            GameSession session = createActiveSession();

            // Point to a non-existent Python binary
            ReflectionTestUtils.setField(recommenderService, "pythonPath", "/nonexistent/python99");

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(basestationService.getPlayerBasestations(1L)).thenReturn(createBasestationState());
            when(rappCatalogueService.getCatalogue()).thenReturn(createCatalogue());

            RecommendationResponse response = recommenderService.getRecommendations("ABCD1234", "token123");

            assertThat(response).isNotNull();
            assertThat(response.recommendation()).isNull();
        }

        @Test
        @DisplayName("returns null recommendation when no basestations exist")
        void returnsEmptyWhenNoBasestations() {
            Player player = createPlayer(1L, 1L);
            GameSession session = createActiveSession();

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(basestationService.getPlayerBasestations(1L)).thenReturn(Collections.emptyList());
            when(rappCatalogueService.getCatalogue()).thenReturn(Collections.emptyList());

            RecommendationResponse response = recommenderService.getRecommendations("ABCD1234", "token123");

            assertThat(response).isNotNull();
            assertThat(response.recommendation()).isNull();
        }

        @Test
        @DisplayName("uses MEDIUM difficulty when player difficulty is null")
        void usesDefaultDifficultyWhenNull() {
            Player player = createPlayer(1L, 1L);
            player.setDifficulty(null); // null difficulty
            GameSession session = createActiveSession();

            when(playerService.validateToken("token123")).thenReturn(player);
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(basestationService.getPlayerBasestations(1L)).thenReturn(createBasestationState());
            when(rappCatalogueService.getCatalogue()).thenReturn(createCatalogue());

            // Should not throw — defaults to MEDIUM
            RecommendationResponse response = recommenderService.getRecommendations("ABCD1234", "token123");
            assertThat(response).isNotNull();
        }
    }
}
