import { useEffect, useLayoutEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import type { IMessage, StompSubscription } from '@stomp/stompjs';
import { useGame } from '../context/GameContext';
import type { useWebSocket } from './useWebSocket';
import type { useGameState, GameEvent } from './useGameState';

interface WebSocketMessage {
  type: string;
  timestamp: string;
  payload: Record<string, unknown>;
}

type WebSocketHook = ReturnType<typeof useWebSocket>;
type GameStateHook = ReturnType<typeof useGameState>;

export interface GameSubscriptionCallbacks {
  onEventReceived?: (event: GameEvent) => void;
}

export function useGameSubscriptions(
  ws: WebSocketHook,
  gameState: GameStateHook,
  callbacks?: GameSubscriptionCallbacks,
) {
  const { sessionCode, playerId, setGameState, setFinalLeaderboard: setContextFinalLeaderboard } = useGame();
  const navigate = useNavigate();
  const subscriptionsRef = useRef<StompSubscription[]>([]);

  // Store all mutable references in refs to avoid effect re-runs
  const callbacksRef = useRef<GameSubscriptionCallbacks | undefined>(callbacks);
  const gameStateRef = useRef(gameState);
  const wsRef = useRef(ws);
  const setGameStateRef = useRef(setGameState);
  const setContextFinalLeaderboardRef = useRef(setContextFinalLeaderboard);
  const navigateRef = useRef(navigate);

  useLayoutEffect(() => {
    callbacksRef.current = callbacks;
    gameStateRef.current = gameState;
    wsRef.current = ws;
    setGameStateRef.current = setGameState;
    setContextFinalLeaderboardRef.current = setContextFinalLeaderboard;
    navigateRef.current = navigate;
  });

  useEffect(() => {
    if (!ws.connected || !sessionCode || !playerId) return;

    const subs: StompSubscription[] = [];

    function parseMessage(msg: IMessage): WebSocketMessage {
      return JSON.parse(msg.body) as WebSocketMessage;
    }

    // Subscribe to game-wide broadcasts (GAME_STARTED, GAME_ENDED)
    const gameSub = wsRef.current.subscribe(`/topic/session/${sessionCode}/game`, (msg) => {
      const data = parseMessage(msg);
      switch (data.type) {
        case 'GAME_STARTED':
          setGameStateRef.current('active');
          navigateRef.current('/game');
          break;
        case 'GAME_ENDED': {
          setGameStateRef.current('completed');
          const payload = data.payload as {
            finalLeaderboard?: Array<{
              rank: number;
              playerId: number;
              displayName: string;
              compositeScore: number;
              scores: { money: number; customerSatisfaction: number; networkStability: number };
            }>;
            leaderboard?: Array<{
              rank: number;
              playerId: number;
              displayName: string;
              compositeScore: number;
              scores: { money: number; customerSatisfaction: number; networkStability: number };
            }>;
          };
          const leaderboardData = payload.finalLeaderboard || payload.leaderboard;
          if (leaderboardData) {
            gameStateRef.current.setFinalLeaderboard(leaderboardData);
            setContextFinalLeaderboardRef.current(leaderboardData);
          }
          navigateRef.current('/results');
          break;
        }
      }
    });
    if (gameSub) subs.push(gameSub);

    // Subscribe to leaderboard updates
    const leaderboardSub = wsRef.current.subscribe(
      `/topic/session/${sessionCode}/leaderboard`,
      (msg) => {
        const data = parseMessage(msg);
        if (data.type === 'LEADERBOARD_UPDATED') {
          const payload = data.payload as {
            leaderboard: Array<{
              rank: number;
              playerId: number;
              displayName: string;
              compositeScore: number;
              scores: { money: number; customerSatisfaction: number; networkStability: number };
            }>;
            currentTick?: number;
            totalTicks?: number;
          };
          gameStateRef.current.updateLeaderboard({
            leaderboard: payload.leaderboard,
            currentTick: payload.currentTick ?? 0,
            totalTicks: payload.totalTicks ?? 60,
          });
        }
      },
    );
    if (leaderboardSub) subs.push(leaderboardSub);

    // Subscribe to player-specific metrics
    const metricsSub = wsRef.current.subscribe(
      `/topic/session/${sessionCode}/player/${playerId}/metrics`,
      (msg) => {
        const data = parseMessage(msg);
        if (data.type === 'METRICS_UPDATED') {
          const payload = data.payload as {
            basestationId: number;
            basestationName: string;
            metrics: {
              health: number;
              customerExperience: number;
              cost: number;
              energyEfficiency: number;
              automationReliability: number;
              slaCompliance: number;
            };
          };
          gameStateRef.current.updateMetrics(payload);
        }
      },
    );
    if (metricsSub) subs.push(metricsSub);

    // Subscribe to player-specific events
    const eventsSub = wsRef.current.subscribe(
      `/topic/session/${sessionCode}/player/${playerId}/events`,
      (msg) => {
        const data = parseMessage(msg);
        if (data.type === 'EVENT_OCCURRED') {
          const payload = data.payload as {
            eventId: number;
            basestationId: number;
            basestationName: string;
            eventType: string;
            severity: string;
            description: string;
            impact: {
              health: number;
              customerExperience: number;
              cost: number;
              energyEfficiency: number;
              automationReliability: number;
              slaCompliance: number;
            };
          };
          gameStateRef.current.addEvent(payload);
          callbacksRef.current?.onEventReceived?.(payload);
        }
      },
    );
    if (eventsSub) subs.push(eventsSub);

    // Subscribe to player-specific rApp status changes
    const rappsSub = wsRef.current.subscribe(
      `/topic/session/${sessionCode}/player/${playerId}/rapps`,
      (msg) => {
        const data = parseMessage(msg);
        if (data.type === 'RAPP_STATUS_CHANGED') {
          const payload = data.payload as {
            deploymentId: number;
            basestationId: number;
            name: string;
            previousStatus: string;
            newStatus: string;
            version: number;
          };
          gameStateRef.current.updateRappStatus(payload);
        }
      },
    );
    if (rappsSub) subs.push(rappsSub);

    subscriptionsRef.current = subs;

    return () => {
      subs.forEach((sub) => sub.unsubscribe());
      subscriptionsRef.current = [];
    };
    // Only re-subscribe when the connection state or identity changes
    // NOT when gameState/ws objects change (those are accessed via refs)
  }, [ws.connected, sessionCode, playerId]);
}
