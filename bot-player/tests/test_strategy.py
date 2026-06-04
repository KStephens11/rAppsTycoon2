"""Unit tests for the strategy module (rank_actions).

Tests the core recommendation logic including:
- Event resolution scoring
- Metric improvement scoring
- Conflict detection and penalties
- Cost penalties and affordability
- TUNE/DISABLE recommendations for underperforming rApps
- Correct sorting by confidence
"""
import pytest

from models import Action, BasestationState, GameState, Recommendation
from strategy import (
    rank_actions,
    _has_conflict,
    _compute_cost_penalty,
    _compute_metric_improvement,
    _is_already_deployed,
    _get_deployed_template_ids,
    _is_underperforming,
    BASE_EVENT_RESOLVE_SCORE,
    BASE_METRIC_IMPROVE_SCORE,
    CONFLICT_PENALTY_AMOUNT,
    COST_PENALTY_AMOUNT,
    COST_PENALTY_THRESHOLD,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

SAMPLE_CATALOGUE = [
    {"id": 1, "name": "Energy Saver", "cost": 100.0},
    {"id": 2, "name": "Capacity Optimiser", "cost": 120.0},
    {"id": 3, "name": "Fault Predictor", "cost": 80.0},
    {"id": 4, "name": "SLA Guardian", "cost": 110.0},
    {"id": 5, "name": "Config Drift Detector", "cost": 90.0},
    {"id": 6, "name": "Traffic Balancer", "cost": 100.0},
    {"id": 7, "name": "Alarm Noise Reducer", "cost": 70.0},
]


def make_basestation(
    bs_id=1,
    name="BS-Alpha",
    metrics=None,
    deployed_rapps=None,
    active_events=None,
):
    """Helper to create a BasestationState."""
    if metrics is None:
        metrics = {
            "health": 80.0,
            "customerExperience": 75.0,
            "cost": 50.0,
            "energyEfficiency": 70.0,
            "automationReliability": 65.0,
            "slaCompliance": 72.0,
        }
    return BasestationState(
        id=bs_id,
        name=name,
        metrics=metrics,
        deployed_rapps=deployed_rapps or [],
        active_events=active_events or [],
    )


def make_game_state(
    basestations=None,
    money=1000.0,
    catalogue=None,
    difficulty="MEDIUM",
):
    """Helper to create a GameState."""
    return GameState(
        basestations=basestations or [make_basestation()],
        money=money,
        catalogue=catalogue or SAMPLE_CATALOGUE,
        difficulty=difficulty,
    )


# ---------------------------------------------------------------------------
# Tests: rank_actions — Event Resolution
# ---------------------------------------------------------------------------


class TestEventResolution:
    """Tests for event-resolving recommendations."""

    def test_power_outage_recommends_energy_saver_and_fault_predictor(self):
        """POWER_OUTAGE should recommend templates 1 and 3."""
        bs = make_basestation(
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 1}
            ]
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        deploy_recs = [r for r in recs if r.action == Action.DEPLOY]
        event_template_ids = {r.rapp_template_id for r in deploy_recs if r.confidence >= 0.5}
        assert 1 in event_template_ids
        assert 3 in event_template_ids

    def test_traffic_spike_recommends_capacity_optimiser_and_traffic_balancer(self):
        """TRAFFIC_SPIKE should recommend templates 2 and 6."""
        bs = make_basestation(
            active_events=[
                {"eventType": "TRAFFIC_SPIKE", "severity": "MEDIUM", "escalationLevel": 0}
            ]
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        deploy_recs = [r for r in recs if r.action == Action.DEPLOY]
        event_template_ids = {r.rapp_template_id for r in deploy_recs if r.confidence >= 0.5}
        assert 2 in event_template_ids
        assert 6 in event_template_ids

    def test_event_resolving_scores_higher_than_metric_improvement(self):
        """Event-resolving actions should score higher than metric improvement."""
        bs = make_basestation(
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 0}
            ]
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        # Top recommendation should be event-resolving (score ~0.9)
        assert recs[0].confidence >= BASE_EVENT_RESOLVE_SCORE - COST_PENALTY_AMOUNT

    def test_already_deployed_rapp_not_recommended_again(self):
        """If an effective rApp is already deployed, it should not be recommended."""
        bs = make_basestation(
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 0}
            ],
            deployed_rapps=[
                {"id": 10, "templateId": 1, "status": "ACTIVE", "version": 1}
            ],
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        # Template 1 should not appear in event-resolving recs
        event_recs = [
            r for r in recs
            if r.action == Action.DEPLOY
            and r.confidence >= BASE_EVENT_RESOLVE_SCORE - 0.3
            and r.rapp_template_id == 1
        ]
        assert len(event_recs) == 0

    def test_cannot_afford_rapp_not_recommended(self):
        """If bot cannot afford the rApp, it should not be recommended."""
        bs = make_basestation(
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 0}
            ]
        )
        # Only 50 money — cannot afford Energy Saver (100) or Fault Predictor (80)
        state = make_game_state(basestations=[bs], money=50.0)
        recs = rank_actions(state)

        event_recs = [
            r for r in recs
            if r.action == Action.DEPLOY and r.rapp_template_id in (1, 3)
        ]
        assert len(event_recs) == 0

    def test_unknown_event_type_produces_no_event_recs(self):
        """Unknown event types should not produce event-resolving recommendations."""
        bs = make_basestation(
            active_events=[
                {"eventType": "UNKNOWN_EVENT", "severity": "LOW", "escalationLevel": 0}
            ]
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        # Should still produce metric improvement recs, but no high-confidence event recs
        high_confidence = [r for r in recs if r.confidence >= BASE_EVENT_RESOLVE_SCORE - 0.1]
        assert len(high_confidence) == 0


# ---------------------------------------------------------------------------
# Tests: rank_actions — Conflict Detection
# ---------------------------------------------------------------------------


class TestConflictDetection:
    """Tests for conflict detection between rApp pairs."""

    def test_energy_saver_conflicts_with_capacity_optimiser(self):
        """Templates 1 and 2 are a conflict pair."""
        assert _has_conflict({2}, 1) is True
        assert _has_conflict({1}, 2) is True

    def test_fault_predictor_conflicts_with_alarm_noise_reducer(self):
        """Templates 3 and 7 are a conflict pair."""
        assert _has_conflict({7}, 3) is True
        assert _has_conflict({3}, 7) is True

    def test_traffic_balancer_conflicts_with_energy_saver(self):
        """Templates 6 and 1 are a conflict pair."""
        assert _has_conflict({1}, 6) is True
        assert _has_conflict({6}, 1) is True

    def test_no_conflict_between_non_paired_rapps(self):
        """Non-conflicting pairs should return False."""
        assert _has_conflict({4}, 5) is False
        assert _has_conflict({2}, 3) is False
        assert _has_conflict({5}, 7) is False

    def test_conflict_penalty_applied_to_score(self):
        """Deploying a conflicting rApp should reduce confidence by penalty amount."""
        bs = make_basestation(
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 0}
            ],
            deployed_rapps=[
                {"id": 10, "templateId": 2, "status": "ACTIVE", "version": 1}
            ],
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        # Template 1 (Energy Saver) conflicts with template 2 (Capacity Optimiser)
        template_1_rec = next(
            (r for r in recs if r.rapp_template_id == 1 and r.action == Action.DEPLOY),
            None,
        )
        assert template_1_rec is not None
        # Score should be reduced by conflict penalty
        assert template_1_rec.confidence <= BASE_EVENT_RESOLVE_SCORE - CONFLICT_PENALTY_AMOUNT + 0.01


# ---------------------------------------------------------------------------
# Tests: rank_actions — Cost Penalty
# ---------------------------------------------------------------------------


class TestCostPenalty:
    """Tests for cost penalty when money would drop below threshold."""

    def test_no_penalty_when_money_sufficient(self):
        """No penalty when remaining money is above threshold."""
        penalty = _compute_cost_penalty(1000.0, 100.0)
        assert penalty == 0.0

    def test_penalty_when_below_threshold(self):
        """Penalty applied when remaining money drops below 200."""
        # 250 money - 100 cost = 150 remaining (below 200 threshold)
        penalty = _compute_cost_penalty(250.0, 100.0)
        assert penalty == COST_PENALTY_AMOUNT

    def test_max_penalty_when_cannot_afford(self):
        """Maximum penalty when cost exceeds money."""
        penalty = _compute_cost_penalty(50.0, 100.0)
        assert penalty == 1.0

    def test_no_penalty_at_exact_threshold(self):
        """No penalty when remaining equals threshold exactly."""
        # 300 money - 100 cost = 200 remaining (exactly at threshold)
        penalty = _compute_cost_penalty(300.0, 100.0)
        assert penalty == 0.0


# ---------------------------------------------------------------------------
# Tests: rank_actions — Metric Improvement
# ---------------------------------------------------------------------------


class TestMetricImprovement:
    """Tests for metric improvement scoring."""

    def test_improvement_positive_when_metrics_low(self):
        """Metric improvement should be positive when metrics are below 100."""
        metrics = {
            "health": 60.0,
            "customerExperience": 50.0,
            "cost": 80.0,
            "energyEfficiency": 40.0,
            "automationReliability": 55.0,
            "slaCompliance": 60.0,
        }
        # Template 2 (Capacity Optimiser) improves health, customerExperience,
        # automationReliability, slaCompliance
        improvement = _compute_metric_improvement(metrics, 2)
        assert improvement > 0.0

    def test_improvement_zero_when_all_metrics_at_max(self):
        """No improvement possible when all metrics are at 100."""
        metrics = {
            "health": 100.0,
            "customerExperience": 100.0,
            "cost": 0.0,
            "energyEfficiency": 100.0,
            "automationReliability": 100.0,
            "slaCompliance": 100.0,
        }
        improvement = _compute_metric_improvement(metrics, 2)
        assert improvement == 0.0

    def test_energy_saver_benefits_from_high_cost(self):
        """Energy Saver reduces cost — beneficial when cost is high."""
        metrics = {
            "health": 90.0,
            "customerExperience": 90.0,
            "cost": 80.0,  # High cost — Energy Saver reduces it
            "energyEfficiency": 60.0,
            "automationReliability": 90.0,
            "slaCompliance": 90.0,
        }
        improvement = _compute_metric_improvement(metrics, 1)
        assert improvement > 0.0

    def test_unknown_template_returns_zero(self):
        """Unknown template ID should return 0 improvement."""
        metrics = {"health": 50.0}
        improvement = _compute_metric_improvement(metrics, 999)
        assert improvement == 0.0

    def test_metric_improvement_recs_have_reasoning(self):
        """Metric improvement recommendations should include reasoning."""
        bs = make_basestation(
            metrics={
                "health": 60.0,
                "customerExperience": 50.0,
                "cost": 80.0,
                "energyEfficiency": 40.0,
                "automationReliability": 55.0,
                "slaCompliance": 60.0,
            }
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        metric_recs = [
            r for r in recs
            if r.action == Action.DEPLOY and r.confidence < BASE_EVENT_RESOLVE_SCORE
        ]
        for rec in metric_recs:
            assert rec.reasoning
            assert "improve metrics" in rec.reasoning.lower() or "deploy" in rec.reasoning.lower()


# ---------------------------------------------------------------------------
# Tests: rank_actions — TUNE/DISABLE Recommendations
# ---------------------------------------------------------------------------


class TestTuneDisable:
    """Tests for TUNE and DISABLE recommendations on underperforming rApps."""

    def test_underperforming_rapp_gets_disable_recommendation(self):
        """An underperforming rApp should receive a DISABLE recommendation."""
        # Capacity Optimiser on a basestation with all metrics at max
        # Its energyEfficiency impact is -5, and with no room to improve other metrics,
        # it produces a negative net score → underperforming
        bs = make_basestation(
            metrics={
                "health": 100.0,
                "customerExperience": 100.0,
                "cost": 0.0,
                "energyEfficiency": 100.0,
                "automationReliability": 100.0,
                "slaCompliance": 100.0,
            },
            deployed_rapps=[
                {"id": 10, "templateId": 2, "name": "Capacity Optimiser", "status": "ACTIVE", "version": 1}
            ],
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        disable_recs = [r for r in recs if r.action == Action.DISABLE and r.deployment_id == 10]
        assert len(disable_recs) >= 1

    def test_underperforming_rapp_gets_tune_recommendation(self):
        """An underperforming rApp should also receive a TUNE recommendation."""
        bs = make_basestation(
            metrics={
                "health": 100.0,
                "customerExperience": 100.0,
                "cost": 0.0,
                "energyEfficiency": 100.0,
                "automationReliability": 100.0,
                "slaCompliance": 100.0,
            },
            deployed_rapps=[
                {"id": 10, "templateId": 2, "name": "Capacity Optimiser", "status": "ACTIVE", "version": 1}
            ],
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        tune_recs = [r for r in recs if r.action == Action.TUNE and r.deployment_id == 10]
        assert len(tune_recs) >= 1

    def test_well_performing_rapp_not_recommended_for_disable(self):
        """A rApp that is providing net positive value should not be recommended for DISABLE."""
        bs = make_basestation(
            metrics={
                "health": 50.0,
                "customerExperience": 50.0,
                "cost": 80.0,
                "energyEfficiency": 40.0,
                "automationReliability": 50.0,
                "slaCompliance": 50.0,
            },
            deployed_rapps=[
                {"id": 10, "templateId": 3, "name": "Fault Predictor", "status": "ACTIVE", "version": 1}
            ],
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        disable_recs = [r for r in recs if r.action == Action.DISABLE and r.deployment_id == 10]
        assert len(disable_recs) == 0

    def test_deploying_rapp_not_considered_for_tune(self):
        """Only ACTIVE rApps should be considered for TUNE/DISABLE."""
        bs = make_basestation(
            metrics={
                "health": 100.0,
                "customerExperience": 100.0,
                "cost": 0.0,
                "energyEfficiency": 100.0,
                "automationReliability": 100.0,
                "slaCompliance": 100.0,
            },
            deployed_rapps=[
                {"id": 10, "templateId": 1, "name": "Energy Saver", "status": "DEPLOYING", "version": 1}
            ],
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        tune_disable_recs = [r for r in recs if r.action in (Action.TUNE, Action.DISABLE)]
        assert len(tune_disable_recs) == 0


# ---------------------------------------------------------------------------
# Tests: rank_actions — Sorting and General Behaviour
# ---------------------------------------------------------------------------


class TestSortingAndGeneral:
    """Tests for result ordering and general behaviour."""

    def test_results_sorted_by_confidence_descending(self):
        """Recommendations should be sorted highest confidence first."""
        bs = make_basestation(
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 0}
            ]
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        assert len(recs) > 1
        for i in range(len(recs) - 1):
            assert recs[i].confidence >= recs[i + 1].confidence

    def test_empty_basestations_returns_empty_list(self):
        """No basestations should produce no recommendations."""
        state = GameState(basestations=[], money=1000.0, catalogue=[], difficulty="MEDIUM")
        recs = rank_actions(state)
        assert recs == []

    def test_no_events_no_low_metrics_produces_no_recs_for_max_metrics(self):
        """Basestations at max metrics with no events should produce minimal/no recs."""
        bs = make_basestation(
            metrics={
                "health": 100.0,
                "customerExperience": 100.0,
                "cost": 0.0,
                "energyEfficiency": 100.0,
                "automationReliability": 100.0,
                "slaCompliance": 100.0,
            }
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        # Only metric improvement recs with score > 0 — should be empty since all at max
        deploy_recs = [r for r in recs if r.action == Action.DEPLOY]
        assert len(deploy_recs) == 0

    def test_multiple_basestations_produces_recs_for_each(self):
        """Each basestation with events should produce recommendations."""
        bs1 = make_basestation(
            bs_id=1,
            name="BS-Alpha",
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 0}
            ],
        )
        bs2 = make_basestation(
            bs_id=2,
            name="BS-Beta",
            active_events=[
                {"eventType": "TRAFFIC_SPIKE", "severity": "MEDIUM", "escalationLevel": 0}
            ],
        )
        state = make_game_state(basestations=[bs1, bs2])
        recs = rank_actions(state)

        bs1_recs = [r for r in recs if r.basestation_id == 1]
        bs2_recs = [r for r in recs if r.basestation_id == 2]
        assert len(bs1_recs) > 0
        assert len(bs2_recs) > 0

    def test_all_recommendations_have_valid_confidence(self):
        """All recommendations should have confidence between 0.0 and 1.0."""
        bs = make_basestation(
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 0}
            ]
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        for rec in recs:
            assert 0.0 <= rec.confidence <= 1.0

    def test_all_recommendations_have_reasoning(self):
        """All recommendations should have non-empty reasoning strings."""
        bs = make_basestation(
            active_events=[
                {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 0}
            ]
        )
        state = make_game_state(basestations=[bs])
        recs = rank_actions(state)

        for rec in recs:
            assert rec.reasoning
            assert len(rec.reasoning) > 0


# ---------------------------------------------------------------------------
# Tests: Helper Functions
# ---------------------------------------------------------------------------


class TestHelpers:
    """Tests for internal helper functions."""

    def test_is_already_deployed_active(self):
        bs = make_basestation(
            deployed_rapps=[{"templateId": 3, "status": "ACTIVE"}]
        )
        assert _is_already_deployed(bs, 3) is True

    def test_is_already_deployed_deploying(self):
        bs = make_basestation(
            deployed_rapps=[{"templateId": 5, "status": "DEPLOYING"}]
        )
        assert _is_already_deployed(bs, 5) is True

    def test_is_not_deployed(self):
        bs = make_basestation(
            deployed_rapps=[{"templateId": 3, "status": "ACTIVE"}]
        )
        assert _is_already_deployed(bs, 7) is False

    def test_disabled_rapp_not_counted_as_deployed(self):
        bs = make_basestation(
            deployed_rapps=[{"templateId": 3, "status": "DISABLED"}]
        )
        assert _is_already_deployed(bs, 3) is False

    def test_get_deployed_template_ids(self):
        bs = make_basestation(
            deployed_rapps=[
                {"templateId": 1, "status": "ACTIVE"},
                {"templateId": 3, "status": "DEPLOYING"},
                {"templateId": 5, "status": "DISABLED"},
            ]
        )
        ids = _get_deployed_template_ids(bs)
        assert ids == {1, 3}
