"""Configuration module for Bot Player.

Reads all configuration from environment variables following the same
patterns as the event-generator service.
"""
import logging
import os
from dataclasses import dataclass
from enum import Enum
from typing import Optional


logger = logging.getLogger(__name__)


class Difficulty(Enum):
    """Bot difficulty levels controlling response latency."""

    EASY = "EASY"
    MEDIUM = "MEDIUM"
    HARD = "HARD"


RESPONSE_DELAYS: dict[Difficulty, float] = {
    Difficulty.EASY: 10.0,
    Difficulty.MEDIUM: 5.0,
    Difficulty.HARD: 0.0,
}


@dataclass
class BotConfig:
    """Configuration for the Bot Player service."""

    session_code: str
    session_token: str
    difficulty: Difficulty
    backend_base_url: str
    log_level: str
    health_port: int

    @property
    def response_delay(self) -> float:
        """Return the response delay in seconds for the configured difficulty."""
        return RESPONSE_DELAYS[self.difficulty]

    @classmethod
    def from_env(cls) -> "BotConfig":
        """Load configuration from environment variables.

        Required:
            SESSION_CODE: The game session code to join.
            SESSION_TOKEN: The authentication token for this bot player.
            BACKEND_BASE_URL: The base URL of the backend service.

        Optional:
            DIFFICULTY: Bot difficulty level (EASY, MEDIUM, HARD). Defaults to MEDIUM.
            LOG_LEVEL: Logging level (DEBUG, INFO, WARNING, ERROR). Defaults to INFO.
            HEALTH_PORT: Port for the /health endpoint. Defaults to 8081.

        Raises:
            ValueError: If any required environment variable is missing.
        """
        # Required fields
        missing = []

        session_code = os.environ.get("SESSION_CODE")
        if not session_code:
            missing.append("SESSION_CODE")

        session_token = os.environ.get("SESSION_TOKEN")
        if not session_token:
            missing.append("SESSION_TOKEN")

        backend_base_url = os.environ.get("BACKEND_BASE_URL")
        if not backend_base_url:
            missing.append("BACKEND_BASE_URL")

        if missing:
            raise ValueError(
                f"Missing required environment variable(s): {', '.join(missing)}"
            )

        # Difficulty with fallback to MEDIUM on unrecognised value
        difficulty_str = os.environ.get("DIFFICULTY", "MEDIUM").upper()
        try:
            difficulty = Difficulty(difficulty_str)
        except ValueError:
            logger.warning(
                "Unrecognised DIFFICULTY value '%s', defaulting to MEDIUM",
                difficulty_str,
            )
            difficulty = Difficulty.MEDIUM

        # Optional fields with defaults
        log_level = os.environ.get("LOG_LEVEL", "INFO").upper()

        health_port_str = os.environ.get("HEALTH_PORT", "8081")
        try:
            health_port = int(health_port_str)
            if health_port <= 0 or health_port > 65535:
                raise ValueError("HEALTH_PORT must be between 1 and 65535")
        except ValueError as e:
            if "invalid literal" in str(e):
                raise ValueError(
                    f"HEALTH_PORT must be a valid integer, got: '{health_port_str}'"
                )
            raise

        return cls(
            session_code=session_code,
            session_token=session_token,
            difficulty=difficulty,
            backend_base_url=backend_base_url,
            log_level=log_level,
            health_port=health_port,
        )


# Singleton instance
config: Optional[BotConfig] = None


def get_config() -> BotConfig:
    """Get the singleton BotConfig instance, loading from environment if needed."""
    global config
    if config is None:
        config = BotConfig.from_env()
    return config
