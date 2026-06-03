#!/usr/bin/env python3
"""CLI wrapper for the strategy module's rank_actions().

Reads a JSON game state from stdin, invokes rank_actions(), and outputs
the top 5 recommendations as JSON on stdout.

Usage:
    echo '{"basestations": [...], "money": 1000, ...}' | python recommend.py
"""

import json
import sys

from models import Action, BasestationState, GameState, Recommendation
from strategy import rank_actions


def parse_game_state(data: dict) -> GameState:
    """Parse raw JSON dict into a GameState object."""
    basestations = []
    for bs in data.get("basestations", []):
        basestations.append(
            BasestationState(
                id=bs["id"],
                name=bs["name"],
                metrics=bs.get("metrics", {}),
                deployed_rapps=bs.get("deployedRapps", bs.get("deployed_rapps", [])),
                active_events=bs.get("activeEvents", bs.get("active_events", [])),
            )
        )

    return GameState(
        basestations=basestations,
        money=float(data.get("money", 1000.0)),
        catalogue=data.get("catalogue", []),
        difficulty=data.get("difficulty", "MEDIUM"),
    )


def recommendation_to_dict(rec: Recommendation) -> dict:
    """Convert a Recommendation to a JSON-serializable dict."""
    return {
        "action": rec.action.value if isinstance(rec.action, Action) else rec.action,
        "rappTemplateId": rec.rapp_template_id,
        "deploymentId": rec.deployment_id,
        "basestationId": rec.basestation_id,
        "confidence": round(rec.confidence, 4),
        "reasoning": rec.reasoning,
    }


def main() -> None:
    """Read game state from stdin, compute recommendations, write to stdout."""
    try:
        raw_input = sys.stdin.read()
        if not raw_input.strip():
            print(json.dumps({"error": "Empty input"}), file=sys.stderr)
            sys.exit(1)

        data = json.loads(raw_input)
        game_state = parse_game_state(data)
        recommendations = rank_actions(game_state)

        # Return top 5
        top_5 = recommendations[:5]
        output = {
            "recommendations": [recommendation_to_dict(r) for r in top_5]
        }

        print(json.dumps(output))

    except json.JSONDecodeError as e:
        print(json.dumps({"error": f"Invalid JSON: {e}"}), file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
