CREATE TABLE rapp_deployment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    basestation_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    status ENUM('DEPLOYING', 'ACTIVE', 'DISABLED', 'ROLLING_BACK') NOT NULL,
    version INT NOT NULL DEFAULT 1,
    configuration JSON,
    deployed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (template_id) REFERENCES rapp_template(id),
    FOREIGN KEY (basestation_id) REFERENCES basestation(id),
    FOREIGN KEY (player_id) REFERENCES player(id)
);
