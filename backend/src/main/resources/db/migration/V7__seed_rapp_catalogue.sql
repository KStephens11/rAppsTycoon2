-- Seed data for rApp catalogue (7 rApps from GAME_RULES.md)

INSERT INTO rapp_template (name, cost, risk, confidence, purpose, benefit, side_effects,
                           impact_health, impact_customer_experience, impact_cost,
                           impact_energy_efficiency, impact_automation_reliability, impact_sla_compliance)
VALUES
    ('Energy Saver', 50.00, 25.00, 80.00,
     'Reduces power consumption across basestation cells',
     'Lowers energy costs by optimising power usage during low-traffic periods',
     'May increase latency in high-load cells',
     0.00, -5.00, -30.00, 20.00, 0.00, -3.00),

    ('Capacity Optimiser', 75.00, 15.00, 85.00,
     'Dynamically allocates capacity based on demand patterns',
     'Improves customer experience by reducing congestion',
     'Higher operational cost during peak hours',
     5.00, 15.00, 20.00, -5.00, 5.00, 10.00),

    ('Fault Predictor', 60.00, 20.00, 70.00,
     'Predicts hardware failures before they occur',
     'Reduces downtime by enabling proactive maintenance',
     'False positives may trigger unnecessary maintenance',
     15.00, 5.00, 10.00, 0.00, 10.00, 8.00),

    ('SLA Guardian', 45.00, 10.00, 90.00,
     'Monitors and enforces SLA compliance thresholds',
     'Prevents SLA breaches by auto-adjusting network parameters',
     'May over-prioritise SLA metrics at expense of cost',
     5.00, 10.00, 15.00, -2.00, 5.00, 20.00),

    ('Configuration Drift Detector', 35.00, 5.00, 95.00,
     'Detects when basestation config deviates from baseline',
     'Maintains consistency and prevents silent degradation',
     'Alert fatigue if thresholds are too sensitive',
     10.00, 3.00, 5.00, 2.00, 15.00, 5.00),

    ('Traffic Balancer', 65.00, 20.00, 75.00,
     'Distributes traffic load across cells to prevent congestion',
     'Improves overall network throughput and user experience',
     'May cause brief handover interruptions during rebalancing',
     8.00, 12.00, 10.00, -3.00, 5.00, 7.00),

    ('Alarm Noise Reducer', 30.00, 15.00, 80.00,
     'Filters and correlates alarms to reduce noise',
     'Reduces operator fatigue and highlights real issues',
     'May suppress genuine alarms if correlation rules are too aggressive',
     5.00, 2.00, -5.00, 0.00, 12.00, 3.00);
