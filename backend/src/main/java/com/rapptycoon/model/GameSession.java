package com.rapptycoon.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", unique = true, nullable = false, length = 8)
    private String sessionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private GameSessionState state;

    @Column(name = "host_player_id")
    private Long hostPlayerId;

    @Column(name = "max_players", nullable = false)
    @Builder.Default
    private int maxPlayers = 6;

    @Column(name = "current_tick", nullable = false)
    @Builder.Default
    private int currentTick = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
