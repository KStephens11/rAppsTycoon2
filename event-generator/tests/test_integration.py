"""Integration tests for the Event Generator.

These tests exercise the full tick_job() loop end-to-end using the `responses`
library to intercept HTTP calls, so no real backend is needed.
"""
import json
import pytest
import responses as resp_lib

import config as config_module
from main import tick_job, session_tick_counters


FAKE_BACKEND = "http://fake-backend:8080"
SESSIONS_URL = f"{FAKE_BACKEND}/api/internal/sessions/active"
EVENTS_URL_TEMPLATE = f"{FAKE_BACKEND}/api/internal/sessions/{{session_code}}/events"

VALID_EVENT_TYPES = {
    "POWER_OUTAGE", "TRAFFIC_SPIKE", "HARDWARE_FAILURE",
    "SLA_BREACH", "INTERFERENCE", "CAPACITY_OVERFLOW",
}
VALID_SEVERITIES = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
IMPACT_KEYS = {
    "health", "customerExperience", "cost",
    "energyEfficiency", "automationReliability", "slaCompliance",
}


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def reset_singletons(monkeypatch):
    """Reset the config singleton and tick counters before every test."""
    config_module.config = None
    session_tick_counters.clear()
    monkeypatch.setenv("INTERNAL_API_KEY", "test-key")
    monkeypatch.setenv("BACKEND_BASE_URL", FAKE_BACKEND)
    monkeypatch.setenv("GAME_TICK_INTERVAL_MS", "5000")
    monkeypatch.setenv("GAME_TICK_TOTAL", "60")
    monkeypatch.setenv("GAME_EVENTS_BASE_RATE", "0.3")
    monkeypatch.setenv("GAME_EVENTS_PLAYER_MULTIPLIER", "0.2")
    monkeypatch.setenv("GAME_ESCALATION_MAX_LEVEL", "3")
    monkeypatch.setenv("LOG_LEVEL", "WARNING")
    yield
    config_module.config = None
    session_tick_counters.clear()


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def _active_sessions_payload(*sessions):
    return {"sessions": list(sessions)}


def _session(code="TEST0001", players=4, basestation_ids=None):
    return {
        "sessionCode": code,
        "playerCount": players,
        "basestationIds": basestation_ids if basestation_ids is not None else [1, 2, 3],
        "startedAt": "2025-01-01T00:00:00Z",
    }


def _event_created_response(event_id=1, session_code="TEST0001"):
    return {"eventId": event_id, "sessionCode": session_code, "createdAt": "2025-01-01T00:00:00Z"}


# ---------------------------------------------------------------------------
# Event shape
# ---------------------------------------------------------------------------

class TestEventShape:
    """Verify that every event pushed to the backend has the correct shape."""

    @resp_lib.activate
    def test_pushed_event_has_all_required_fields(self):
        """tick_job() pushes events with all required API contract fields."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=6)),
                     status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        tick_job()

        post_calls = [c for c in resp_lib.calls if c.request.method == "POST"]
        assert len(post_calls) >= 1

        for call in post_calls:
            body = json.loads(call.request.body)
            assert set(body.keys()) == {
                "basestationId", "eventType", "severity", "description", "impact"
            }, f"Unexpected keys: {body.keys()}"
            assert set(body["impact"].keys()) == IMPACT_KEYS

    @resp_lib.activate
    def test_pushed_event_type_is_valid(self):
        """All pushed eventType values are from the known catalogue."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=6)),
                     status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        tick_job()

        for call in resp_lib.calls:
            if call.request.method == "POST":
                body = json.loads(call.request.body)
                assert body["eventType"] in VALID_EVENT_TYPES

    @resp_lib.activate
    def test_pushed_event_severity_is_valid(self):
        """All pushed severity values are LOW / MEDIUM / HIGH / CRITICAL."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=6)),
                     status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        tick_job()

        for call in resp_lib.calls:
            if call.request.method == "POST":
                body = json.loads(call.request.body)
                assert body["severity"] in VALID_SEVERITIES

    @resp_lib.activate
    def test_pushed_event_basestation_id_matches_session(self):
        """basestationId in every pushed event is one of the session's basestationIds."""
        station_ids = [10, 20, 30]
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=6, basestation_ids=station_ids)),
                     status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        tick_job()

        for call in resp_lib.calls:
            if call.request.method == "POST":
                body = json.loads(call.request.body)
                assert body["basestationId"] in station_ids

    @resp_lib.activate
    def test_pushed_event_description_contains_basestation_name(self):
        """Event description contains the basestation name (BS-<id>)."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=6, basestation_ids=[99])),
                     status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        tick_job()

        for call in resp_lib.calls:
            if call.request.method == "POST":
                body = json.loads(call.request.body)
                assert "BS-99" in body["description"]


# ---------------------------------------------------------------------------
# Auth header
# ---------------------------------------------------------------------------

class TestAuthHeader:
    """Verify that the correct auth header is sent on every request."""

    @resp_lib.activate
    def test_internal_api_key_sent_on_get_sessions(self):
        """GET active sessions includes X-Internal-Key header."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(),
                     status=200)

        tick_job()

        get_call = next(c for c in resp_lib.calls if c.request.method == "GET")
        assert get_call.request.headers.get("X-Internal-Key") == "test-key"

    @resp_lib.activate
    def test_internal_api_key_sent_on_push_event(self):
        """POST push event includes X-Internal-Key and Content-Type headers."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=6)),
                     status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        tick_job()

        post_calls = [c for c in resp_lib.calls if c.request.method == "POST"]
        assert len(post_calls) >= 1
        for call in post_calls:
            assert call.request.headers.get("X-Internal-Key") == "test-key"
            assert "application/json" in call.request.headers.get("Content-Type", "")


# ---------------------------------------------------------------------------
# Session lifecycle
# ---------------------------------------------------------------------------

class TestSessionLifecycle:
    """Verify tick counter management across multiple ticks."""

    @resp_lib.activate
    def test_new_session_initialises_tick_counter_at_zero(self):
        """First tick for a session starts at tick 0."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session()),
                     status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        assert "TEST0001" not in session_tick_counters
        tick_job()
        assert session_tick_counters.get("TEST0001") == 1  # incremented after first tick

    @resp_lib.activate
    def test_tick_counter_increments_on_each_tick(self):
        """Tick counter advances by 1 on every call to tick_job()."""
        for _ in range(3):
            resp_lib.add(resp_lib.GET, SESSIONS_URL,
                         json=_active_sessions_payload(_session()),
                         status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        tick_job()
        assert session_tick_counters["TEST0001"] == 1
        tick_job()
        assert session_tick_counters["TEST0001"] == 2
        tick_job()
        assert session_tick_counters["TEST0001"] == 3

    @resp_lib.activate
    def test_completed_session_removed_from_tick_counters(self):
        """A session that disappears from active list is removed from the counter."""
        # First tick — session is active
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session()),
                     status=200)
        resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)
        tick_job()
        assert "TEST0001" in session_tick_counters

        # Second tick — session is gone
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(),  # empty
                     status=200)
        tick_job()
        assert "TEST0001" not in session_tick_counters

    @resp_lib.activate
    def test_multiple_sessions_tracked_independently(self):
        """Two concurrent sessions each have their own tick counter."""
        for _ in range(2):
            resp_lib.add(resp_lib.GET, SESSIONS_URL,
                         json=_active_sessions_payload(
                             _session("SESS0001", players=4),
                             _session("SESS0002", players=4),
                         ),
                         status=200)
        for code in ("SESS0001", "SESS0002"):
            resp_lib.add(resp_lib.POST, EVENTS_URL_TEMPLATE.format(session_code=code),
                         json=_event_created_response(session_code=code), status=201)

        tick_job()
        tick_job()

        assert session_tick_counters["SESS0001"] == 2
        assert session_tick_counters["SESS0002"] == 2


# ---------------------------------------------------------------------------
# No sessions
# ---------------------------------------------------------------------------

class TestNoActiveSessions:
    """Verify correct behaviour when there are no active sessions."""

    @resp_lib.activate
    def test_no_events_pushed_when_no_active_sessions(self):
        """tick_job() makes no POST calls when the sessions list is empty."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(),
                     status=200)

        tick_job()

        post_calls = [c for c in resp_lib.calls if c.request.method == "POST"]
        assert len(post_calls) == 0

    @resp_lib.activate
    def test_no_events_pushed_when_session_has_no_basestations(self):
        """tick_job() makes no POST calls when a session has an empty basestationIds list."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(basestation_ids=[])),
                     status=200)

        tick_job()

        post_calls = [c for c in resp_lib.calls if c.request.method == "POST"]
        assert len(post_calls) == 0

    @resp_lib.activate
    def test_no_events_pushed_when_player_count_below_minimum(self):
        """tick_job() makes no POST calls when playerCount < 2."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=1)),
                     status=200)

        tick_job()

        post_calls = [c for c in resp_lib.calls if c.request.method == "POST"]
        assert len(post_calls) == 0


# ---------------------------------------------------------------------------
# Error resilience
# ---------------------------------------------------------------------------

class TestErrorResilience:
    """Verify tick_job() stays alive and continues despite backend errors."""

    @resp_lib.activate
    def test_tick_job_continues_when_get_sessions_returns_500(self):
        """tick_job() does not raise when GET sessions returns 500."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL, status=500, body="Internal Error")

        # Should not raise
        tick_job()

    @resp_lib.activate
    def test_tick_job_continues_when_get_sessions_network_error(self):
        """tick_job() does not raise on a network-level connection error."""
        import requests
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     body=requests.exceptions.ConnectionError("refused"))

        tick_job()  # must not raise

    @resp_lib.activate
    def test_tick_job_continues_after_push_event_failure(self):
        """tick_job() continues processing remaining events when one POST fails."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=6)),
                     status=200)
        # First call fails, subsequent calls succeed
        resp_lib.add(resp_lib.POST,
                     EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     status=503, body="Service Unavailable")
        resp_lib.add(resp_lib.POST,
                     EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)
        resp_lib.add(resp_lib.POST,
                     EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     json=_event_created_response(), status=201)

        tick_job()  # must not raise

    @resp_lib.activate
    def test_tick_counter_still_increments_after_push_failure(self):
        """Tick counter advances even when event pushes fail."""
        resp_lib.add(resp_lib.GET, SESSIONS_URL,
                     json=_active_sessions_payload(_session(players=6)),
                     status=200)
        resp_lib.add(resp_lib.POST,
                     EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                     status=503, body="Service Unavailable")

        tick_job()

        assert session_tick_counters.get("TEST0001") == 1


# ---------------------------------------------------------------------------
# Difficulty curve end-to-end
# ---------------------------------------------------------------------------

class TestDifficultyCurve:
    """Verify that severity distribution shifts as ticks progress."""

    @resp_lib.activate
    def test_early_ticks_do_not_produce_critical_events(self):
        """Over 50 early-phase ticks, CRITICAL severity should never appear."""
        for _ in range(50):
            resp_lib.add(resp_lib.GET, SESSIONS_URL,
                         json=_active_sessions_payload(_session(players=6)),
                         status=200)
            resp_lib.add(resp_lib.POST,
                         EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                         json=_event_created_response(), status=201)

        # Force early phase by capping tick counter
        session_tick_counters["TEST0001"] = 0

        for _ in range(50):
            # Keep tick number in early range (< 20 out of 60)
            session_tick_counters["TEST0001"] = min(session_tick_counters.get("TEST0001", 0), 15)
            tick_job()

        for call in resp_lib.calls:
            if call.request.method == "POST":
                body = json.loads(call.request.body)
                assert body["severity"] != "CRITICAL", (
                    f"CRITICAL event appeared in early phase: {body}"
                )

    @resp_lib.activate
    def test_late_ticks_produce_high_or_critical_events(self):
        """Over many late-phase ticks, at least one HIGH or CRITICAL event appears."""
        for _ in range(100):
            resp_lib.add(resp_lib.GET, SESSIONS_URL,
                         json=_active_sessions_payload(_session(players=6)),
                         status=200)
            resp_lib.add(resp_lib.POST,
                         EVENTS_URL_TEMPLATE.format(session_code="TEST0001"),
                         json=_event_created_response(), status=201)

        # Force late phase
        session_tick_counters["TEST0001"] = 45  # tick 45/60 = 75% progress

        for _ in range(100):
            tick_job()

        severities = set()
        for call in resp_lib.calls:
            if call.request.method == "POST":
                body = json.loads(call.request.body)
                severities.add(body["severity"])

        assert severities & {"HIGH", "CRITICAL"}, (
            f"Expected HIGH or CRITICAL in late phase, got only: {severities}"
        )
