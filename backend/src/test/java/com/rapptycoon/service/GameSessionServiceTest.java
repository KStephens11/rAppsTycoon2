package com.rapptycoon.service;

import com.rapptycoon.config.GameProperties;
import com.rapptycoon.dto.CreateSessionResponse;
import com.rapptycoon.dto.JoinResponse;
import com.rapptycoon.dto.SessionResponse;
import com.rapptycoon.exception.*;
import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.GameSessionRepository;
import com.rapptycoon.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private BasestationService basestationService;

    private GameProperties gameProperties;
    private GameSessionService gameSessionService;

    @BeforeEach
    void setUp() {
        gameProperties = new GameProperties();
        GameProperties.Players players = new GameProperties.Players();
        players.setMin(2);
        players.setMax(6);
        gameProperties.setPlayers(players);

        gameSessionService = new GameSessionService(gameSessionRepository, playerRepository, gameProperties, basestationService);
    }

    @Nested
    @DisplayName("createSession")
    class CreateSession {

        @Test
        @DisplayName("creates session in LOBBY state with 8-char code")
        void createsSessionInLobbyStateWith8CharCode() {
            when(gameSessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> {
                GameSession session = invocation.getArgument(0);
                session.setId(1L);
                return session;
            });
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
                Player player = invocation.getArgument(0);
                player.setId(1L);
                return player;
            });

            CreateSessionResponse response = gameSessionService.createSession("HostPlayer");

            assertThat(response.sessionCode()).hasSize(8);
            assertThat(response.state()).isEqualTo("LOBBY");
        }

        @Test
        @DisplayName("creates host player with 64-char token")
        void createsHostPlayerWith64CharToken() {
            when(gameSessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> {
                GameSession session = invocation.getArgument(0);
                session.setId(1L);
                return session;
            });
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
                Player player = invocation.getArgument(0);
                player.setId(1L);
                return player;
            });

            CreateSessionResponse response = gameSessionService.createSession("HostPlayer");

            assertThat(response.hostPlayer().sessionToken()).hasSize(64);
        }

        @Test
        @DisplayName("sets hostPlayerId on session after player creation")
        void setsHostPlayerIdOnSession() {
            when(gameSessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> {
                GameSession session = invocation.getArgument(0);
                session.setId(1L);
                return session;
            });
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
                Player player = invocation.getArgument(0);
                player.setId(10L);
                return player;
            });

            gameSessionService.createSession("HostPlayer");

            // Verify save is called twice: once for initial creation, once to set hostPlayerId
            verify(gameSessionRepository, times(2)).save(argThat(session ->
                    session.getHostPlayerId() == null || session.getHostPlayerId().equals(10L)
            ));
        }

        @Test
        @DisplayName("player has default scores (money=1000, satisfaction=100, stability=100)")
        void playerHasDefaultScores() {
            when(gameSessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> {
                GameSession session = invocation.getArgument(0);
                session.setId(1L);
                return session;
            });
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
                Player player = invocation.getArgument(0);
                player.setId(1L);
                return player;
            });

            gameSessionService.createSession("HostPlayer");

            verify(playerRepository).save(argThat(player ->
                    player.getScoreMoney().compareTo(new BigDecimal("1000.00")) == 0 &&
                    player.getScoreSatisfaction().compareTo(new BigDecimal("100.00")) == 0 &&
                    player.getScoreStability().compareTo(new BigDecimal("100.00")) == 0
            ));
        }

        @Test
        @DisplayName("player is marked as connected")
        void playerIsMarkedAsConnected() {
            when(gameSessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> {
                GameSession session = invocation.getArgument(0);
                session.setId(1L);
                return session;
            });
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
                Player player = invocation.getArgument(0);
                player.setId(1L);
                return player;
            });

            CreateSessionResponse response = gameSessionService.createSession("HostPlayer");

            assertThat(response.hostPlayer().connected()).isTrue();
            verify(playerRepository).save(argThat(Player::isConnected));
        }
    }

    @Nested
    @DisplayName("joinSession")
    class JoinSession {

        @Test
        @DisplayName("successfully joins when session is in LOBBY and not full")
        void successfullyJoinsLobbySession() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .hostPlayerId(1L)
                    .maxPlayers(6)
                    .build();

            Player existingPlayer = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("hosttoken")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L))
                    .thenReturn(List.of(existingPlayer))
                    .thenReturn(List.of(existingPlayer, Player.builder()
                            .id(2L)
                            .sessionId(1L)
                            .displayName("NewPlayer")
                            .sessionToken("newtoken")
                            .connected(true)
                            .build()));
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
                Player player = invocation.getArgument(0);
                player.setId(2L);
                return player;
            });

            JoinResponse response = gameSessionService.joinSession("ABCD1234", "NewPlayer");

            assertThat(response.player().displayName()).isEqualTo("NewPlayer");
            assertThat(response.player().sessionToken()).isNotNull();
            assertThat(response.session()).isNotNull();
        }

        @Test
        @DisplayName("throws SessionNotFoundException for non-existent code")
        void throwsSessionNotFoundForNonExistentCode() {
            when(gameSessionRepository.findBySessionCode("INVALID1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gameSessionService.joinSession("INVALID1", "Player"))
                    .isInstanceOf(SessionNotFoundException.class);
        }

        @Test
        @DisplayName("throws InvalidStateException when session is ACTIVE")
        void throwsInvalidStateWhenActive() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.ACTIVE)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> gameSessionService.joinSession("ABCD1234", "Player"))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("throws InvalidStateException when session is COMPLETED")
        void throwsInvalidStateWhenCompleted() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.COMPLETED)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> gameSessionService.joinSession("ABCD1234", "Player"))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("throws SessionFullException when session has 6 players")
        void throwsSessionFullWhenMaxPlayers() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .maxPlayers(6)
                    .build();

            List<Player> sixPlayers = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                sixPlayers.add(Player.builder()
                        .id((long) (i + 1))
                        .sessionId(1L)
                        .displayName("Player" + i)
                        .sessionToken("token" + i)
                        .build());
            }

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(sixPlayers);

            assertThatThrownBy(() -> gameSessionService.joinSession("ABCD1234", "ExtraPlayer"))
                    .isInstanceOf(SessionFullException.class);
        }
    }

    @Nested
    @DisplayName("startSession")
    class StartSession {

        @Test
        @DisplayName("successfully transitions LOBBY → ACTIVE when host calls with ≥2 players")
        void successfullyStartsSession() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .hostPlayerId(1L)
                    .maxPlayers(6)
                    .createdAt(LocalDateTime.now())
                    .build();

            Player hostPlayer = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("hosttoken123")
                    .connected(true)
                    .build();

            Player otherPlayer = Player.builder()
                    .id(2L)
                    .sessionId(1L)
                    .displayName("Player2")
                    .sessionToken("othertoken")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionToken("hosttoken123")).thenReturn(Optional.of(hostPlayer));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(hostPlayer, otherPlayer));
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SessionResponse response = gameSessionService.startSession("ABCD1234", "hosttoken123");

            assertThat(response.state()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("sets startedAt timestamp")
        void setsStartedAtTimestamp() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .hostPlayerId(1L)
                    .maxPlayers(6)
                    .createdAt(LocalDateTime.now())
                    .build();

            Player hostPlayer = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("hosttoken123")
                    .connected(true)
                    .build();

            Player otherPlayer = Player.builder()
                    .id(2L)
                    .sessionId(1L)
                    .displayName("Player2")
                    .sessionToken("othertoken")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionToken("hosttoken123")).thenReturn(Optional.of(hostPlayer));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(hostPlayer, otherPlayer));
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SessionResponse response = gameSessionService.startSession("ABCD1234", "hosttoken123");

            assertThat(response.startedAt()).isNotNull();
        }

        @Test
        @DisplayName("throws UnauthorizedException for invalid token")
        void throwsUnauthorizedForInvalidToken() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .hostPlayerId(1L)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionToken("invalidtoken")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gameSessionService.startSession("ABCD1234", "invalidtoken"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("throws ForbiddenException when non-host tries to start")
        void throwsForbiddenForNonHost() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .hostPlayerId(1L)
                    .build();

            Player nonHostPlayer = Player.builder()
                    .id(2L)
                    .sessionId(1L)
                    .displayName("NotHost")
                    .sessionToken("nonhosttoken")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionToken("nonhosttoken")).thenReturn(Optional.of(nonHostPlayer));

            assertThatThrownBy(() -> gameSessionService.startSession("ABCD1234", "nonhosttoken"))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("throws InvalidStateException when session is not in LOBBY")
        void throwsInvalidStateWhenNotLobby() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.ACTIVE)
                    .hostPlayerId(1L)
                    .build();

            Player hostPlayer = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("hosttoken123")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionToken("hosttoken123")).thenReturn(Optional.of(hostPlayer));

            assertThatThrownBy(() -> gameSessionService.startSession("ABCD1234", "hosttoken123"))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("throws InvalidStateException when fewer than 2 players")
        void throwsInvalidStateWhenFewerThan2Players() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .hostPlayerId(1L)
                    .build();

            Player hostPlayer = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("hosttoken123")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionToken("hosttoken123")).thenReturn(Optional.of(hostPlayer));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(hostPlayer));

            assertThatThrownBy(() -> gameSessionService.startSession("ABCD1234", "hosttoken123"))
                    .isInstanceOf(InvalidStateException.class);
        }
    }

    @Nested
    @DisplayName("endSession")
    class EndSession {

        @Test
        @DisplayName("successfully transitions ACTIVE → COMPLETED")
        void successfullyEndsSession() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.ACTIVE)
                    .hostPlayerId(1L)
                    .maxPlayers(6)
                    .createdAt(LocalDateTime.now())
                    .startedAt(LocalDateTime.now())
                    .build();

            Player player = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("token")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));

            SessionResponse response = gameSessionService.endSession("ABCD1234");

            assertThat(response.state()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("sets endedAt timestamp")
        void setsEndedAtTimestamp() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.ACTIVE)
                    .hostPlayerId(1L)
                    .maxPlayers(6)
                    .createdAt(LocalDateTime.now())
                    .startedAt(LocalDateTime.now())
                    .build();

            Player player = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("token")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player));

            SessionResponse response = gameSessionService.endSession("ABCD1234");

            assertThat(response.endedAt()).isNotNull();
        }

        @Test
        @DisplayName("throws InvalidStateException when session is not ACTIVE")
        void throwsInvalidStateWhenNotActive() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> gameSessionService.endSession("ABCD1234"))
                    .isInstanceOf(InvalidStateException.class);
        }
    }

    @Nested
    @DisplayName("getSession")
    class GetSession {

        @Test
        @DisplayName("returns session with player list")
        void returnsSessionWithPlayerList() {
            GameSession session = GameSession.builder()
                    .id(1L)
                    .sessionCode("ABCD1234")
                    .state(GameSessionState.LOBBY)
                    .hostPlayerId(1L)
                    .maxPlayers(6)
                    .createdAt(LocalDateTime.now())
                    .build();

            Player player1 = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("token1")
                    .connected(true)
                    .build();

            Player player2 = Player.builder()
                    .id(2L)
                    .sessionId(1L)
                    .displayName("Player2")
                    .sessionToken("token2")
                    .connected(true)
                    .build();

            when(gameSessionRepository.findBySessionCode("ABCD1234")).thenReturn(Optional.of(session));
            when(playerRepository.findBySessionId(1L)).thenReturn(List.of(player1, player2));
            when(playerRepository.findBySessionToken("token1")).thenReturn(Optional.of(player1));

            SessionResponse response = gameSessionService.getSession("ABCD1234", "token1");

            assertThat(response.sessionCode()).isEqualTo("ABCD1234");
            assertThat(response.players()).hasSize(2);
        }

        @Test
        @DisplayName("throws SessionNotFoundException for non-existent code")
        void throwsSessionNotFoundForNonExistentCode() {
            Player player = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("Host")
                    .sessionToken("token1")
                    .connected(true)
                    .build();
            when(playerRepository.findBySessionToken("token1")).thenReturn(Optional.of(player));
            when(gameSessionRepository.findBySessionCode("INVALID1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gameSessionService.getSession("INVALID1", "token1"))
                    .isInstanceOf(SessionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("generateUniqueSessionCode")
    class GenerateUniqueSessionCode {

        @Test
        @DisplayName("generates 8-character code")
        void generates8CharCode() {
            when(gameSessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());

            String code = gameSessionService.generateUniqueSessionCode();

            assertThat(code).hasSize(8);
        }

        @Test
        @DisplayName("code contains only A-Z and 0-9")
        void codeContainsOnlyAlphanumeric() {
            when(gameSessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());

            String code = gameSessionService.generateUniqueSessionCode();

            assertThat(code).matches("[A-Z0-9]{8}");
        }

        @Test
        @DisplayName("retries on collision")
        void retriesOnCollision() {
            GameSession existingSession = GameSession.builder()
                    .id(1L)
                    .sessionCode("EXISTING")
                    .state(GameSessionState.LOBBY)
                    .build();

            // First call returns existing session (collision), second call returns empty (success)
            when(gameSessionRepository.findBySessionCode(anyString()))
                    .thenReturn(Optional.of(existingSession))
                    .thenReturn(Optional.empty());

            String code = gameSessionService.generateUniqueSessionCode();

            assertThat(code).hasSize(8);
            verify(gameSessionRepository, times(2)).findBySessionCode(anyString());
        }
    }

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("generates 64-character token")
        void generates64CharToken() {
            String token = gameSessionService.generateToken();

            assertThat(token).hasSize(64);
        }

        @Test
        @DisplayName("token contains only hex characters (0-9, a-f)")
        void tokenContainsOnlyHexChars() {
            String token = gameSessionService.generateToken();

            assertThat(token).matches("[0-9a-f]{64}");
        }
    }
}
