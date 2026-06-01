import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';

const GameContext = createContext();

export const useGame = () => useContext(GameContext);

export const GameProvider = ({ children }) => {
  const [sessionCode, setSessionCode] = useState(() => localStorage.getItem('sessionCode') || '');
  const [sessionToken, setSessionToken] = useState(() => localStorage.getItem('sessionToken') || '');
  const [gameState, setGameState] = useState('LOBBY'); // LOBBY, ACTIVE, COMPLETE
  const [playerData] = useState(null);
  const [basestations, setBasestations] = useState([]);
  const [leaderboard] = useState([]);
  const [catalogue, setCatalogue] = useState([]);
  const [error, setError] = useState(null);

  // FIX: Derived state computed synchronously using useMemo instead of useEffect + useState
  const processedCatalogue = useMemo(() => {
    if (!catalogue.length) return [];

    return catalogue.map(template => {
      let activeDeployment = null;

      for (const bs of basestations) {
        const found = bs.deployedRapps?.find(d => d.templateId === template.id);
        if (found) {
          if (!activeDeployment || found.status === 'ACTIVE') {
            activeDeployment = found;
          }
        }
      }

      let status = "unlocked";
      let statusText = "Unlocked";
      let level = null;

      if (activeDeployment) {
        status = activeDeployment.status.toLowerCase();
        level = activeDeployment.version;

        if (status === 'active') statusText = `Active - Level ${level}`;
        else if (status === 'deployed') statusText = "Deployed";
        else statusText = status.charAt(0).toUpperCase() + status.slice(1);
      }

      return {
        ...template,
        status,
        statusText,
        level
      };
    });
  }, [catalogue, basestations]);

  const apiFetch = useCallback(async (endpoint, options = {}) => {
    const baseUrl = '';
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    if (sessionToken) {
      headers['X-Session-Token'] = sessionToken;
    }

    try {
      const response = await fetch(`${baseUrl}${endpoint}`, { ...options, headers });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.message || 'API Error');
      }
      return data;
    } catch (err) {
      setError(err.message);
      throw err; // Re-throwing is now fine since we removed the call-stack looping inside useEffect loaders
    }
  }, [sessionToken]);

  const refreshBasestations = useCallback(async () => {
    if (!sessionCode || !sessionToken) return;
    try {
      const data = await apiFetch(`/api/sessions/${sessionCode}/basestations`);
      setBasestations(data.basestations);
    } catch (err) {
      console.error("Failed to fetch basestations", err);
    }
  }, [apiFetch, sessionCode, sessionToken]);

  const refreshSession = useCallback(async () => {
    if (!sessionCode || !sessionToken) return;
    try {
      const data = await apiFetch(`/api/sessions/${sessionCode}`);
      setGameState(data.state);
    } catch (err) {
      console.error("Failed to fetch session", err);
    }
  }, [apiFetch, sessionCode, sessionToken]);

  const refreshCatalogue = useCallback(async () => {
    if (!sessionToken) return;
    try {
      const data = await apiFetch('/api/rapps/catalogue');
      setCatalogue(data.rapps);
    } catch (err) {
      console.error("Failed to fetch catalogue", err);
    }
  }, [apiFetch, sessionToken]);

  // Initial load safely calls memoized callbacks asynchronously
  useEffect(() => {
    if (sessionCode && sessionToken) {
      refreshSession();
      refreshBasestations();
      refreshCatalogue();
    }
  }, [sessionCode, sessionToken, refreshSession, refreshBasestations, refreshCatalogue]);

  // Polling for updates
  useEffect(() => {
    if (gameState === 'ACTIVE') {
      const interval = setInterval(() => {
        refreshBasestations();
      }, 5000);
      return () => clearInterval(interval);
    }
  }, [gameState, refreshBasestations]);

  const createSession = useCallback(async (hostName) => {
    const data = await apiFetch('/api/sessions', {
      method: 'POST',
      body: JSON.stringify({ hostName }),
    });
    setSessionCode(data.sessionCode);
    setSessionToken(data.hostPlayer.sessionToken);
    localStorage.setItem('sessionCode', data.sessionCode);
    localStorage.setItem('sessionToken', data.hostPlayer.sessionToken);
    return data;
  }, [apiFetch]);

  const joinSession = useCallback(async (code, displayName) => {
    const data = await apiFetch(`/api/sessions/${code}/join`, {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    });
    setSessionCode(code);
    setSessionToken(data.player.sessionToken);
    localStorage.setItem('sessionCode', code);
    localStorage.setItem('sessionToken', data.player.sessionToken);
    return data;
  }, [apiFetch]);

  const startSession = useCallback(async () => {
    await apiFetch(`/api/sessions/${sessionCode}/start`, {
      method: 'POST',
    });
    setGameState('ACTIVE');
  }, [apiFetch, sessionCode]);

  const contextValue = useMemo(() => ({
    sessionCode,
    sessionToken,
    gameState,
    playerData,
    basestations,
    leaderboard,
    catalogue,
    processedCatalogue,
    error,
    createSession,
    joinSession,
    startSession,
    refreshBasestations,
  }), [
    sessionCode,
    sessionToken,
    gameState,
    playerData,
    basestations,
    leaderboard,
    catalogue,
    processedCatalogue,
    error,
    createSession,
    joinSession,
    startSession,
    refreshBasestations,
  ]);

  return (
    <GameContext.Provider value={contextValue}>
      {children}
    </GameContext.Provider>
  );
};

GameProvider.propTypes = {
  children: PropTypes.node.isRequired,
};