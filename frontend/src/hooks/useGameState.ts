import { useReducer, useCallback } from 'react';

export interface BasestationMetrics {
  health: number;
  customerExperience: number;
  cost: number;
  energyEfficiency: number;
  automationReliability: number;
  slaCompliance: number;
}

export interface BasestationState {
  id: number;
  name: string;
  metrics: BasestationMetrics;
}

export interface LeaderboardEntry {
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

export interface GameEvent {
  eventId: number;
  basestationId: number;
  basestationName: string;
  eventType: string;
  severity: string;
  description: string;
  impact: BasestationMetrics;
}

export interface RappDeployment {
  deploymentId: number;
  basestationId: number;
  name: string;
  previousStatus: string;
  newStatus: string;
  version: number;
}

interface RealTimeGameState {
  basestations: BasestationState[];
  leaderboard: LeaderboardEntry[];
  events: GameEvent[];
  rappDeployments: RappDeployment[];
  finalLeaderboard: LeaderboardEntry[] | null;
  currentTick: number;
  totalTicks: number;
}

type GameStateAction =
  | { type: 'UPDATE_METRICS'; payload: { basestationId: number; basestationName: string; metrics: BasestationMetrics } }
  | { type: 'UPDATE_LEADERBOARD'; payload: { leaderboard: LeaderboardEntry[]; currentTick: number; totalTicks: number } }
  | { type: 'ADD_EVENT'; payload: GameEvent }
  | { type: 'UPDATE_RAPP_STATUS'; payload: RappDeployment }
  | { type: 'SET_FINAL_LEADERBOARD'; payload: LeaderboardEntry[] }
  | { type: 'RESET' };

const initialState: RealTimeGameState = {
  basestations: [],
  leaderboard: [],
  events: [],
  rappDeployments: [],
  finalLeaderboard: null,
  currentTick: 0,
  totalTicks: 60,
};

function gameStateReducer(state: RealTimeGameState, action: GameStateAction): RealTimeGameState {
  switch (action.type) {
    case 'UPDATE_METRICS': {
      const { basestationId, basestationName, metrics } = action.payload;
      const existing = state.basestations.find((bs) => bs.id === basestationId);
      if (existing) {
        return {
          ...state,
          basestations: state.basestations.map((bs) =>
            bs.id === basestationId ? { ...bs, metrics } : bs,
          ),
        };
      }
      return {
        ...state,
        basestations: [...state.basestations, { id: basestationId, name: basestationName, metrics }],
      };
    }
    case 'UPDATE_LEADERBOARD':
      return {
        ...state,
        leaderboard: action.payload.leaderboard,
        currentTick: action.payload.currentTick,
        totalTicks: action.payload.totalTicks,
      };
    case 'ADD_EVENT':
      return { ...state, events: [...state.events, action.payload] };
    case 'UPDATE_RAPP_STATUS': {
      const deployment = action.payload;
      const existingIdx = state.rappDeployments.findIndex(
        (r) => r.deploymentId === deployment.deploymentId,
      );
      if (existingIdx >= 0) {
        const updated = [...state.rappDeployments];
        updated[existingIdx] = deployment;
        return { ...state, rappDeployments: updated };
      }
      return { ...state, rappDeployments: [...state.rappDeployments, deployment] };
    }
    case 'SET_FINAL_LEADERBOARD':
      return { ...state, finalLeaderboard: action.payload };
    case 'RESET':
      return initialState;
    default:
      return state;
  }
}

export function useGameState() {
  const [state, dispatch] = useReducer(gameStateReducer, initialState);

  const updateMetrics = useCallback(
    (payload: { basestationId: number; basestationName: string; metrics: BasestationMetrics }) => {
      dispatch({ type: 'UPDATE_METRICS', payload });
    },
    [],
  );

  const updateLeaderboard = useCallback(
    (payload: { leaderboard: LeaderboardEntry[]; currentTick: number; totalTicks: number }) => {
      dispatch({ type: 'UPDATE_LEADERBOARD', payload });
    },
    [],
  );

  const addEvent = useCallback((payload: GameEvent) => {
    dispatch({ type: 'ADD_EVENT', payload });
  }, []);

  const updateRappStatus = useCallback((payload: RappDeployment) => {
    dispatch({ type: 'UPDATE_RAPP_STATUS', payload });
  }, []);

  const setFinalLeaderboard = useCallback((leaderboard: LeaderboardEntry[]) => {
    dispatch({ type: 'SET_FINAL_LEADERBOARD', payload: leaderboard });
  }, []);

  const resetGameState = useCallback(() => {
    dispatch({ type: 'RESET' });
  }, []);

  return {
    ...state,
    updateMetrics,
    updateLeaderboard,
    addEvent,
    updateRappStatus,
    setFinalLeaderboard,
    resetGameState,
  };
}
