"""Unit tests for the config module.

Tests BotConfig loading from environment variables including:
- Required field validation
- Difficulty parsing and defaults
- Health port validation
- Response delay calculation
"""
import os

import pytest

from config import BotConfig, Difficulty, RESPONSE_DELAYS


class TestBotConfigFromEnv:
    """Tests for BotConfig.from_env()."""

    def _set_required_env(self, monkeypatch):
        """Set all required environment variables."""
        monkeypatch.setenv("SESSION_CODE", "ABCD1234")
        monkeypatch.setenv("SESSION_TOKEN", "a" * 64)
        monkeypatch.setenv("BACKEND_BASE_URL", "http://localhost:8080")

    def test_loads_with_all_required_vars(self, monkeypatch):
        self._set_required_env(monkeypatch)
        config = BotConfig.from_env()
        assert config.session_code == "ABCD1234"
        assert config.session_token == "a" * 64
        assert config.backend_base_url == "http://localhost:8080"
        assert config.difficulty == Difficulty.MEDIUM  # default
        assert config.log_level == "INFO"  # default
        assert config.health_port == 8081  # default

    def test_missing_session_code_raises(self, monkeypatch):
        monkeypatch.setenv("SESSION_TOKEN", "token123")
        monkeypatch.setenv("BACKEND_BASE_URL", "http://localhost:8080")
        monkeypatch.delenv("SESSION_CODE", raising=False)
        with pytest.raises(ValueError, match="SESSION_CODE"):
            BotConfig.from_env()

    def test_missing_session_token_raises(self, monkeypatch):
        monkeypatch.setenv("SESSION_CODE", "ABCD1234")
        monkeypatch.setenv("BACKEND_BASE_URL", "http://localhost:8080")
        monkeypatch.delenv("SESSION_TOKEN", raising=False)
        with pytest.raises(ValueError, match="SESSION_TOKEN"):
            BotConfig.from_env()

    def test_missing_backend_url_raises(self, monkeypatch):
        monkeypatch.setenv("SESSION_CODE", "ABCD1234")
        monkeypatch.setenv("SESSION_TOKEN", "token123")
        monkeypatch.delenv("BACKEND_BASE_URL", raising=False)
        with pytest.raises(ValueError, match="BACKEND_BASE_URL"):
            BotConfig.from_env()

    def test_multiple_missing_vars_reported(self, monkeypatch):
        monkeypatch.delenv("SESSION_CODE", raising=False)
        monkeypatch.delenv("SESSION_TOKEN", raising=False)
        monkeypatch.delenv("BACKEND_BASE_URL", raising=False)
        with pytest.raises(ValueError, match="SESSION_CODE.*SESSION_TOKEN.*BACKEND_BASE_URL"):
            BotConfig.from_env()

    def test_difficulty_easy(self, monkeypatch):
        self._set_required_env(monkeypatch)
        monkeypatch.setenv("DIFFICULTY", "EASY")
        config = BotConfig.from_env()
        assert config.difficulty == Difficulty.EASY

    def test_difficulty_hard(self, monkeypatch):
        self._set_required_env(monkeypatch)
        monkeypatch.setenv("DIFFICULTY", "HARD")
        config = BotConfig.from_env()
        assert config.difficulty == Difficulty.HARD

    def test_difficulty_case_insensitive(self, monkeypatch):
        self._set_required_env(monkeypatch)
        monkeypatch.setenv("DIFFICULTY", "easy")
        config = BotConfig.from_env()
        assert config.difficulty == Difficulty.EASY

    def test_unrecognised_difficulty_defaults_to_medium(self, monkeypatch):
        self._set_required_env(monkeypatch)
        monkeypatch.setenv("DIFFICULTY", "NIGHTMARE")
        config = BotConfig.from_env()
        assert config.difficulty == Difficulty.MEDIUM

    def test_custom_log_level(self, monkeypatch):
        self._set_required_env(monkeypatch)
        monkeypatch.setenv("LOG_LEVEL", "DEBUG")
        config = BotConfig.from_env()
        assert config.log_level == "DEBUG"

    def test_custom_health_port(self, monkeypatch):
        self._set_required_env(monkeypatch)
        monkeypatch.setenv("HEALTH_PORT", "9090")
        config = BotConfig.from_env()
        assert config.health_port == 9090

    def test_invalid_health_port_raises(self, monkeypatch):
        self._set_required_env(monkeypatch)
        monkeypatch.setenv("HEALTH_PORT", "not_a_number")
        with pytest.raises(ValueError, match="HEALTH_PORT"):
            BotConfig.from_env()


class TestResponseDelay:
    """Tests for the response_delay property."""

    def test_easy_delay(self):
        config = BotConfig(
            session_code="ABCD1234",
            session_token="token",
            difficulty=Difficulty.EASY,
            backend_base_url="http://localhost:8080",
            log_level="INFO",
            health_port=8081,
        )
        assert config.response_delay == 10.0

    def test_medium_delay(self):
        config = BotConfig(
            session_code="ABCD1234",
            session_token="token",
            difficulty=Difficulty.MEDIUM,
            backend_base_url="http://localhost:8080",
            log_level="INFO",
            health_port=8081,
        )
        assert config.response_delay == 5.0

    def test_hard_delay(self):
        config = BotConfig(
            session_code="ABCD1234",
            session_token="token",
            difficulty=Difficulty.HARD,
            backend_base_url="http://localhost:8080",
            log_level="INFO",
            health_port=8081,
        )
        assert config.response_delay == 0.0
