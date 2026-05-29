CREATE TABLE rapp_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    purpose TEXT NOT NULL,
    cost DECIMAL(10,2) NOT NULL,
    benefit TEXT NOT NULL,
    risk DECIMAL(5,2) NOT NULL,
    confidence DECIMAL(5,2) NOT NULL,
    side_effects TEXT,
    impact_health DECIMAL(5,2) DEFAULT 0.00,
    impact_customer_experience DECIMAL(5,2) DEFAULT 0.00,
    impact_cost DECIMAL(10,2) DEFAULT 0.00,
    impact_energy_efficiency DECIMAL(5,2) DEFAULT 0.00,
    impact_automation_reliability DECIMAL(5,2) DEFAULT 0.00,
    impact_sla_compliance DECIMAL(5,2) DEFAULT 0.00
);
