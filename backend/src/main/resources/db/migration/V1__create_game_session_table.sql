CREATE TABLE game_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_code VARCHAR(8) UNIQUE NOT NULL,
    state ENUM('LOBBY', 'ACTIVE', 'COMPLETED') NOT NULL DEFAULT 'LOBBY',
    host_player_id BIGINT,
    max_players INT NOT NULL DEFAULT 6,
    current_tick INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL
);
