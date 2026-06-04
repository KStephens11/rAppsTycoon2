import { useEffect, useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Copy, Crown, Users, Play, LogOut, Bot, Plus } from 'lucide-react';
import { Button, MascotByte } from '../ui';
import { useGame, type Player } from '../../context/GameContext';
import { apiGet, apiPost, ApiError } from '../../services/api';

interface SessionResponse {
  sessionCode: string;
  state: string;
  maxPlayers: number;
  players: Player[];
}

export function WaitingRoom() {
  const { sessionCode, token, isHost, players, playerId, setPlayers, setGameState, reset } = useGame();
  const navigate = useNavigate();
  const [botDifficulty, setBotDifficulty] = useState<'EASY' | 'MEDIUM' | 'HARD'>('MEDIUM');
  const [addingBot, setAddingBot] = useState(false);
  const [botError, setBotError] = useState('');
  const [gameDuration, setGameDuration] = useState(5);

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
      await apiPost(`/api/sessions/${sessionCode}/start`, { durationMinutes: gameDuration }, token);
      setGameState('active');
      navigate('/game');
    } catch (err) {
      if (err instanceof ApiError) {
        throw err;
      }
    }
  };

  const handleAddBot = async () => {
    if (!sessionCode || !token) return;
    setBotError('');
    setAddingBot(true);
    try {
      await apiPost(`/api/sessions/${sessionCode}/bots`, {
        count: 1,
        difficulty: botDifficulty,
      }, token);
      // Polling will pick up the new player, but let's refresh immediately
      const data = await apiGet<SessionResponse>(`/api/sessions/${sessionCode}`, token);
      setPlayers(data.players);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'SESSION_FULL') {
          setBotError('Lobby is full (6/6 players)');
        } else {
          setBotError(err.message);
        }
      } else {
        setBotError('Failed to add bot');
      }
    } finally {
      setAddingBot(false);
    }
  };

  // Derive mood and speech based on lobby state
  const canStart = isHost && players.length >= 2;
  const mascotMood = canStart ? 'excited' : 'happy';
  const mascotSpeech = isHost
    ? canStart
      ? "Ready — hit Start whenever you like!"
      : "Invite friends or add bots to play!"
    : "Waiting for the host to start...";

  return (
    <motion.div
      initial={{ opacity: 0, x: 50 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -50 }}
      transition={{ duration: 0.3 }}
      className="flex flex-row items-start gap-6 w-full"
    >
      {/* ── Mascot sidebar ── */}
      <div className="shrink-0 w-36 flex flex-col items-center gap-3 pt-6">
        <MascotByte mood={mascotMood} size={84} />
        <AnimatePresence mode="wait">
          <motion.div
            key={mascotSpeech}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.25 }}
            className="rounded-xl border border-surface-lighter bg-surface-light px-2.5 py-2 text-center w-full"
          >
            <p className="text-[11px] text-text-muted leading-relaxed">{mascotSpeech}</p>
          </motion.div>
        </AnimatePresence>
      </div>

      {/* ── Main content ── */}
      <div className="flex flex-col items-center gap-6 flex-1 min-w-0">

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
                {player.isBot && <Bot size={14} className="inline mr-1.5 text-info" />}
                {player.displayName}
                {player.id === playerId && (
                  <span className="text-text-muted text-xs ml-2">(you)</span>
                )}
                {player.isBot && (
                  <span className="text-text-muted text-xs ml-2">(bot)</span>
                )}
              </span>
              {player.isHost && (
                <Crown size={16} className="text-warning" />
              )}
            </motion.div>
          ))}
        </AnimatePresence>
      </div>

      {/* Add Bot Panel (host only) */}
      {isHost && players.length < 6 && (
        <div className="w-full rounded-xl border border-surface-lighter bg-surface-light p-4">
          <div className="flex items-center gap-2 mb-3">
            <Bot size={16} className="text-info" />
            <span className="text-sm font-medium text-text">Add Bot Player</span>
          </div>

          <div className="flex items-center gap-3 mb-3">
            <label className="text-xs text-text-muted">Difficulty:</label>
            <select
              value={botDifficulty}
              onChange={(e) => setBotDifficulty(e.target.value as 'EASY' | 'MEDIUM' | 'HARD')}
              className="flex-1 rounded-lg border border-surface-lighter bg-surface px-3 py-1.5 text-sm text-text focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              aria-label="Bot difficulty"
            >
              <option value="EASY">Easy (slow reactions)</option>
              <option value="MEDIUM">Medium</option>
              <option value="HARD">Hard (instant reactions)</option>
            </select>
          </div>

          <Button
            variant="secondary"
            size="sm"
            onClick={handleAddBot}
            disabled={addingBot || players.length >= 6}
            className="w-full gap-2"
          >
            <Plus size={14} />
            {addingBot ? 'Adding...' : 'Add Bot'}
          </Button>

          {botError && (
            <p className="mt-2 text-xs text-danger">{botError}</p>
          )}
        </div>
      )}

      {/* Start Game Button (host only) */}
      {isHost && (
        <div className="w-full flex flex-col gap-3">
          {/* Game Duration Selector */}
          <div className="flex items-center gap-3">
            <label className="text-sm text-text-muted whitespace-nowrap">Game Length:</label>
            <div className="flex gap-1 flex-1">
              {[1, 2, 3, 4, 5].map((min) => (
                <button
                  key={min}
                  onClick={() => setGameDuration(min)}
                  className={`flex-1 py-1.5 px-2 rounded-lg text-sm font-medium transition-colors cursor-pointer ${
                    gameDuration === min
                      ? 'bg-primary text-surface'
                      : 'bg-surface-lighter text-text-muted hover:text-text'
                  }`}
                  aria-label={`${min} minute${min > 1 ? 's' : ''}`}
                >
                  {min}m
                </button>
              ))}
            </div>
          </div>

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
        </div>
      )}

      {!isHost && (
        <p className="text-text-muted text-sm text-center">
          Waiting for the host to start the game...
        </p>
      )}

      {/* Leave Lobby */}
      <button
        onClick={async () => {
          if (sessionCode && token) {
            try {
              await apiPost(`/api/sessions/${sessionCode}/leave`, {}, token);
            } catch { /* ignore — reset anyway */ }
          }
          reset();
          navigate('/');
        }}
        className="flex items-center gap-2 text-sm text-text-muted hover:text-danger transition-colors mt-2 cursor-pointer"
      >
        <LogOut size={14} />
        Leave Lobby
      </button>

      </div>{/* end main content */}
    </motion.div>
  );
}
