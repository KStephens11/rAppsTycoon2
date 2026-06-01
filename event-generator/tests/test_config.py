"""Tests for config module."""
import os
import pytest
from config import Config


class TestConfig:
    """Test suite for Config class."""
    
    def test_missing_internal_api_key_raises_error(self, monkeypatch):
        """Test that missing INTERNAL_API_KEY raises ValueError."""
        monkeypatch.delenv('INTERNAL_API_KEY', raising=False)
        
        with pytest.raises(ValueError, match="INTERNAL_API_KEY environment variable is required"):
            Config.from_env()
    
    def test_invalid_numeric_values_raise_error(self, monkeypatch):
        """Test that invalid numeric values raise ValueError."""
        monkeypatch.setenv('INTERNAL_API_KEY', 'test-key')
        
        # Negative tick interval
        monkeypatch.setenv('GAME_TICK_INTERVAL_MS', '-1000')
        with pytest.raises(ValueError, match="GAME_TICK_INTERVAL_MS must be positive"):
            Config.from_env()
        
        # Negative base rate
        monkeypatch.setenv('GAME_TICK_INTERVAL_MS', '5000')
        monkeypatch.setenv('GAME_EVENTS_BASE_RATE', '-0.5')
        with pytest.raises(ValueError, match="GAME_EVENTS_BASE_RATE must be non-negative"):
            Config.from_env()
        
        # Invalid format
        monkeypatch.setenv('GAME_EVENTS_BASE_RATE', '0.3')
        monkeypatch.setenv('GAME_TICK_TOTAL', 'not-a-number')
        with pytest.raises(ValueError, match="Invalid numeric value"):
            Config.from_env()
    
    def test_defaults_applied_when_env_vars_absent(self, monkeypatch):
        """Test that all defaults are applied correctly when env vars are absent."""
        monkeypatch.setenv('INTERNAL_API_KEY', 'test-key')
        # Clear all optional env vars
        for key in ['BACKEND_BASE_URL', 'GAME_TICK_INTERVAL_MS', 'GAME_TICK_TOTAL',
                    'GAME_EVENTS_BASE_RATE', 'GAME_EVENTS_PLAYER_MULTIPLIER',
                    'GAME_ESCALATION_MAX_LEVEL', 'LOG_LEVEL']:
            monkeypatch.delenv(key, raising=False)
        
        config = Config.from_env()
        
        assert config.backend_base_url == 'http://backend:8080'
        assert config.internal_api_key == 'test-key'
        assert config.tick_interval_ms == 5000
        assert config.tick_total == 60
        assert config.events_base_rate == 0.3
        assert config.events_player_multiplier == 0.2
        assert config.escalation_max_level == 3
        assert config.log_level == 'INFO'
    
    def test_valid_env_vars_override_defaults(self, monkeypatch):
        """Test that valid env vars override defaults."""
        monkeypatch.setenv('INTERNAL_API_KEY', 'custom-key')
        monkeypatch.setenv('BACKEND_BASE_URL', 'http://custom:9090')
        monkeypatch.setenv('GAME_TICK_INTERVAL_MS', '3000')
        monkeypatch.setenv('GAME_TICK_TOTAL', '120')
        monkeypatch.setenv('GAME_EVENTS_BASE_RATE', '0.5')
        monkeypatch.setenv('GAME_EVENTS_PLAYER_MULTIPLIER', '0.3')
        monkeypatch.setenv('GAME_ESCALATION_MAX_LEVEL', '5')
        monkeypatch.setenv('LOG_LEVEL', 'DEBUG')
        
        config = Config.from_env()
        
        assert config.backend_base_url == 'http://custom:9090'
        assert config.internal_api_key == 'custom-key'
        assert config.tick_interval_ms == 3000
        assert config.tick_total == 120
        assert config.events_base_rate == 0.5
        assert config.events_player_multiplier == 0.3
        assert config.escalation_max_level == 5
        assert config.log_level == 'DEBUG'
    
    def test_tick_interval_seconds_property(self, monkeypatch):
        """Test that tick_interval_seconds converts ms to seconds correctly."""
        monkeypatch.setenv('INTERNAL_API_KEY', 'test-key')
        monkeypatch.setenv('GAME_TICK_INTERVAL_MS', '5000')
        
        config = Config.from_env()
        
        assert config.tick_interval_seconds == 5.0
