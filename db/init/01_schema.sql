CREATE TABLE IF NOT EXISTS game_session (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(8)   NOT NULL UNIQUE,
    status        ENUM('LOBBY','ACTIVE','COMPLETED') NOT NULL DEFAULT 'LOBBY',
    host_player_id BIGINT,
    current_tick  INT          NOT NULL DEFAULT 0,
    total_ticks   INT          NOT NULL DEFAULT 60,
    tick_interval_ms INT       NOT NULL DEFAULT 5000,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT       NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    token         VARCHAR(128) NOT NULL UNIQUE,
    money         DECIMAL(10,2) NOT NULL DEFAULT 1000.00,
    is_connected  BOOLEAN      NOT NULL DEFAULT TRUE,
    joined_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_player_session FOREIGN KEY (session_id) REFERENCES game_session(id)
);

ALTER TABLE game_session
    ADD CONSTRAINT fk_session_host FOREIGN KEY (host_player_id) REFERENCES player(id);

CREATE TABLE IF NOT EXISTS basestation (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id            BIGINT       NOT NULL,
    player_id             BIGINT       NOT NULL,
    name                  VARCHAR(32)  NOT NULL,
    health                DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    customer_experience   DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    cost                  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    energy_efficiency     DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    automation_reliability DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    sla_compliance        DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    CONSTRAINT fk_bs_session FOREIGN KEY (session_id) REFERENCES game_session(id),
    CONSTRAINT fk_bs_player  FOREIGN KEY (player_id)  REFERENCES player(id)
);

CREATE TABLE IF NOT EXISTS rapp_template (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(64)  NOT NULL UNIQUE,
    cost                  DECIMAL(8,2) NOT NULL,
    risk_pct              DECIMAL(5,2) NOT NULL,
    confidence_pct        DECIMAL(5,2) NOT NULL,
    delta_health          DECIMAL(5,2) NOT NULL DEFAULT 0,
    delta_customer_exp    DECIMAL(5,2) NOT NULL DEFAULT 0,
    delta_cost            DECIMAL(5,2) NOT NULL DEFAULT 0,
    delta_energy_eff      DECIMAL(5,2) NOT NULL DEFAULT 0,
    delta_auto_rel        DECIMAL(5,2) NOT NULL DEFAULT 0,
    delta_sla_compliance  DECIMAL(5,2) NOT NULL DEFAULT 0
);

INSERT INTO rapp_template (name, cost, risk_pct, confidence_pct,
    delta_health, delta_customer_exp, delta_cost, delta_energy_eff, delta_auto_rel, delta_sla_compliance)
VALUES
    ('Energy Saver',          50, 25, 80,   0,  -5, -30, +20,   0,  -3),
    ('Capacity Optimiser',    75, 15, 85,  +5, +15, +20,  -5,  +5, +10),
    ('Fault Predictor',       60, 20, 70, +15,  +5, +10,   0, +10,  +8),
    ('SLA Guardian',          45, 10, 90,  +5, +10, +15,  -2,  +5, +20),
    ('Config Drift Detector', 35,  5, 95, +10,  +3,  +5,  +2, +15,  +5),
    ('Traffic Balancer',      65, 20, 75,  +8, +12, +10,  -3,  +5,  +7),
    ('Alarm Noise Reducer',   30, 15, 80,  +5,  +2,  -5,   0, +12,  +3)
ON DUPLICATE KEY UPDATE name = name;

CREATE TABLE IF NOT EXISTS rapp_deployment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    player_id       BIGINT       NOT NULL,
    basestation_id  BIGINT       NOT NULL,
    template_id     BIGINT       NOT NULL,
    status          ENUM('DEPLOYING','ACTIVE','DISABLED','ROLLING_BACK') NOT NULL DEFAULT 'DEPLOYING',
    version         INT          NOT NULL DEFAULT 1,
    aggressiveness  ENUM('LOW','MODERATE','HIGH') NOT NULL DEFAULT 'MODERATE',
    threshold       INT          NOT NULL DEFAULT 50,
    prev_aggressiveness ENUM('LOW','MODERATE','HIGH') NULL,
    prev_threshold  INT          NULL,
    deployed_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rd_session     FOREIGN KEY (session_id)     REFERENCES game_session(id),
    CONSTRAINT fk_rd_player      FOREIGN KEY (player_id)      REFERENCES player(id),
    CONSTRAINT fk_rd_basestation FOREIGN KEY (basestation_id) REFERENCES basestation(id),
    CONSTRAINT fk_rd_template    FOREIGN KEY (template_id)    REFERENCES rapp_template(id)
);

CREATE TABLE IF NOT EXISTS game_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    basestation_id  BIGINT       NOT NULL,
    event_type      ENUM('POWER_OUTAGE','TRAFFIC_SPIKE','HARDWARE_FAILURE',
                         'SLA_BREACH','INTERFERENCE','CAPACITY_OVERFLOW') NOT NULL,
    severity        ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    escalation_level INT         NOT NULL DEFAULT 0,
    ticks_at_max    INT          NOT NULL DEFAULT 0,
    is_resolved     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at_tick INT          NOT NULL,
    resolved_at_tick INT         NULL,
    CONSTRAINT fk_event_session     FOREIGN KEY (session_id)     REFERENCES game_session(id),
    CONSTRAINT fk_event_basestation FOREIGN KEY (basestation_id) REFERENCES basestation(id)
);
