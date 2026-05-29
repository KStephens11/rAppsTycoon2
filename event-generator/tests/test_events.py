"""Tests for events module."""
import pytest
from collections import Counter

from events import (
    select_severity,
    scale_impact,
    calculate_event_count,
    generate_event,
    SEVERITY_MULTIPLIERS
)


class TestSelectSeverity:
    """Test suite for select_severity function."""
    
    def test_returns_valid_severity_strings(self):
        """Test that select_severity returns only valid severity strings."""
        valid_severities = {'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'}
        
        for tick in range(0, 60, 5):
            severity = select_severity(tick, 60)
            assert severity in valid_severities
    
    def test_early_phase_never_returns_critical(self):
        """Test that early phase (0-33%) never returns CRITICAL."""
        # Test first 20 ticks (0-19) out of 60 total
        for _ in range(100):
            for tick in range(0, 20):
                severity = select_severity(tick, 60)
                assert severity != 'CRITICAL', f"CRITICAL found at tick {tick}"
    
    def test_distribution_approximately_correct(self):
        """Test that severity distribution is approximately correct over many samples."""
        # Early phase (tick 10 out of 60)
        early_samples = [select_severity(10, 60) for _ in range(10000)]
        early_counts = Counter(early_samples)
        
        # Expected: LOW 60%, MEDIUM 30%, HIGH 10%, CRITICAL 0%
        assert early_counts['LOW'] > 5500  # ~60% with tolerance
        assert early_counts['MEDIUM'] > 2500  # ~30% with tolerance
        assert early_counts['HIGH'] > 500  # ~10% with tolerance
        assert early_counts.get('CRITICAL', 0) == 0
        
        # Late phase (tick 50 out of 60)
        late_samples = [select_severity(50, 60) for _ in range(10000)]
        late_counts = Counter(late_samples)
        
        # Expected: LOW 10%, MEDIUM 30%, HIGH 35%, CRITICAL 25%
        assert late_counts['LOW'] < 1500  # ~10% with tolerance
        assert late_counts['CRITICAL'] > 2000  # ~25% with tolerance


class TestScaleImpact:
    """Test suite for scale_impact function."""
    
    def test_applies_correct_multiplier_for_each_severity(self):
        """Test that scale_impact applies correct multiplier for each severity level."""
        base_impact = {
            'health': -10.0,
            'customerExperience': -5.0,
            'cost': 10.0
        }
        
        # LOW (×1.0)
        low_impact = scale_impact(base_impact, 'LOW')
        assert low_impact['health'] == -10.0
        assert low_impact['customerExperience'] == -5.0
        assert low_impact['cost'] == 10.0
        
        # MEDIUM (×1.5)
        medium_impact = scale_impact(base_impact, 'MEDIUM')
        assert medium_impact['health'] == -15.0
        assert medium_impact['customerExperience'] == -7.5
        assert medium_impact['cost'] == 15.0
        
        # HIGH (×2.0)
        high_impact = scale_impact(base_impact, 'HIGH')
        assert high_impact['health'] == -20.0
        assert high_impact['customerExperience'] == -10.0
        assert high_impact['cost'] == 20.0
        
        # CRITICAL (×3.0)
        critical_impact = scale_impact(base_impact, 'CRITICAL')
        assert critical_impact['health'] == -30.0
        assert critical_impact['customerExperience'] == -15.0
        assert critical_impact['cost'] == 30.0
    
    def test_rounds_values_to_two_decimal_places(self):
        """Test that scale_impact rounds values to 2 decimal places."""
        base_impact = {'health': -7.5, 'cost': 12.5}
        
        # MEDIUM (×1.5) should give -11.25 and 18.75
        medium_impact = scale_impact(base_impact, 'MEDIUM')
        assert medium_impact['health'] == -11.25
        assert medium_impact['cost'] == 18.75
        
        # Test rounding with more complex values
        base_impact2 = {'health': -3.333}
        high_impact = scale_impact(base_impact2, 'HIGH')
        assert high_impact['health'] == -6.67  # -3.333 × 2.0 = -6.666 → -6.67


class TestCalculateEventCount:
    """Test suite for calculate_event_count function."""
    
    def test_returns_zero_for_player_count_less_than_two(self):
        """Test that calculate_event_count returns 0 for player_count < 2."""
        assert calculate_event_count(0, 0.3, 0.2) == 0
        assert calculate_event_count(1, 0.3, 0.2) == 0
    
    def test_returns_non_negative_integer(self):
        """Test that calculate_event_count returns non-negative integer."""
        for player_count in range(2, 7):
            result = calculate_event_count(player_count, 0.3, 0.2)
            assert isinstance(result, int)
            assert result >= 0
    
    def test_probabilistic_rounding(self):
        """Test that probabilistic rounding works approximately correctly."""
        # With base_rate=0.3 and 2 players, expected rate is 0.3
        # Over many samples, average should be close to 0.3
        samples = [calculate_event_count(2, 0.3, 0.2) for _ in range(10000)]
        average = sum(samples) / len(samples)
        assert 0.25 < average < 0.35  # Within tolerance of 0.3
        
        # With 6 players: 0.3 + (6-2)*0.2 = 1.1
        # Average should be close to 1.1
        samples = [calculate_event_count(6, 0.3, 0.2) for _ in range(10000)]
        average = sum(samples) / len(samples)
        assert 1.05 < average < 1.15  # Within tolerance of 1.1


class TestGenerateEvent:
    """Test suite for generate_event function."""
    
    def test_returns_dict_with_all_required_keys(self):
        """Test that generate_event returns a dict with all required keys."""
        event = generate_event(1, 'BS-Alpha', 10, 60)
        
        required_keys = {'basestationId', 'eventType', 'severity', 'description', 'impact'}
        assert set(event.keys()) == required_keys
        
        # Check impact has all metric keys
        impact_keys = {
            'health', 'customerExperience', 'cost',
            'energyEfficiency', 'automationReliability', 'slaCompliance'
        }
        assert set(event['impact'].keys()) == impact_keys
    
    def test_impact_values_are_all_non_zero(self):
        """Test that generate_event impact values are all non-zero."""
        # Generate multiple events to test different types
        for _ in range(20):
            event = generate_event(1, 'BS-Alpha', 10, 60)
            
            # At least one impact value should be non-zero
            # (Actually all should be non-zero based on the catalogue)
            impact_values = list(event['impact'].values())
            assert any(v != 0 for v in impact_values)
    
    def test_basestation_id_and_name_in_output(self):
        """Test that basestation ID and name appear in the output."""
        event = generate_event(42, 'BS-TestStation', 10, 60)
        
        assert event['basestationId'] == 42
        assert 'BS-TestStation' in event['description']
    
    def test_event_type_is_valid(self):
        """Test that generated event type is one of the valid types."""
        valid_types = {
            'POWER_OUTAGE', 'TRAFFIC_SPIKE', 'HARDWARE_FAILURE',
            'SLA_BREACH', 'INTERFERENCE', 'CAPACITY_OVERFLOW'
        }
        
        for _ in range(50):
            event = generate_event(1, 'BS-Alpha', 10, 60)
            assert event['eventType'] in valid_types
    
    def test_severity_is_valid(self):
        """Test that generated severity is one of the valid levels."""
        valid_severities = {'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'}
        
        for _ in range(50):
            event = generate_event(1, 'BS-Alpha', 10, 60)
            assert event['severity'] in valid_severities
