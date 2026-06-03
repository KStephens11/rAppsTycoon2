"""Data classes representing game state and strategy recommendations.

These models match the API contract response shapes from the basestations endpoint,
catalogue endpoint, and WebSocket event/metrics messages.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


class Action(str, Enum):
    """Possible rApp actions the bot can take."""

    DEPLOY = "DEPLOY"
    TUNE = "TUNE"
    DISABLE = "DISABLE"
    ROLLBACK = "ROLLBACK"


@dataclass
class BasestationState:
    """State of a single basestation, matching GET /api/sessions/{code}/basestations response shape.

    Attributes:
        id: Unique basestation identifier.
        name: Display name (e.g. "BS-Alpha").
        metrics: Current metric values — keys: health, customerExperience, cost,
                 energyEfficiency, automationReliability, slaCompliance.
        deployed_rapps: List of deployed rApps — each dict has: id, templateId,
                        name, status, version, deployedAt.
        active_events: List of active events — each dict has: id, eventType,
                       severity, description, escalationLevel, createdAt.
    """

    id: int
    name: str
    metrics: dict = field(default_factory=dict)
    deployed_rapps: list = field(default_factory=list)
    active_events: list = field(default_factory=list)


@dataclass
class GameState:
    """Complete game state passed to the strategy module for scoring.

    Attributes:
        basestations: All basestations owned by this bot player.
        money: Current money balance (initialised at 1000.0).
        catalogue: rApp catalogue entries — each dict has: id, name, purpose,
                   cost, benefit, risk, confidence, sideEffects, impact.
        difficulty: Current difficulty level string (EASY, MEDIUM, HARD).
    """

    basestations: list[BasestationState] = field(default_factory=list)
    money: float = 1000.0
    catalogue: list[dict] = field(default_factory=list)
    difficulty: str = "MEDIUM"


@dataclass
class Recommendation:
    """A ranked action recommendation produced by the strategy module.

    Attributes:
        action: The action type (DEPLOY, TUNE, DISABLE, or ROLLBACK).
        rapp_template_id: Template ID for DEPLOY actions; None for others.
        deployment_id: Deployment ID for TUNE/DISABLE/ROLLBACK actions; None for DEPLOY.
        basestation_id: Target basestation for this recommendation.
        confidence: Score between 0.0 and 1.0 indicating recommendation strength.
        reasoning: Human-readable explanation of why this action was recommended.
    """

    action: Action
    rapp_template_id: Optional[int]
    deployment_id: Optional[int]
    basestation_id: int
    confidence: float
    reasoning: str

    def __post_init__(self) -> None:
        """Validate field constraints after initialisation."""
        if not isinstance(self.action, Action):
            self.action = Action(self.action)
        if not (0.0 <= self.confidence <= 1.0):
            raise ValueError(
                f"confidence must be between 0.0 and 1.0, got {self.confidence}"
            )
        if not self.reasoning:
            raise ValueError("reasoning must be a non-empty string")
