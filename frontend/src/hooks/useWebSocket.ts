import { useRef, useState, useCallback, useEffect } from 'react';
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { useGame } from '../context/GameContext';

export type ConnectionState = 'connected' | 'connecting' | 'disconnected';

interface UseWebSocketReturn {
  connected: boolean;
  connecting: boolean;
  connectionState: ConnectionState;
  error: string | null;
  subscribe: (destination: string, callback: (message: IMessage) => void) => StompSubscription | null;
  sendAction: (code: string, payload: unknown) => void;
  connect: () => void;
  disconnect: () => void;
}

function getWsUrl(): string {
  const envWsUrl = import.meta.env.VITE_WS_URL;
  if (envWsUrl) return envWsUrl;

  const apiUrl = import.meta.env.VITE_API_URL;
  if (apiUrl) {
    const wsUrl = apiUrl.replace(/^http/, 'ws');
    return `${wsUrl}/ws/game/websocket`;
  }

  // Derive from current browser location so it works from any machine
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/game/websocket`;
}

const INITIAL_RECONNECT_DELAY = 1000;
const MAX_RECONNECT_DELAY = 30000;

export function useWebSocket(): UseWebSocketReturn {
  const { token } = useGame();
  const clientRef = useRef<Client | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const [error, setError] = useState<string | null>(null);
  const reconnectDelayRef = useRef(INITIAL_RECONNECT_DELAY);

  const connect = useCallback(() => {
    if (clientRef.current?.active) return;
    if (!token) {
      setError('No session token available');
      return;
    }

    setConnectionState('connecting');
    setError(null);

    const client = new Client({
      brokerURL: getWsUrl(),
      connectHeaders: {
        'X-Session-Token': token,
      },
      reconnectDelay: INITIAL_RECONNECT_DELAY,
      // Custom backoff handled via beforeConnect
      beforeConnect: () => {
        // The STOMP client calls this before each connection attempt
        // We update the reconnectDelay for exponential backoff
        if (clientRef.current) {
          clientRef.current.reconnectDelay = reconnectDelayRef.current;
        }
      },
      onConnect: () => {
        setConnectionState('connected');
        setError(null);
        // Reset backoff on successful connect
        reconnectDelayRef.current = INITIAL_RECONNECT_DELAY;
      },
      onDisconnect: () => {
        setConnectionState('disconnected');
      },
      onStompError: (frame) => {
        setError(frame.headers['message'] || 'STOMP connection error');
        setConnectionState('disconnected');
      },
      onWebSocketClose: () => {
        setConnectionState('connecting');
        // Exponential backoff: double the delay, cap at max
        reconnectDelayRef.current = Math.min(
          reconnectDelayRef.current * 2,
          MAX_RECONNECT_DELAY,
        );
      },
      onWebSocketError: () => {
        setError('WebSocket connection failed');
      },
    });

    clientRef.current = client;
    client.activate();
  }, [token]);

  const disconnect = useCallback(() => {
    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }
    setConnectionState('disconnected');
    reconnectDelayRef.current = INITIAL_RECONNECT_DELAY;
  }, []);

  const subscribe = useCallback(
    (destination: string, callback: (message: IMessage) => void): StompSubscription | null => {
      if (!clientRef.current?.connected) return null;
      return clientRef.current.subscribe(destination, callback);
    },
    [],
  );

  const sendAction = useCallback((code: string, payload: unknown) => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/session/${code}/action`,
      body: JSON.stringify(payload),
    });
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
    };
  }, []);

  return {
    connected: connectionState === 'connected',
    connecting: connectionState === 'connecting',
    connectionState,
    error,
    subscribe,
    sendAction,
    connect,
    disconnect,
  };
}
