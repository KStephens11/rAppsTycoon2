package com.rapptycoon.service;

import com.rapptycoon.dto.AddBotsRequest;
import com.rapptycoon.dto.AddBotsResponse;
import com.rapptycoon.exception.ForbiddenException;
import com.rapptycoon.exception.InvalidStateException;
import com.rapptycoon.exception.SessionFullException;
import com.rapptycoon.exception.SessionNotFoundException;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Difficulty;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotServiceTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private BotService botService;

    private Player createHostPlayer(Long id, Long sessionId) {
        return Player.builder()
                .id(id)
                .sessionId(sessionId)
                .displayName("Host")
                .sessionToken("host-token-123")
                .scoreMoney(new BigDecimal("1000.00"))
                .build();
    }

    private Player createRegularPlayer(Long id, Long sessionId) {
        return Player.builder()
                .id(id)
                .sessionId(sessionId)
                .displayName("Player2")
                .sessionToken("player2-token-456")
                .scoreMoney(new BigDecimal("1000.00"))
                .build();
    }

    private GameSession createLobbySession(Long hostPlayerId) {
        return GameSession.builder()
                .id(1L)
                .sessionCode("ABCD1234")
                .state(GameSessionState.LOBBY)
                .hostPlayerId(hostPlayerId)
                .maxPlayers(6)
                .build();
    }

    @Nested
    @DisplayName("addBots")
    class AddBots {

        @Test
        @DisplayName("creates bot players with correct names and difficulty")
        void createsBotPlayersSuccessfully() {
            Player host = createHostPlayer(1L, 1L);
            GameSession session = createLobbySession(1L);

            when(playerRepository.findBySessionToken("host-token-123")).thenReturn(Optional.of(host));
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(host));
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                p.setId(10L);
                return p;
            });

            AddBotsRequest request = new AddBotsRequest(2, Difficulty.MEDIUM);
            AddBotsResponse response = botService.addBots("ABCD1234", "host-token-123", request);

            assertThat(response.bots()).hasSize(2);
            assertThat(response.bots().get(0).displayName()).isEqualTo("Bot-Alpha");
            assertThat(response.bots().get(1).displayName()).isEqualTo("Bot-Beta");
            assertThat(response.bots().get(0).isBot()).isTrue();
            verify(playerRepository, times(2)).save(any(Player.class));
        }

        @Test
        @DisplayName("assigns correct bot names when bots already exist")
        void assignsCorrectNamesWithExistingBots() {
            Player host = createHostPlayer(1L, 1L);
            GameSession session = createLobbySession(1L);

            Player existingBot = Player.builder()
                    .id(5L)
                    .sessionId(1L)
                    .displayName("Bot-Alpha")
                    .sessionToken("bot-token-existing")
                    .isBot(true)
                    .difficulty("EASY")
                    .build();

            when(playerRepository.findBySessionToken("host-token-123")).thenReturn(Optional.of(host));
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(host, existingBot));
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                p.setId(11L);
                return p;
            });

            AddBotsRequest request = new AddBotsRequest(1, Difficulty.HARD);
            AddBotsResponse response = botService.addBots("ABCD1234", "host-token-123", request);

            assertThat(response.bots()).hasSize(1);
            assertThat(response.bots().get(0).displayName()).isEqualTo("Bot-Beta");
        }

        @Test
        @DisplayName("saves bot players with isBot true and correct difficulty")
        void savesBotWithCorrectFields() {
            Player host = createHostPlayer(1L, 1L);
            GameSession session = createLobbySession(1L);

            when(playerRepository.findBySessionToken("host-token-123")).thenReturn(Optional.of(host));
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(host));
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                p.setId(10L);
                return p;
            });

            AddBotsRequest request = new AddBotsRequest(1, Difficulty.HARD);
            botService.addBots("ABCD1234", "host-token-123", request);

            verify(playerRepository).save(argThat(p ->
                    p.isBot()
                            && "HARD".equals(p.getDifficulty())
                            && p.getSessionToken() != null
                            && p.getSessionToken().length() == 64
                            && !p.isConnected()
            ));
        }

        @Test
        @DisplayName("throws UnauthorizedException for invalid token")
        void throwsUnauthorizedForInvalidToken() {
            when(playerRepository.findBySessionToken("invalid-token")).thenReturn(Optional.empty());

            AddBotsRequest request = new AddBotsRequest(1, Difficulty.EASY);

            assertThatThrownBy(() -> botService.addBots("ABCD1234", "invalid-token", request))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("throws SessionNotFoundException for unknown session code")
        void throwsSessionNotFoundForUnknownCode() {
            Player host = createHostPlayer(1L, 1L);
            when(playerRepository.findBySessionToken("host-token-123")).thenReturn(Optional.of(host));
            when(gameSessionRepository.findBySessionCode("INVALID1")).thenReturn(Optional.empty());

            AddBotsRequest request = new AddBotsRequest(1, Difficulty.EASY);

            assertThatThrownBy(() -> botService.addBots("INVALID1", "host-token-123", request))
                    .isInstanceOf(SessionNotFoundException.class);
        }

        @Test
        @DisplayName("throws ForbiddenException when non-host tries to add bots")
        void throwsForbiddenForNonHost() {
            Player nonHost = createRegularPlayer(2L, 1L);
            GameSession session = createLobbySession(1L); // host is player 1

            when(playerRepository.findBySessionToken("player2-token-456")).thenReturn(Optional.of(nonHost));
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));

            AddBotsRequest request = new AddBotsRequest(1, Difficulty.EASY);

            assertThatThrownBy(() -> botService.addBots("ABCD1234", "player2-token-456", request))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("throws InvalidStateException when session is not in LOBBY state")
        void throwsInvalidStateForActiveSession() {
            Player host = createHostPlayer(1L, 1L);
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.ACTIVE)
                    .hostPlayerId(1L)
                    .build();

            when(playerRepository.findBySessionToken("host-token-123")).thenReturn(Optional.of(host));
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));

            AddBotsRequest request = new AddBotsRequest(1, Difficulty.EASY);

            assertThatThrownBy(() -> botService.addBots("ABCD1234", "host-token-123", request))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("throws SessionFullException when adding bots would exceed max players")
        void throwsSessionFullWhenExceedingMax() {
            Player host = createHostPlayer(1L, 1L);
            GameSession session = createLobbySession(1L);

            // 5 existing players (including host)
            List<Player> existingPlayers = new ArrayList<>();
            existingPlayers.add(host);
            for (int i = 2; i <= 5; i++) {
                existingPlayers.add(Player.builder()
                        .id((long) i)
                        .sessionId(1L)
                        .displayName("Player" + i)
                        .sessionToken("token-" + i)
                        .build());
            }

            when(playerRepository.findBySessionToken("host-token-123")).thenReturn(Optional.of(host));
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(existingPlayers);

            // Trying to add 2 bots when 5 already exist (max is 6)
            AddBotsRequest request = new AddBotsRequest(2, Difficulty.EASY);

            assertThatThrownBy(() -> botService.addBots("ABCD1234", "host-token-123", request))
                    .isInstanceOf(SessionFullException.class);
        }

        @Test
        @DisplayName("generates unique 64-character session tokens for each bot")
        void generatesUniqueTokens() {
            Player host = createHostPlayer(1L, 1L);
            GameSession session = createLobbySession(1L);

            List<String> savedTokens = new ArrayList<>();

            when(playerRepository.findBySessionToken("host-token-123")).thenReturn(Optional.of(host));
            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(host));
            when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                savedTokens.add(p.getSessionToken());
                p.setId(10L);
                return p;
            });

            AddBotsRequest request = new AddBotsRequest(3, Difficulty.MEDIUM);
            botService.addBots("ABCD1234", "host-token-123", request);

            assertThat(savedTokens).hasSize(3);
            assertThat(savedTokens.stream().distinct().count()).isEqualTo(3);
            savedTokens.forEach(token -> assertThat(token).hasSize(64));
        }
    }
}
