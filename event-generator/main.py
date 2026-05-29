"""Main entry point for the Event Generator service."""
import logging
import signal
import sys
import time
from typing import Dict

import schedule

from config import get_config
from client import BackendClient, BackendClientError
from events import calculate_event_count, generate_event


# Global flag for graceful shutdown
shutdown_requested = False

# Per-session tick counters
session_tick_counters: Dict[str, int] = {}


def signal_handler(signum, frame):
    """Handle shutdown signals gracefully."""
    global shutdown_requested
    logger = logging.getLogger(__name__)
    logger.info(f"Received signal {signum}, initiating graceful shutdown...")
    shutdown_requested = True


def tick_job():
    """Execute one tick of event generation across all active sessions."""
    logger = logging.getLogger(__name__)
    config = get_config()
    client = BackendClient()
    
    sessions_processed = 0
    events_pushed = 0
    errors = 0
    
    try:
        # Get all active sessions
        active_sessions = client.get_active_sessions()
        
        for session in active_sessions:
            session_code = session['sessionCode']
            player_count = session['playerCount']
            basestation_ids = session['basestationIds']
            
            # Initialize or increment tick counter for this session
            if session_code not in session_tick_counters:
                session_tick_counters[session_code] = 0
                logger.info(f"New session detected: {session_code} with {player_count} players")
            
            tick_number = session_tick_counters[session_code]
            
            # Calculate how many events to generate this tick
            event_count = calculate_event_count(
                player_count,
                config.events_base_rate,
                config.events_player_multiplier
            )
            
            # Generate and push events
            for _ in range(event_count):
                if not basestation_ids:
                    logger.warning(f"Session {session_code} has no basestations")
                    continue
                
                # Randomly select a basestation
                import random
                basestation_id = random.choice(basestation_ids)
                basestation_name = f"BS-{basestation_id}"
                
                # Generate event
                event = generate_event(
                    basestation_id,
                    basestation_name,
                    tick_number,
                    config.tick_total
                )
                
                # Push to backend
                try:
                    client.push_event(session_code, event)
                    events_pushed += 1
                    logger.debug(
                        f"Pushed {event['eventType']} ({event['severity']}) "
                        f"to {session_code}/{basestation_name}"
                    )
                except BackendClientError as e:
                    logger.warning(f"Failed to push event to {session_code}: {e}")
                    errors += 1
            
            # Increment tick counter
            session_tick_counters[session_code] += 1
            sessions_processed += 1
        
        # Clean up tick counters for sessions that are no longer active
        active_codes = {s['sessionCode'] for s in active_sessions}
        removed_sessions = []
        for session_code in list(session_tick_counters.keys()):
            if session_code not in active_codes:
                del session_tick_counters[session_code]
                removed_sessions.append(session_code)
        
        if removed_sessions:
            logger.info(f"Removed completed sessions: {', '.join(removed_sessions)}")
        
        logger.debug(
            f"Tick complete: {sessions_processed} sessions, "
            f"{events_pushed} events pushed, {errors} errors"
        )
    
    except BackendClientError as e:
        logger.warning(f"Failed to get active sessions: {e}")


def main():
    """Main entry point."""
    global shutdown_requested
    
    # Load configuration (fail fast if invalid)
    try:
        config = get_config()
    except ValueError as e:
        print(f"Configuration error: {e}", file=sys.stderr)
        sys.exit(1)
    
    # Configure logging
    logging.basicConfig(
        level=config.log_level,
        format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    
    logger = logging.getLogger(__name__)
    logger.info("Event Generator starting...")
    logger.info(f"Backend URL: {config.backend_base_url}")
    logger.info(f"Tick interval: {config.tick_interval_seconds}s")
    logger.info(f"Event base rate: {config.events_base_rate}")
    
    # Register signal handlers for graceful shutdown
    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)
    
    # Schedule the tick job
    schedule.every(config.tick_interval_seconds).seconds.do(tick_job)
    
    logger.info("Event Generator ready, waiting for active sessions...")
    
    # Main loop
    while not shutdown_requested:
        schedule.run_pending()
        time.sleep(0.1)
    
    # Complete current tick before shutting down
    logger.info("Completing current tick before shutdown...")
    schedule.run_pending()
    
    logger.info("Event Generator stopped")


if __name__ == '__main__':
    main()
