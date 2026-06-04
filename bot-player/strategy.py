"""Stateless strategy module for the Bot Player.

Scores all possible rApp actions against the current game state and returns
ranked Recommendations. This module is designed to be importable from both
the bot-player service and any future recommender endpoint.

No mutable module-level state — all decisions are computed purely from the
input GameState.
"""

from __future__ import annotations

from models import Action, BasestationState, GameState, Recommendation


# ---------------------------------------------------------------------------
# Game-rule constants (derived from GAME_RULES.md)
# ---------------------------------------------------------------------------

# Event type → list of effective rApp template IDs that resolve/mitigate it
EVENT_RESOLUTION_MAP: dict[str, list[int]] = {
    "POWER_OUTAGE": [1, 3],       # Energy Saver, Fault Predictor
    "TRAFFIC_SPIKE": [2, 6],      # Capacity Optimiser, Traffic Balancer
    "HARDWARE_FAILURE": [3, 5],   # Fault Predictor, Config Drift Detector
    "SLA_BREACH": [4, 2],         # SLA Guardian, Capacity Optimiser
    "INTERFERENCE": [6, 7],       # Traffic Balancer, Alarm Noise Reducer
    "CAPACITY_OVERFLOW": [2, 6],  # Capacity Optimiser, Traffic Balancer
}

# Conflicting rApp pairs (template IDs) — deploying both on same basestation
# incurs penalties.
CONFLICT_PAIRS: list[tuple[int, int]] = [
    (1, 2),  # Energy Saver + Capacity Optimiser
    (3, 7),  # Fault Predictor + Alarm Noise Reducer
    (6, 1),  # Traffic Balancer + Energy Saver
]

# Scoring constants
BASE_EVENT_RESOLVE_SCORE: float = 0.9
BASE_METRIC_IMPROVE_SCORE: float = 0.6
COST_PENALTY_THRESHOLD: float = 200.0
COST_PENALTY_AMOUNT: float = 0.25
CONFLICT_PENALTY_AMOUNT: float = 0.2
TUNE_BASE_SCORE: float = 0.4
DISABLE_BASE_SCORE: float = 0.35

# Metric weights for scoring metric improvement (aligned with composite score)
# Higher weight = more impactful to the overall score
METRIC_WEIGHTS: dict[str, float] = {
    "health": 0.15,
    "customerExperience": 0.25,
    "cost": 0.10,
    "energyEfficiency": 0.10,
    "automationReliability": 0.20,
    "slaCompliance": 0.20,
}

# rApp impact per tick (template_id → metric → impact value)
# Positive impact on metrics like health, customerExperience etc. is good.
# For cost, lower is better so positive cost impact is bad.
RAPP_IMPACTS: dict[int, dict[str, float]] = {
    1: {  # Energy Saver
        "health": 0, "customerExperience": -5, "cost": -30,
        "energyEfficiency": 20, "automationReliability": 0, "slaCompliance": -3,
    },
    2: {  # Capacity Optimiser
        "health": 5, "customerExperience": 15, "cost": 20,
        "energyEfficiency": -5, "automationReliability": 5, "slaCompliance": 10,
    },
    3: {  # Fault Predictor
        "health": 15, "customerExperience": 5, "cost": 10,
        "energyEfficiency": 0, "automationReliability": 10, "slaCompliance": 8,
    },
    4: {  # SLA Guardian
        "health": 5, "customerExperience": 10, "cost": 15,
        "energyEfficiency": -2, "automationReliability": 5, "slaCompliance": 20,
    },
    5: {  # Config Drift Detector
        "health": 10, "customerExperience": 3, "cost": 5,
        "energyEfficiency": 2, "automationReliability": 15, "slaCompliance": 5,
    },
    6: {  # Traffic Balancer
        "health": 8, "customerExperience": 12, "cost": 10,
        "energyEfficiency": -3, "automationReliability": 5, "slaCompliance": 7,
    },
    7: {  # Alarm Noise Reducer
        "health": 5, "customerExperience": 2, "cost": -5,
        "energyEfficiency": 0, "automationReliability": 12, "slaCompliance": 3,
    },
}

# Metrics where lower is better (cost) — used for inversion in scoring
LOWER_IS_BETTER_METRICS: set[str] = {"cost"}


# ---------------------------------------------------------------------------
# Internal helper functions
# ---------------------------------------------------------------------------


def _get_catalogue_cost(catalogue: list[dict], template_id: int) -> float:
    """Look up the deployment cost for a given rApp template ID."""
    for entry in catalogue:
        if entry.get("id") == template_id:
            return float(entry.get("cost", 0))
    return 0.0


def _get_catalogue_name(catalogue: list[dict], template_id: int) -> str:
    """Look up the name for a given rApp template ID."""
    for entry in catalogue:
        if entry.get("id") == template_id:
            return entry.get("name", f"rApp-{template_id}")
    return f"rApp-{template_id}"


def _has_conflict(
    deployed_template_ids: set[int], candidate_template_id: int
) -> bool:
    """Check if deploying candidate would create a conflict with existing rApps."""
    for id_a, id_b in CONFLICT_PAIRS:
        if candidate_template_id == id_a and id_b in deployed_template_ids:
            return True
        if candidate_template_id == id_b and id_a in deployed_template_ids:
            return True
    return False


def _compute_cost_penalty(money: float, deploy_cost: float) -> float:
    """Return cost penalty if deploying would drop money below threshold."""
    remaining = money - deploy_cost
    if remaining < 0:
        # Cannot afford — maximum penalty (will effectively be filtered or score 0)
        return 1.0
    if remaining < COST_PENALTY_THRESHOLD:
        return COST_PENALTY_AMOUNT
    return 0.0


def _compute_metric_improvement(
    metrics: dict, template_id: int
) -> float:
    """Score how much a given rApp would improve the basestation's metrics.

    Returns a normalised score between 0.0 and 1.0.
    """
    impacts = RAPP_IMPACTS.get(template_id)
    if not impacts:
        return 0.0

    total_benefit = 0.0
    for metric_name, weight in METRIC_WEIGHTS.items():
        current_value = metrics.get(metric_name, 100.0)
        impact = impacts.get(metric_name, 0.0)

        if metric_name in LOWER_IS_BETTER_METRICS:
            # For cost, negative impact (reducing cost) is beneficial
            if impact < 0:
                # Benefit: cost reduction when cost > 0
                benefit = min(abs(impact), current_value) / 100.0
            else:
                benefit = 0.0
        else:
            # For normal metrics, positive impact is good, especially when metric is low
            if impact > 0:
                # More benefit when the metric is further below 100
                room_to_improve = max(0.0, 100.0 - current_value)
                benefit = min(impact, room_to_improve) / 100.0
            else:
                benefit = 0.0

        total_benefit += benefit * weight

    return min(total_benefit, 1.0)


def _is_already_deployed(
    basestation: BasestationState, template_id: int
) -> bool:
    """Check if an rApp template is already deployed (ACTIVE) on a basestation."""
    for rapp in basestation.deployed_rapps:
        rapp_template = rapp.get("templateId") or rapp.get("template_id")
        rapp_status = rapp.get("status", "")
        if rapp_template == template_id and rapp_status in ("ACTIVE", "DEPLOYING"):
            return True
    return False


def _get_deployed_template_ids(basestation: BasestationState) -> set[int]:
    """Get the set of template IDs for all active/deploying rApps on a basestation."""
    ids: set[int] = set()
    for rapp in basestation.deployed_rapps:
        rapp_template = rapp.get("templateId") or rapp.get("template_id")
        rapp_status = rapp.get("status", "")
        if rapp_template is not None and rapp_status in ("ACTIVE", "DEPLOYING"):
            ids.add(rapp_template)
    return ids


def _is_underperforming(
    basestation: BasestationState, deployed_rapp: dict
) -> bool:
    """Determine if a deployed rApp is underperforming (net negative effect).

    A deployed rApp is underperforming if the metrics it primarily affects
    are already at maximum (100) and it introduces negative side effects,
    or if it is causing more harm than good.
    """
    template_id = deployed_rapp.get("templateId") or deployed_rapp.get("template_id")
    if template_id is None:
        return False

    impacts = RAPP_IMPACTS.get(template_id)
    if not impacts:
        return False

    metrics = basestation.metrics
    net_score = 0.0

    for metric_name, impact in impacts.items():
        current = metrics.get(metric_name, 100.0)
        if metric_name in LOWER_IS_BETTER_METRICS:
            # For cost: negative impact is good, positive is bad
            net_score -= impact * METRIC_WEIGHTS.get(metric_name, 0.1)
        else:
            # For normal metrics: positive impact is good only if there's room
            if impact > 0:
                room = max(0.0, 100.0 - current)
                effective = min(impact, room)
                net_score += effective * METRIC_WEIGHTS.get(metric_name, 0.1)
            else:
                # Negative impact is always bad
                net_score += impact * METRIC_WEIGHTS.get(metric_name, 0.1)

    return net_score < 0.0


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def rank_actions(game_state: GameState) -> list[Recommendation]:
    """Score all possible actions and return sorted by descending confidence.

    Scoring rules:
    1. Event-resolving actions score higher than metric-improvement actions
    2. Cost penalty applied if remaining money would drop below 200
    3. Conflict penalty for deploying rApps that create conflicting pairs
    4. Actions are NOT executed — only ranked and returned

    Args:
        game_state: The current complete game state including basestations,
                    money, catalogue, and difficulty.

    Returns:
        List of Recommendation objects sorted by confidence descending.
    """
    recommendations: list[Recommendation] = []

    for basestation in game_state.basestations:
        deployed_ids = _get_deployed_template_ids(basestation)

        # --- Event-resolving recommendations ---
        for event in basestation.active_events:
            event_type = event.get("eventType") or event.get("event_type", "")
            effective_rapps = EVENT_RESOLUTION_MAP.get(event_type, [])

            for template_id in effective_rapps:
                # Skip if already deployed on this basestation
                if _is_already_deployed(basestation, template_id):
                    continue

                cost = _get_catalogue_cost(game_state.catalogue, template_id)
                rapp_name = _get_catalogue_name(game_state.catalogue, template_id)

                # Cannot afford — skip entirely
                if game_state.money < cost:
                    continue

                score = BASE_EVENT_RESOLVE_SCORE
                score -= _compute_cost_penalty(game_state.money, cost)
                if _has_conflict(deployed_ids, template_id):
                    score -= CONFLICT_PENALTY_AMOUNT

                # Clamp confidence to valid range
                score = max(0.0, min(1.0, score))

                severity = event.get("severity", "UNKNOWN")
                reasoning = (
                    f"Deploy {rapp_name} to resolve {event_type} "
                    f"(severity: {severity}) on {basestation.name}"
                )

                recommendations.append(
                    Recommendation(
                        action=Action.DEPLOY,
                        rapp_template_id=template_id,
                        deployment_id=None,
                        basestation_id=basestation.id,
                        confidence=score,
                        reasoning=reasoning,
                    )
                )

        # --- Metric-improvement recommendations ---
        for catalogue_entry in game_state.catalogue:
            template_id = catalogue_entry.get("id")
            if template_id is None:
                continue

            # Skip if already deployed
            if _is_already_deployed(basestation, template_id):
                continue

            cost = float(catalogue_entry.get("cost", 0))
            rapp_name = catalogue_entry.get("name", f"rApp-{template_id}")

            # Cannot afford — skip entirely
            if game_state.money < cost:
                continue

            # Compute metric improvement score
            improvement = _compute_metric_improvement(
                basestation.metrics, template_id
            )
            if improvement <= 0.0:
                continue

            score = BASE_METRIC_IMPROVE_SCORE * improvement
            score -= _compute_cost_penalty(game_state.money, cost)
            if _has_conflict(deployed_ids, template_id):
                score -= CONFLICT_PENALTY_AMOUNT

            # Clamp confidence
            score = max(0.0, min(1.0, score))

            if score <= 0.0:
                continue

            reasoning = (
                f"Deploy {rapp_name} to improve metrics on {basestation.name} "
                f"(improvement score: {improvement:.2f})"
            )

            recommendations.append(
                Recommendation(
                    action=Action.DEPLOY,
                    rapp_template_id=template_id,
                    deployment_id=None,
                    basestation_id=basestation.id,
                    confidence=score,
                    reasoning=reasoning,
                )
            )

        # --- TUNE/DISABLE recommendations for underperforming rApps ---
        for deployed_rapp in basestation.deployed_rapps:
            rapp_status = deployed_rapp.get("status", "")
            if rapp_status != "ACTIVE":
                continue

            deployment_id = deployed_rapp.get("id")
            if deployment_id is None:
                continue

            rapp_name = deployed_rapp.get("name", "Unknown rApp")
            version = deployed_rapp.get("version", 1)

            if _is_underperforming(basestation, deployed_rapp):
                # Recommend DISABLE for underperforming rApps
                recommendations.append(
                    Recommendation(
                        action=Action.DISABLE,
                        rapp_template_id=None,
                        deployment_id=deployment_id,
                        basestation_id=basestation.id,
                        confidence=DISABLE_BASE_SCORE,
                        reasoning=(
                            f"Disable underperforming {rapp_name} "
                            f"on {basestation.name}"
                        ),
                    )
                )

                # Also recommend TUNE if version > 0 (can try different config)
                if version >= 1:
                    recommendations.append(
                        Recommendation(
                            action=Action.TUNE,
                            rapp_template_id=None,
                            deployment_id=deployment_id,
                            basestation_id=basestation.id,
                            confidence=TUNE_BASE_SCORE,
                            reasoning=(
                                f"Tune underperforming {rapp_name} "
                                f"on {basestation.name} to reduce negative impact"
                            ),
                        )
                    )

    # Sort by confidence descending
    recommendations.sort(key=lambda r: r.confidence, reverse=True)

    return recommendations
