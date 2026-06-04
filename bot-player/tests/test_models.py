"""Unit tests for the models module.

Tests data classes and validation logic for:
- Action enum
- BasestationState
- GameState
- Recommendation validation
"""
import pytest

from models import Action, BasestationState, GameState, Recommendation


class TestAction:
    """Tests for the Action enum."""

    def test_deploy_value(self):
        assert Action.DEPLOY.value == "DEPLOY"

    def test_tune_value(self):
        assert Action.TUNE.value == "TUNE"

    def test_disable_value(self):
        assert Action.DISABLE.value == "DISABLE"

    def test_rollback_value(self):
        assert Action.ROLLBACK.value == "ROLLBACK"

    def test_action_is_string_enum(self):
        """Action values should be usable as strings."""
        assert Action.DEPLOY == "DEPLOY"
        assert str(Action.DEPLOY) == "Action.DEPLOY"


class TestBasestationState:
    """Tests for BasestationState data class."""

    def test_defaults(self):
        bs = BasestationState(id=1, name="BS-1")
        assert bs.id == 1
        assert bs.name == "BS-1"
        assert bs.metrics == {}
        assert bs.deployed_rapps == []
        assert bs.active_events == []

    def test_with_all_fields(self):
        bs = BasestationState(
            id=5,
            name="BS-Alpha",
            metrics={"health": 90.0, "customerExperience": 85.0},
            deployed_rapps=[{"id": 10, "templateId": 1, "status": "ACTIVE"}],
            active_events=[{"eventType": "POWER_OUTAGE", "severity": "HIGH"}],
        )
        assert bs.id == 5
        assert bs.name == "BS-Alpha"
        assert bs.metrics["health"] == 90.0
        assert len(bs.deployed_rapps) == 1
        assert len(bs.active_events) == 1


class TestGameState:
    """Tests for GameState data class."""

    def test_defaults(self):
        gs = GameState()
        assert gs.basestations == []
        assert gs.money == 1000.0
        assert gs.catalogue == []
        assert gs.difficulty == "MEDIUM"

    def test_custom_values(self):
        bs = BasestationState(id=1, name="Test")
        gs = GameState(
            basestations=[bs],
            money=500.0,
            catalogue=[{"id": 1, "name": "Test rApp", "cost": 100}],
            difficulty="HARD",
        )
        assert len(gs.basestations) == 1
        assert gs.money == 500.0
        assert gs.difficulty == "HARD"


class TestRecommendation:
    """Tests for Recommendation data class and validation."""

    def test_valid_recommendation(self):
        rec = Recommendation(
            action=Action.DEPLOY,
            rapp_template_id=1,
            deployment_id=None,
            basestation_id=10,
            confidence=0.85,
            reasoning="Deploy Energy Saver to resolve POWER_OUTAGE",
        )
        assert rec.action == Action.DEPLOY
        assert rec.rapp_template_id == 1
        assert rec.deployment_id is None
        assert rec.basestation_id == 10
        assert rec.confidence == 0.85
        assert "Energy Saver" in rec.reasoning

    def test_confidence_must_be_between_0_and_1(self):
        """Confidence outside [0.0, 1.0] should raise ValueError."""
        with pytest.raises(ValueError, match="confidence must be between"):
            Recommendation(
                action=Action.DEPLOY,
                rapp_template_id=1,
                deployment_id=None,
                basestation_id=10,
                confidence=1.5,
                reasoning="Test",
            )

    def test_negative_confidence_raises(self):
        with pytest.raises(ValueError, match="confidence must be between"):
            Recommendation(
                action=Action.DEPLOY,
                rapp_template_id=1,
                deployment_id=None,
                basestation_id=10,
                confidence=-0.1,
                reasoning="Test",
            )

    def test_empty_reasoning_raises(self):
        """Empty reasoning should raise ValueError."""
        with pytest.raises(ValueError, match="reasoning must be a non-empty string"):
            Recommendation(
                action=Action.DEPLOY,
                rapp_template_id=1,
                deployment_id=None,
                basestation_id=10,
                confidence=0.5,
                reasoning="",
            )

    def test_action_coerced_from_string(self):
        """Action field should accept string value and coerce to enum."""
        rec = Recommendation(
            action="DEPLOY",
            rapp_template_id=1,
            deployment_id=None,
            basestation_id=10,
            confidence=0.5,
            reasoning="Test recommendation",
        )
        assert rec.action == Action.DEPLOY

    def test_boundary_confidence_zero(self):
        rec = Recommendation(
            action=Action.DISABLE,
            rapp_template_id=None,
            deployment_id=5,
            basestation_id=10,
            confidence=0.0,
            reasoning="Minimal confidence",
        )
        assert rec.confidence == 0.0

    def test_boundary_confidence_one(self):
        rec = Recommendation(
            action=Action.DEPLOY,
            rapp_template_id=2,
            deployment_id=None,
            basestation_id=10,
            confidence=1.0,
            reasoning="Maximum confidence",
        )
        assert rec.confidence == 1.0
