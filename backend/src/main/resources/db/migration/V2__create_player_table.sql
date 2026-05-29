CREATE TABLE player (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    session_token VARCHAR(64) UNIQUE NOT NULL,
    score_money DECIMAL(10,2) DEFAULT 1000.00,
    score_satisfaction DECIMAL(5,2) DEFAULT 100.00,
    score_stability DECIMAL(5,2) DEFAULT 100.00,
    composite_score DECIMAL(10,2) DEFAULT 0.00,
    connected BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (session_id) REFERENCES game_session(id)
);
