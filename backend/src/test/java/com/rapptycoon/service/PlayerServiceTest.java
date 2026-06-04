package com.rapptycoon.service;

import com.rapptycoon.exception.UnauthorizedException;
import com.rapptycoon.model.Player;
import com.rapptycoon.repository.BasestationRepository;
import com.rapptycoon.repository.GameEventRepository;
import com.rapptycoon.repository.PlayerRepository;
import com.rapptycoon.repository.RappDeploymentRepository;
import com.rapptycoon.repository.RappTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private RappTemplateRepository rappTemplateRepository;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(
                playerRepository,
                basestationRepository,
                rappDeploymentRepository,
                gameEventRepository,
                rappTemplateRepository
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
}
