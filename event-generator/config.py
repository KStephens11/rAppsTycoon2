"""Configuration module for Event Generator.

Reads all configuration from environment variables with sensible defaults.
"""
import os
from dataclasses import dataclass
from typing import Optional


@dataclass
class Config:
    """Configuration for the Event Generator service."""
    
    backend_base_url: str
    internal_api_key: str
    tick_interval_ms: int
    tick_total: int
    events_base_rate: float
    events_player_multiplier: float
    escalation_max_level: int
    log_level: str
    
    @property
    def tick_interval_seconds(self) -> float:
        """Convert tick interval from milliseconds to seconds."""
        return self.tick_interval_ms / 1000.0
    
    @classmethod
    def from_env(cls) -> 'Config':
        """Load configuration from environment variables.
        
        Raises:
            ValueError: If INTERNAL_API_KEY is missing or if numeric values are invalid.
        """
        # Required field
        internal_api_key = os.environ.get('INTERNAL_API_KEY')
        if not internal_api_key:
            raise ValueError("INTERNAL_API_KEY environment variable is required")
        
        # Optional fields with defaults
        backend_base_url = os.environ.get('BACKEND_BASE_URL', 'http://backend:8080')
        log_level = os.environ.get('LOG_LEVEL', 'INFO')
        
        # Numeric fields with validation
        try:
            tick_interval_ms = int(os.environ.get('GAME_TICK_INTERVAL_MS', '5000'))
            if tick_interval_ms <= 0:
                raise ValueError("GAME_TICK_INTERVAL_MS must be positive")
            
            tick_total = int(os.environ.get('GAME_TICK_TOTAL', '60'))
            if tick_total <= 0:
                raise ValueError("GAME_TICK_TOTAL must be positive")
            
            events_base_rate = float(os.environ.get('GAME_EVENTS_BASE_RATE', '0.3'))
            if events_base_rate < 0:
                raise ValueError("GAME_EVENTS_BASE_RATE must be non-negative")
            
            events_player_multiplier = float(os.environ.get('GAME_EVENTS_PLAYER_MULTIPLIER', '0.2'))
            if events_player_multiplier < 0:
                raise ValueError("GAME_EVENTS_PLAYER_MULTIPLIER must be non-negative")
            
            escalation_max_level = int(os.environ.get('GAME_ESCALATION_MAX_LEVEL', '3'))
            if escalation_max_level < 0:
                raise ValueError("GAME_ESCALATION_MAX_LEVEL must be non-negative")
                
        except ValueError as e:
            if "invalid literal" in str(e):
                raise ValueError(f"Invalid numeric value in environment variable: {e}")
            raise
        
        return cls(
            backend_base_url=backend_base_url,
            internal_api_key=internal_api_key,
            tick_interval_ms=tick_interval_ms,
            tick_total=tick_total,
            events_base_rate=events_base_rate,
            events_player_multiplier=events_player_multiplier,
            escalation_max_level=escalation_max_level,
            log_level=log_level
        )


# Singleton instance
config: Optional[Config] = None


def get_config() -> Config:
    """Get the singleton Config instance, loading from environment if needed."""
    global config
    if config is None:
        config = Config.from_env()
    return config
