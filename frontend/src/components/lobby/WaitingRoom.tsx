import { useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Copy, Crown, Users, Play } from 'lucide-react';
import { Button } from '../ui/Button';
import { useGame, type Player } from '../../context/GameContext';
import { apiGet, apiPost, ApiError } from '../../services/api';

interface SessionResponse {
  sessionCode: string;
  state: string;
  maxPlayers: number;
  players: Player[];
}

export function WaitingRoom() {
  const { sessionCode, token, isHost, players, playerId, setPlayers, setGameState } = useGame();
  const navigate = useNavigate();

  // Poll session state every 2 seconds
  useEffect(() => {
    if (!sessionCode || !token) return;

    const poll = async () => {
      try {
        const data = await apiGet<SessionResponse>(`/api/sessions/${sessionCode}`, token);
        setPlayers(data.players);

        if (data.state === 'ACTIVE') {
          setGameState('active');
          navigate('/game');
        }
      } catch {
        // Silently ignore polling errors
      }
    };

    // Initial poll
    poll();

    const interval = setInterval(poll, 2000);
    return () => clearInterval(interval);
  }, [sessionCode, token, setPlayers, setGameState, navigate]);

  const copyCode = useCallback(async () => {
    if (sessionCode) {
      await navigator.clipboard.writeText(sessionCode);
    }
  }, [sessionCode]);

  const handleStart = async () => {
    if (!sessionCode || !token) return;
    try {
      await apiPost(`/api/sessions/${sessionCode}/start`, {}, token);
      setGameState('active');
      navigate('/game');
    } catch (err) {
      if (err instanceof ApiError) {
        // Error will be handled by parent via toast
        throw err;
      }
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: 50 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -50 }}
      transition={{ duration: 0.3 }}
      className="flex flex-col items-center gap-6 w-full max-w-md mx-auto"
    >
      {/* Session Code */}
      <div className="text-center">
        <p className="text-text-muted text-sm mb-2">Session Code</p>
        <div className="flex items-center gap-3">
          <span className="text-4xl font-bold font-mono tracking-widest text-primary drop-shadow-[0_0_12px_rgba(6,182,212,0.5)]">
            {sessionCode}
          </span>
          <button
            onClick={copyCode}
            className="p-2 rounded-lg bg-surface-lighter hover:bg-surface-lighter/80 text-text-muted hover:text-text transition-colors cursor-pointer"
            aria-label="Copy session code"
          >
            <Copy size={18} />
          </button>
        </div>
      </div>

      {/* Player Count */}
      <div className="flex items-center gap-2 text-text-muted">
        <Users size={16} />
        <span className="text-sm">{players.length}/6 Players</span>
      </div>

      {/* Player List */}
      <div className="w-full rounded-xl border border-surface-lighter bg-surface-light p-4">
        <AnimatePresence mode="popLayout">
          {players.map((player) => (
            <motion.div
              key={player.id}
              initial={{ opacity: 0, y: -10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              transition={{ duration: 0.25 }}
              className="flex items-center justify-between py-2 px-3 rounded-lg mb-1 last:mb-0 hover:bg-surface-lighter/50 transition-colors"
            >
              <span className={`text-sm font-medium ${player.id === playerId ? 'text-primary' : 'text-text'}`}>
                {player.displayName}
                {player.id === playerId && (
                  <span className="text-text-muted text-xs ml-2">(you)</span>
                )}
              </span>
              {player.isHost && (
                <Crown size={16} className="text-warning" />
              )}
            </motion.div>
          ))}
        </AnimatePresence>
      </div>

      {/* Start Game Button (host only) */}
      {isHost && (
        <Button
          variant="primary"
          size="lg"
          onClick={handleStart}
          disabled={players.length < 2}
          className="w-full gap-2"
        >
          <Play size={18} />
          Start Game
        </Button>
      )}

      {!isHost && (
        <p className="text-text-muted text-sm text-center">
          Waiting for the host to start the game...
        </p>
      )}
    </motion.div>
  );
}
