import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';

export interface Player {
  id: number;
  displayName: string;
  isHost: boolean;
  connected?: boolean;
}

export interface FinalLeaderboardEntry {
  rank: number;
  playerId: number;
  displayName: string;
  compositeScore: number;
  scores: {
    money: number;
    customerSatisfaction: number;
    networkStability: number;
  };
}

export interface GameState {
  sessionCode: string | null;
  token: string | null;
  playerId: number | null;
  isHost: boolean;
  gameState: 'lobby' | 'active' | 'completed' | null;
  players: Player[];
  finalLeaderboard: FinalLeaderboardEntry[] | null;
}

interface GameContextValue extends GameState {
  setSession: (data: {
    sessionCode: string;
    token: string;
    playerId: number;
    isHost: boolean;
  }) => void;
  setPlayers: (players: Player[]) => void;
  setGameState: (state: 'lobby' | 'active' | 'completed') => void;
  setFinalLeaderboard: (leaderboard: FinalLeaderboardEntry[]) => void;
  reset: () => void;
}

const initialState: GameState = {
  sessionCode: null,
  token: null,
  playerId: null,
  isHost: false,
  gameState: null,
  players: [],
  finalLeaderboard: null,
};

const STORAGE_KEY = 'rapp-tycoon-session';

function loadFromStorage(): GameState {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) return JSON.parse(stored);
  } catch { /* ignore */ }
  return initialState;
}

const GameContext = createContext<GameContextValue | null>(null);

export function GameProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<GameState>(loadFromStorage);

  // Persist to localStorage on state change
  useEffect(() => {
    if (state.sessionCode) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }, [state]);

  const setSession = useCallback(
    (data: { sessionCode: string; token: string; playerId: number; isHost: boolean }) => {
      setState((prev) => ({
        ...prev,
        sessionCode: data.sessionCode,
        token: data.token,
        playerId: data.playerId,
        isHost: data.isHost,
        gameState: 'lobby',
      }));
    },
    [],
  );

  const setPlayers = useCallback((players: Player[]) => {
    setState((prev) => ({ ...prev, players }));
  }, []);

  const setGameState = useCallback((gameState: 'lobby' | 'active' | 'completed') => {
    setState((prev) => ({ ...prev, gameState }));
  }, []);

  const setFinalLeaderboard = useCallback((leaderboard: FinalLeaderboardEntry[]) => {
    setState((prev) => ({ ...prev, finalLeaderboard: leaderboard }));
  }, []);

  const reset = useCallback(() => {
    setState(initialState);
    localStorage.removeItem(STORAGE_KEY);
  }, []);

  return (
    <GameContext.Provider value={{ ...state, setSession, setPlayers, setGameState, setFinalLeaderboard, reset }}>
      {children}
    </GameContext.Provider>
  );
}

export function useGame(): GameContextValue {
  const context = useContext(GameContext);
  if (!context) {
    throw new Error('useGame must be used within a GameProvider');
  }
  return context;
}
