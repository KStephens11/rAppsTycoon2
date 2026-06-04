"""Unit tests for the recommend.py CLI wrapper.

Tests the parse_game_state and recommendation_to_dict functions,
plus end-to-end invocation via subprocess.
"""
import json
import subprocess
import sys
import os

import pytest

from recommend import parse_game_state, recommendation_to_dict
from models import Action, Recommendation


class TestParseGameState:
    """Tests for parse_game_state function."""

    def test_parses_minimal_game_state(self):
        data = {
            "basestations": [
                {
                    "id": 1,
                    "name": "BS-Alpha",
                    "metrics": {"health": 90.0},
                    "deployedRapps": [],
                    "activeEvents": [],
                }
            ],
            "money": 800.0,
            "catalogue": [{"id": 1, "name": "Energy Saver", "cost": 100}],
            "difficulty": "HARD",
        }

        state = parse_game_state(data)

        assert len(state.basestations) == 1
        assert state.basestations[0].id == 1
        assert state.basestations[0].name == "BS-Alpha"
        assert state.basestations[0].metrics["health"] == 90.0
        assert state.money == 800.0
        assert state.difficulty == "HARD"
        assert len(state.catalogue) == 1

    def test_handles_snake_case_keys(self):
        """Should handle both camelCase and snake_case field names."""
        data = {
            "basestations": [
                {
                    "id": 2,
                    "name": "BS-Beta",
                    "metrics": {},
                    "deployed_rapps": [{"templateId": 1, "status": "ACTIVE"}],
                    "active_events": [{"event_type": "POWER_OUTAGE"}],
                }
            ],
            "money": 500.0,
        }

        state = parse_game_state(data)

        assert len(state.basestations[0].deployed_rapps) == 1
        assert len(state.basestations[0].active_events) == 1

    def test_defaults_when_fields_missing(self):
        """Missing fields should use defaults."""
        data = {}

        state = parse_game_state(data)

        assert state.basestations == []
        assert state.money == 1000.0
        assert state.catalogue == []
        assert state.difficulty == "MEDIUM"


class TestRecommendationToDict:
    """Tests for recommendation_to_dict function."""

    def test_converts_deploy_recommendation(self):
        rec = Recommendation(
            action=Action.DEPLOY,
            rapp_template_id=3,
            deployment_id=None,
            basestation_id=1,
            confidence=0.85,
            reasoning="Deploy Fault Predictor to resolve HARDWARE_FAILURE",
        )

        result = recommendation_to_dict(rec)

        assert result["action"] == "DEPLOY"
        assert result["rappTemplateId"] == 3
        assert result["deploymentId"] is None
        assert result["basestationId"] == 1
        assert result["confidence"] == 0.85
        assert "Fault Predictor" in result["reasoning"]

    def test_converts_disable_recommendation(self):
        rec = Recommendation(
            action=Action.DISABLE,
            rapp_template_id=None,
            deployment_id=42,
            basestation_id=5,
            confidence=0.35,
            reasoning="Disable underperforming rApp",
        )

        result = recommendation_to_dict(rec)

        assert result["action"] == "DISABLE"
        assert result["rappTemplateId"] is None
        assert result["deploymentId"] == 42
        assert result["basestationId"] == 5

    def test_confidence_rounded_to_4_decimal_places(self):
        rec = Recommendation(
            action=Action.DEPLOY,
            rapp_template_id=1,
            deployment_id=None,
            basestation_id=1,
            confidence=0.123456789,
            reasoning="Test rounding",
        )

        result = recommendation_to_dict(rec)

        assert result["confidence"] == 0.1235


class TestRecommendCLI:
    """End-to-end tests for recommend.py as a CLI tool."""

    @pytest.fixture
    def bot_player_dir(self):
        """Return the bot-player directory path."""
        return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    def test_valid_input_produces_recommendations(self, bot_player_dir):
        """Valid JSON input should produce a recommendations array."""
        input_data = json.dumps({
            "basestations": [
                {
                    "id": 1,
                    "name": "BS-Alpha",
                    "metrics": {
                        "health": 60.0,
                        "customerExperience": 50.0,
                        "cost": 80.0,
                        "energyEfficiency": 40.0,
                        "automationReliability": 55.0,
                        "slaCompliance": 60.0,
                    },
                    "deployedRapps": [],
                    "activeEvents": [
                        {"eventType": "POWER_OUTAGE", "severity": "HIGH", "escalationLevel": 1}
                    ],
                }
            ],
            "money": 1000.0,
            "catalogue": [
                {"id": 1, "name": "Energy Saver", "cost": 100.0},
                {"id": 3, "name": "Fault Predictor", "cost": 80.0},
            ],
            "difficulty": "MEDIUM",
        })

        result = subprocess.run(
            [sys.executable, "recommend.py"],
            input=input_data,
            capture_output=True,
            text=True,
            cwd=bot_player_dir,
        )

        assert result.returncode == 0
        output = json.loads(result.stdout)
        assert "recommendations" in output
        assert len(output["recommendations"]) > 0
        assert len(output["recommendations"]) <= 5

        # Verify structure of first recommendation
        first = output["recommendations"][0]
        assert "action" in first
        assert "confidence" in first
        assert "reasoning" in first
        assert 0.0 <= first["confidence"] <= 1.0

    def test_empty_input_fails(self, bot_player_dir):
        """Empty stdin should cause exit code 1."""
        result = subprocess.run(
            [sys.executable, "recommend.py"],
            input="",
            capture_output=True,
            text=True,
            cwd=bot_player_dir,
        )
        assert result.returncode == 1

    def test_invalid_json_fails(self, bot_player_dir):
        """Invalid JSON input should cause exit code 1."""
        result = subprocess.run(
            [sys.executable, "recommend.py"],
            input="not valid json{{{",
            capture_output=True,
            text=True,
            cwd=bot_player_dir,
        )
        assert result.returncode == 1

    def test_max_5_recommendations_returned(self, bot_player_dir):
        """Output should contain at most 5 recommendations."""
        input_data = json.dumps({
            "basestations": [
                {
                    "id": 1,
                    "name": "BS-Alpha",
                    "metrics": {
                        "health": 30.0,
                        "customerExperience": 30.0,
                        "cost": 90.0,
                        "energyEfficiency": 30.0,
                        "automationReliability": 30.0,
                        "slaCompliance": 30.0,
                    },
                    "deployedRapps": [],
                    "activeEvents": [
                        {"eventType": "POWER_OUTAGE", "severity": "CRITICAL", "escalationLevel": 2},
                        {"eventType": "TRAFFIC_SPIKE", "severity": "HIGH", "escalationLevel": 1},
                        {"eventType": "HARDWARE_FAILURE", "severity": "MEDIUM", "escalationLevel": 0},
                    ],
                }
            ],
            "money": 5000.0,
            "catalogue": [
                {"id": i, "name": f"rApp-{i}", "cost": 50.0}
                for i in range(1, 8)
            ],
            "difficulty": "MEDIUM",
        })

        result = subprocess.run(
            [sys.executable, "recommend.py"],
            input=input_data,
            capture_output=True,
            text=True,
            cwd=bot_player_dir,
        )

        assert result.returncode == 0
        output = json.loads(result.stdout)
        assert len(output["recommendations"]) <= 5
