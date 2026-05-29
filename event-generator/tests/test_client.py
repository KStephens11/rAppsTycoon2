"""Tests for client module."""
import pytest
from unittest.mock import Mock, patch
import requests

from client import BackendClient, BackendClientError


@pytest.fixture
def mock_config(monkeypatch):
    """Mock configuration for tests."""
    monkeypatch.setenv('INTERNAL_API_KEY', 'test-key')
    monkeypatch.setenv('BACKEND_BASE_URL', 'http://test-backend:8080')


@pytest.fixture
def client(mock_config):
    """Create a BackendClient instance for testing."""
    return BackendClient(timeout=1.0)


class TestBackendClient:
    """Test suite for BackendClient class."""
    
    @patch('client.requests.get')
    def test_get_active_sessions_returns_parsed_list_on_200(self, mock_get, client):
        """Test get_active_sessions returns parsed list on 200."""
        mock_response = Mock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            'sessions': [
                {
                    'sessionCode': 'ABC12345',
                    'playerCount': 4,
                    'basestationIds': [1, 2, 3, 4],
                    'startedAt': '2025-01-15T10:05:00Z'
                }
            ]
        }
        mock_get.return_value = mock_response
        
        result = client.get_active_sessions()
        
        assert len(result) == 1
        assert result[0]['sessionCode'] == 'ABC12345'
        assert result[0]['playerCount'] == 4
        mock_get.assert_called_once()
    
    @patch('client.requests.get')
    def test_get_active_sessions_raises_on_401(self, mock_get, client):
        """Test get_active_sessions raises BackendClientError on 401."""
        mock_response = Mock()
        mock_response.status_code = 401
        mock_response.text = 'Unauthorized'
        mock_get.return_value = mock_response
        
        with pytest.raises(BackendClientError, match="HTTP 401"):
            client.get_active_sessions()
    
    @patch('client.requests.get')
    def test_get_active_sessions_raises_on_500(self, mock_get, client):
        """Test get_active_sessions raises BackendClientError on 500."""
        mock_response = Mock()
        mock_response.status_code = 500
        mock_response.text = 'Internal Server Error'
        mock_get.return_value = mock_response
        
        with pytest.raises(BackendClientError, match="HTTP 500"):
            client.get_active_sessions()
    
    @patch('client.requests.get')
    def test_get_active_sessions_raises_on_connection_error(self, mock_get, client):
        """Test get_active_sessions raises BackendClientError on ConnectionError."""
        mock_get.side_effect = requests.exceptions.ConnectionError("Connection refused")
        
        with pytest.raises(BackendClientError, match="Network error"):
            client.get_active_sessions()
    
    @patch('client.requests.post')
    def test_push_event_sends_correct_json_and_headers_on_201(self, mock_post, client):
        """Test push_event sends correct JSON body and headers on success."""
        mock_response = Mock()
        mock_response.status_code = 201
        mock_response.json.return_value = {
            'eventId': 5,
            'sessionCode': 'ABC12345',
            'basestationId': 1,
            'eventType': 'POWER_OUTAGE',
            'severity': 'HIGH',
            'createdAt': '2025-01-15T10:10:00Z'
        }
        mock_post.return_value = mock_response
        
        event = {
            'basestationId': 1,
            'eventType': 'POWER_OUTAGE',
            'severity': 'HIGH',
            'description': 'Power failure',
            'impact': {
                'health': -15.0,
                'customerExperience': -10.0,
                'cost': 25.0,
                'energyEfficiency': -20.0,
                'automationReliability': -5.0,
                'slaCompliance': -12.0
            }
        }
        
        result = client.push_event('ABC12345', event)
        
        assert result['eventId'] == 5
        mock_post.assert_called_once()
        call_args = mock_post.call_args
        assert call_args.kwargs['json'] == event
        assert call_args.kwargs['headers']['X-Internal-Key'] == 'test-key'
        assert call_args.kwargs['headers']['Content-Type'] == 'application/json'
    
    @patch('client.requests.post')
    def test_push_event_raises_on_404(self, mock_post, client):
        """Test push_event raises BackendClientError on 404 (SESSION_NOT_FOUND)."""
        mock_response = Mock()
        mock_response.status_code = 404
        mock_response.text = 'Session not found'
        mock_post.return_value = mock_response
        
        event = {'basestationId': 1, 'eventType': 'POWER_OUTAGE'}
        
        with pytest.raises(BackendClientError, match="HTTP 404"):
            client.push_event('INVALID', event)
    
    @patch('client.requests.post')
    def test_push_event_raises_on_timeout(self, mock_post, client):
        """Test push_event raises BackendClientError on Timeout."""
        mock_post.side_effect = requests.exceptions.Timeout("Request timed out")
        
        event = {'basestationId': 1, 'eventType': 'POWER_OUTAGE'}
        
        with pytest.raises(BackendClientError, match="Network error"):
            client.push_event('ABC12345', event)
