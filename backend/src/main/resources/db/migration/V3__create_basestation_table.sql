CREATE TABLE basestation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    position_x INT NOT NULL,
    position_y INT NOT NULL,
    health DECIMAL(5,2) DEFAULT 100.00,
    customer_experience DECIMAL(5,2) DEFAULT 100.00,
    cost DECIMAL(10,2) DEFAULT 0.00,
    energy_efficiency DECIMAL(5,2) DEFAULT 100.00,
    automation_reliability DECIMAL(5,2) DEFAULT 100.00,
    sla_compliance DECIMAL(5,2) DEFAULT 100.00,
    FOREIGN KEY (player_id) REFERENCES player(id)
);
