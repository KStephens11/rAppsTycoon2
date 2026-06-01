"""Backend API client for Event Generator."""
import logging
from typing import Any, Dict, List

import requests

from config import get_config


logger = logging.getLogger(__name__)


class BackendClientError(Exception):
    """Exception raised when backend API calls fail."""
    pass


class BackendClient:
    """Client for communicating with the Backend REST API."""
    
    def __init__(self, timeout: float = 5.0):
        """Initialize the backend client.
        
        Args:
            timeout: Request timeout in seconds (default: 5.0)
        """
        self.config = get_config()
        self.timeout = timeout
        self.base_url = self.config.backend_base_url.rstrip('/')
        self.headers = {
            'X-Internal-Key': self.config.internal_api_key,
            'Content-Type': 'application/json'
        }
    
    def get_active_sessions(self) -> List[Dict[str, Any]]:
        """Get all active game sessions from the backend.
        
        Returns:
            List of session dicts with keys: sessionCode, playerCount,
            basestationIds, startedAt
        
        Raises:
            BackendClientError: On HTTP error or network failure
        """
        url = f"{self.base_url}/api/internal/sessions/active"
        
        try:
            response = requests.get(
                url,
                headers={'X-Internal-Key': self.config.internal_api_key},
                timeout=self.timeout
            )
            
            if response.status_code == 200:
                data = response.json()
                return data.get('sessions', [])
            else:
                logger.error(
                    f"GET {url} failed with status {response.status_code}: {response.text}"
                )
                raise BackendClientError(
                    f"Failed to get active sessions: HTTP {response.status_code}"
                )
        
        except requests.exceptions.RequestException as e:
            logger.error(f"Network error calling GET {url}: {e}")
            raise BackendClientError(f"Network error: {e}")
    
    def push_event(self, session_code: str, event: Dict[str, Any]) -> Dict[str, Any]:
        """Push a generated event to a game session.
        
        Args:
            session_code: 8-character session code
            event: Event dict matching API_CONTRACT section 7.2 request body
        
        Returns:
            Created event dict on success (201)
        
        Raises:
            BackendClientError: On HTTP error or network failure
        """
        url = f"{self.base_url}/api/internal/sessions/{session_code}/events"
        
        try:
            response = requests.post(
                url,
                json=event,
                headers=self.headers,
                timeout=self.timeout
            )
            
            if response.status_code == 201:
                return response.json()
            else:
                logger.error(
                    f"POST {url} failed with status {response.status_code}: {response.text}"
                )
                raise BackendClientError(
                    f"Failed to push event: HTTP {response.status_code}"
                )
        
        except requests.exceptions.RequestException as e:
            logger.error(f"Network error calling POST {url}: {e}")
            raise BackendClientError(f"Network error: {e}")
