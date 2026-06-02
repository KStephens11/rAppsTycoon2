package com.rapptycoon.service;

import com.rapptycoon.dto.ReconnectResponse;
import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.*;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameEventRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.repository.RappDeploymentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private BasestationRepository basestationRepository;

    @Mock
    private RappDeploymentRepository rappDeploymentRepository;

    @Mock
    private GameEventRepository gameEventRepository;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(
                playerRepository,
                basestationRepository,
                rappDeploymentRepository,
                gameEventRepository
        );
    }

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("generates a 64-character hex string")
        void generates64CharHexString() {
            String token = playerService.generateToken();

            assertThat(token).hasSize(64);
            assertThat(token).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("generates unique tokens on successive calls")
        void generatesUniqueTokens() {
            String token1 = playerService.generateToken();
            String token2 = playerService.generateToken();

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("returns player when token exists")
        void returnsPlayerWhenTokenExists() {
            Player player = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("TestPlayer")
                    .sessionToken("valid-token-abc123")
                    .connected(true)
                    .build();

            when(playerRepository.findBySessionToken("valid-token-abc123")).thenReturn(Optional.of(player));

            Player result = playerService.validateToken("valid-token-abc123");

            assertThat(result).isEqualTo(player);
            assertThat(result.getDisplayName()).isEqualTo("TestPlayer");
        }

        @Test
        @DisplayName("throws UnauthorizedException when token not found")
        void throwsUnauthorizedWhenTokenNotFound() {
            when(playerRepository.findBySessionToken("invalid-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> playerService.validateToken("invalid-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid session token");
        }
    }

    @Nested
    @DisplayName("disconnect")
    class Disconnect {

        @Test
        @DisplayName("sets connected to false and saves")
        void setsConnectedFalseAndSaves() {
            Player player = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("TestPlayer")
                    .sessionToken("token123")
                    .connected(true)
                    .build();

            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Player result = playerService.disconnect(1L);

            assertThat(result.isConnected()).isFalse();
            verify(playerRepository).save(argThat(p -> !p.isConnected()));
        }

        @Test
        @DisplayName("throws EntityNotFoundException for non-existent player")
        void throwsExceptionForNonExistentPlayer() {
            when(playerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> playerService.disconnect(999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Player not found");
        }
    }

    @Nested
    @DisplayName("reconnect")
    class Reconnect {

        @Test
        @DisplayName("sets connected to true and returns full game state")
        void setsConnectedTrueAndReturnsFullState() {
            Player player = Player.builder()
                    .id(1L)
                    .sessionId(1L)
                    .displayName("TestPlayer")
                    .sessionToken("reconnect-token")
                    .connected(false)
                    .build();

            Basestation bs = Basestation.builder()
                    .id(10L)
                    .playerId(1L)
                    .name("BS-Alpha")
                    .positionX(100)
                    .positionY(200)
                    .health(new BigDecimal("85.50"))
                    .customerExperience(new BigDecimal("92.00"))
                    .cost(new BigDecimal("150.00"))
                    .energyEfficiency(new BigDecimal("78.00"))
                    .automationReliability(new BigDecimal("95.00"))
                    .slaCompliance(new BigDecimal("88.50"))
                    .build();

            RappDeployment deployment = RappDeployment.builder()
                    .id(5L)
                    .templateId(3L)
                    .basestationId(10L)
                    .playerId(1L)
                    .status(DeploymentStatus.ACTIVE)
                    .version(1)
                    .deployedAt(LocalDateTime.of(2025, 1, 15, 10, 6, 0))
                    .build();

            GameEvent event = GameEvent.builder()
                    .id(7L)
                    .sessionId(1L)
                    .basestationId(10L)
                    .eventType("POWER_OUTAGE")
                    .severity(EventSeverity.HIGH)
                    .description("Power supply failure")
                    .escalationLevel(2)
                    .createdAt(LocalDateTime.of(2025, 1, 15, 10, 7, 0))
                    .resolved(false)
                    .build();

            when(playerRepository.findBySessionToken("reconnect-token")).thenReturn(Optional.of(player));
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(basestationRepository.findByPlayerId(1L)).thenReturn(List.of(bs));
            when(rappDeploymentRepository.findByBasestationId(10L)).thenReturn(List.of(deployment));
            when(gameEventRepository.findByBasestationIdAndResolvedFalse(10L)).thenReturn(List.of(event));

            ReconnectResponse response = playerService.reconnect("reconnect-token");

            assertThat(response.player().connected()).isTrue();
            assertThat(response.player().displayName()).isEqualTo("TestPlayer");
            assertThat(response.basestations()).hasSize(1);

            var bsState = response.basestations().get(0);
            assertThat(bsState.id()).isEqualTo(10L);
            assertThat(bsState.name()).isEqualTo("BS-Alpha");
            assertThat(bsState.metrics().health()).isEqualByComparingTo(new BigDecimal("85.50"));
            assertThat(bsState.deployedRapps()).hasSize(1);
            assertThat(bsState.deployedRapps().get(0).templateId()).isEqualTo(3L);
            assertThat(bsState.deployedRapps().get(0).status()).isEqualTo("ACTIVE");
            assertThat(bsState.activeEvents()).hasSize(1);
            assertThat(bsState.activeEvents().get(0).eventType()).isEqualTo("POWER_OUTAGE");
            assertThat(bsState.activeEvents().get(0).escalationLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("throws UnauthorizedException for invalid token")
        void throwsUnauthorizedForInvalidToken() {
            when(playerRepository.findBySessionToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> playerService.reconnect("bad-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid session token");
        }

        @Test
        @DisplayName("returns empty basestations list when player has none")
        void returnsEmptyBasestationsWhenPlayerHasNone() {
            Player player = Player.builder()
                    .id(2L)
                    .sessionId(1L)
                    .displayName("NewPlayer")
                    .sessionToken("new-token")
                    .connected(false)
                    .build();

            when(playerRepository.findBySessionToken("new-token")).thenReturn(Optional.of(player));
            when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(basestationRepository.findByPlayerId(2L)).thenReturn(Collections.emptyList());

            ReconnectResponse response = playerService.reconnect("new-token");

            assertThat(response.player().connected()).isTrue();
            assertThat(response.basestations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPlayersBySession")
    class GetPlayersBySession {

        @Test
        @DisplayName("returns all players for a session")
        void returnsAllPlayersForSession() {
            Player player1 = Player.builder()
                    .id(1L)
                    .sessionId(5L)
                    .displayName("Player1")
                    .sessionToken("token1")
                    .connected(true)
                    .build();

            Player player2 = Player.builder()
                    .id(2L)
                    .sessionId(5L)
                    .displayName("Player2")
                    .sessionToken("token2")
                    .connected(true)
                    .build();

            when(playerRepository.findBySessionId(5L)).thenReturn(List.of(player1, player2));

            List<Player> result = playerService.getPlayersBySession(5L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Player::getDisplayName)
                    .containsExactly("Player1", "Player2");
        }

        @Test
        @DisplayName("returns empty list for session with no players")
        void returnsEmptyListForSessionWithNoPlayers() {
            when(playerRepository.findBySessionId(99L)).thenReturn(Collections.emptyList());

            List<Player> result = playerService.getPlayersBySession(99L);

            assertThat(result).isEmpty();
        }
    }
}
