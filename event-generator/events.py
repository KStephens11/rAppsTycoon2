"""Event generation logic for the Event Generator."""
import random
from typing import Dict, Any


# Event type catalogue with base impact values (at LOW severity)
EVENT_CATALOGUE = {
    'POWER_OUTAGE': {
        'typical_severity': 'HIGH',
        'impact': {
            'health': -7.5,
            'customerExperience': -5.0,
            'cost': 12.5,
            'energyEfficiency': -10.0,
            'automationReliability': -2.5,
            'slaCompliance': -6.0
        },
        'description_template': 'Power supply failure detected at {basestation_name}'
    },
    'TRAFFIC_SPIKE': {
        'typical_severity': 'MEDIUM',
        'impact': {
            'health': -3.0,
            'customerExperience': -8.0,
            'cost': 5.0,
            'energyEfficiency': -2.0,
            'automationReliability': -1.0,
            'slaCompliance': -7.0
        },
        'description_template': 'Sudden surge in network demand at {basestation_name}'
    },
    'HARDWARE_FAILURE': {
        'typical_severity': 'CRITICAL',
        'impact': {
            'health': -10.0,
            'customerExperience': -4.0,
            'cost': 8.0,
            'energyEfficiency': -3.0,
            'automationReliability': -8.0,
            'slaCompliance': -4.0
        },
        'description_template': 'Physical component malfunction at {basestation_name}'
    },
    'SLA_BREACH': {
        'typical_severity': 'HIGH',
        'impact': {
            'health': -2.0,
            'customerExperience': -7.0,
            'cost': 6.0,
            'energyEfficiency': -1.0,
            'automationReliability': -2.0,
            'slaCompliance': -12.0
        },
        'description_template': 'SLA threshold violation detected at {basestation_name}'
    },
    'INTERFERENCE': {
        'typical_severity': 'LOW',
        'impact': {
            'health': -4.0,
            'customerExperience': -5.0,
            'cost': 3.0,
            'energyEfficiency': -1.5,
            'automationReliability': -1.0,
            'slaCompliance': -3.0
        },
        'description_template': 'Radio interference from external source at {basestation_name}'
    },
    'CAPACITY_OVERFLOW': {
        'typical_severity': 'MEDIUM',
        'impact': {
            'health': -3.0,
            'customerExperience': -9.0,
            'cost': 7.0,
            'energyEfficiency': -2.0,
            'automationReliability': -1.5,
            'slaCompliance': -8.0
        },
        'description_template': 'Basestation capacity exceeded at {basestation_name}'
    }
}


# Severity multipliers from GAME_RULES
SEVERITY_MULTIPLIERS = {
    'LOW': 1.0,
    'MEDIUM': 1.5,
    'HIGH': 2.0,
    'CRITICAL': 3.0
}


# Difficulty curve from GAME_RULES
DIFFICULTY_PHASES = {
    'early': {
        'range': (0.0, 0.33),
        'distribution': {'LOW': 60, 'MEDIUM': 30, 'HIGH': 10, 'CRITICAL': 0}
    },
    'mid': {
        'range': (0.33, 0.67),
        'distribution': {'LOW': 30, 'MEDIUM': 40, 'HIGH': 20, 'CRITICAL': 10}
    },
    'late': {
        'range': (0.67, 1.0),
        'distribution': {'LOW': 10, 'MEDIUM': 30, 'HIGH': 35, 'CRITICAL': 25}
    }
}


def select_severity(tick_number: int, total_ticks: int) -> str:
    """Select event severity based on difficulty curve.
    
    Args:
        tick_number: Current tick number (0-indexed)
        total_ticks: Total ticks in the game
    
    Returns:
        Severity level: 'LOW', 'MEDIUM', 'HIGH', or 'CRITICAL'
    """
    progress = tick_number / total_ticks if total_ticks > 0 else 0.0
    
    # Determine phase
    if progress < 0.33:
        phase = 'early'
    elif progress < 0.67:
        phase = 'mid'
    else:
        phase = 'late'
    
    distribution = DIFFICULTY_PHASES[phase]['distribution']
    severities = list(distribution.keys())
    weights = list(distribution.values())
    
    return random.choices(severities, weights=weights, k=1)[0]


def scale_impact(base_impact: Dict[str, float], severity: str) -> Dict[str, float]:
    """Scale impact values by severity multiplier.
    
    Args:
        base_impact: Base impact dict with metric keys
        severity: Severity level
    
    Returns:
        Scaled impact dict with values rounded to 2 decimal places
    """
    multiplier = SEVERITY_MULTIPLIERS.get(severity, 1.0)
    
    return {
        key: round(value * multiplier, 2)
        for key, value in base_impact.items()
    }


def calculate_event_count(player_count: int, base_rate: float, player_multiplier: float) -> int:
    """Calculate number of events to generate this tick.
    
    Uses probabilistic rounding based on the expected event rate.
    
    Args:
        player_count: Number of players in the session
        base_rate: Base events per tick (for 2 players)
        player_multiplier: Additional events per tick per player above 2
    
    Returns:
        Number of events to generate (non-negative integer)
    """
    if player_count < 2:
        return 0
    
    expected_rate = base_rate + (player_count - 2) * player_multiplier
    
    # Probabilistic rounding: e.g. 0.7 → 70% chance of 1, 30% chance of 0
    integer_part = int(expected_rate)
    fractional_part = expected_rate - integer_part
    
    if random.random() < fractional_part:
        return integer_part + 1
    else:
        return integer_part


def generate_event(
    basestation_id: int,
    basestation_name: str,
    tick_number: int,
    total_ticks: int
) -> Dict[str, Any]:
    """Generate a random event for a basestation.
    
    Args:
        basestation_id: ID of the target basestation
        basestation_name: Name of the target basestation
        tick_number: Current tick number (for difficulty curve)
        total_ticks: Total ticks in the game
    
    Returns:
        Event dict matching API_CONTRACT section 7.2 request body
    """
    # Randomly select event type
    event_type = random.choice(list(EVENT_CATALOGUE.keys()))
    event_data = EVENT_CATALOGUE[event_type]
    
    # Select severity based on difficulty curve
    severity = select_severity(tick_number, total_ticks)
    
    # Scale impact by severity
    scaled_impact = scale_impact(event_data['impact'], severity)
    
    # Format description
    description = event_data['description_template'].format(basestation_name=basestation_name)
    
    return {
        'basestationId': basestation_id,
        'eventType': event_type,
        'severity': severity,
        'description': description,
        'impact': scaled_impact
    }
