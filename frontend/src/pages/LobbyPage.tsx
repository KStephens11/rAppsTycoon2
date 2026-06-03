import { useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { Card } from '../components/ui';
import { Button } from '../components/ui';
import { ToastContainer, type ToastMessage } from '../components/ui';
import { WaitingRoom } from '../components/lobby/WaitingRoom';
import { SettingsToolbar } from '../components/ui/SettingsToolbar';
import { useGame } from '../context/GameContext';
import { apiPost, ApiError } from '../services/api';

interface CreateSessionResponse {
  sessionCode: string;
  sessionId: number;
  hostPlayer: {
    id: number;
    displayName: string;
    sessionToken: string;
  };
  state: string;
  maxPlayers: number;
}

interface JoinSessionResponse {
  player: {
    id: number;
    displayName: string;
    sessionToken: string;
  };
  session: {
    sessionCode: string;
    state: string;
    players: { id: number; displayName: string; isHost: boolean }[];
    maxPlayers: number;
  };
}

const ERROR_MESSAGES: Record<string, string> = {
  SESSION_NOT_FOUND: 'Session not found. Check the code and try again.',
  SESSION_FULL: 'This session is full (6/6 players).',
  INVALID_STATE: 'This game has already started.',
  VALIDATION_ERROR: 'Please check your input and try again.',
};

export function LobbyPage() {
  const { gameState } = useGame();
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const navigate = useNavigate();

  const addToast = useCallback((message: string, type: 'error' | 'success' | 'info' = 'error') => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, message, type }]);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const inWaitingRoom = gameState === 'lobby';

  return (
    <div className="relative flex h-full items-center justify-center p-4 bg-linear-to-br from-surface via-surface to-surface-light">
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      {/* Settings toolbar (sound + theme toggles) */}
      <div className="absolute top-4 right-4 z-10">
        <SettingsToolbar />
      </div>

      <AnimatePresence mode="wait">
        {!inWaitingRoom ? (
          <motion.div
            key="lobby-forms"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, x: -100 }}
            transition={{ duration: 0.3 }}
            className="flex flex-col items-center gap-8 w-full max-w-3xl"
          >
            {/* Title */}
            <div className="text-center">
              <h1 className="text-5xl font-bold text-primary drop-shadow-[0_0_20px_rgba(6,182,212,0.4)] mb-2">
                rApp Tycoon
              </h1>
              <p className="text-text-muted">Deploy. Optimise. Dominate the network.</p>
            </div>

            {/* Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 w-full">
              <CreateGameCard onError={addToast} />
              <JoinGameCard onError={addToast} />
            </div>

            {/* Tutorial button */}
            <Button
              variant="ghost"
              size="md"
              onClick={() => navigate('/tutorial')}
              className="flex items-center gap-2 text-text-muted hover:text-primary"
            >
              <span className="text-lg">🎓</span>
              New to the game? Take the tutorial
            </Button>
          </motion.div>
        ) : (
          <motion.div
            key="waiting-room"
            initial={{ opacity: 0, x: 100 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
            className="w-full max-w-md"
          >
            <WaitingRoom />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function CreateGameCard({ onError }: { onError: (msg: string) => void }) {
  const { setSession, setPlayers } = useGame();
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [fieldError, setFieldError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldError('');

    if (!name.trim()) {
      setFieldError('Please enter your name');
      return;
    }

    setLoading(true);
    try {
      const data = await apiPost<CreateSessionResponse>('/api/sessions', {
        hostName: name.trim(),
      });

      setSession({
        sessionCode: data.sessionCode,
        token: data.hostPlayer.sessionToken,
        playerId: data.hostPlayer.id,
        isHost: true,
      });
      setPlayers([
        { id: data.hostPlayer.id, displayName: data.hostPlayer.displayName, isHost: true },
      ]);
    } catch (err) {
      if (err instanceof ApiError) {
        const msg = ERROR_MESSAGES[err.code] || err.message;
        setFieldError(msg);
        onError(msg);
      } else {
        onError('Something went wrong. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold text-text mb-4">Create Game</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Enter your name"
            maxLength={50}
            className="w-full rounded-lg border border-surface-lighter bg-surface px-4 py-2.5 text-sm text-text placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary transition-colors"
            aria-label="Display name"
          />
          {fieldError && (
            <p className="mt-1.5 text-xs text-danger">{fieldError}</p>
          )}
        </div>
        <Button type="submit" variant="primary" size="lg" disabled={loading} className="w-full">
          {loading ? 'Creating...' : 'Create Session'}
        </Button>
      </form>
    </Card>
  );
}

function JoinGameCard({ onError }: { onError: (msg: string) => void }) {
  const { setSession, setPlayers } = useGame();
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [fieldError, setFieldError] = useState('');

  const handleCodeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCode(e.target.value.toUpperCase().slice(0, 8));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldError('');

    if (!code.trim() || code.length < 8) {
      setFieldError('Please enter a valid 8-character session code');
      return;
    }
    if (!name.trim()) {
      setFieldError('Please enter your name');
      return;
    }

    setLoading(true);
    try {
      const data = await apiPost<JoinSessionResponse>(`/api/sessions/${code}/join`, {
        displayName: name.trim(),
      });

      setSession({
        sessionCode: data.session.sessionCode,
        token: data.player.sessionToken,
        playerId: data.player.id,
        isHost: false,
      });
      setPlayers(data.session.players);
    } catch (err) {
      if (err instanceof ApiError) {
        const msg = ERROR_MESSAGES[err.code] || err.message;
        setFieldError(msg);
        onError(msg);
      } else {
        onError('Something went wrong. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold text-text mb-4">Join Game</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <input
          type="text"
          value={code}
          onChange={handleCodeChange}
          placeholder="Session code"
          maxLength={8}
          className="w-full rounded-lg border border-surface-lighter bg-surface px-4 py-2.5 text-sm text-text font-mono tracking-widest uppercase placeholder:text-text-muted placeholder:font-sans placeholder:tracking-normal placeholder:normal-case focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary transition-colors"
          aria-label="Session code"
        />
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Enter your name"
          maxLength={50}
          className="w-full rounded-lg border border-surface-lighter bg-surface px-4 py-2.5 text-sm text-text placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary transition-colors"
          aria-label="Display name"
        />
        {fieldError && (
          <p className="text-xs text-danger">{fieldError}</p>
        )}
        <Button type="submit" variant="primary" size="lg" disabled={loading} className="w-full">
          {loading ? 'Joining...' : 'Join'}
        </Button>
      </form>
    </Card>
  );
}
