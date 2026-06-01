import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const GameContext = createContext();

export const useGame = () => useContext(GameContext);

export const GameProvider = ({ children }) => {
  const [sessionCode, setSessionCode] = useState(localStorage.getItem('sessionCode') || '');
  const [sessionToken, setSessionToken] = useState(localStorage.getItem('sessionToken') || '');
  const [gameState, setGameState] = useState('LOBBY'); // LOBBY, ACTIVE, COMPLETE
  const [playerData, setPlayerData] = useState(null);
  const [basestations, setBasestations] = useState([]);
  const [leaderboard, setLeaderboard] = useState([]);
  const [catalogue, setCatalogue] = useState([]);
  const [processedCatalogue, setProcessedCatalogue] = useState([]);
  const [error, setError] = useState(null);

  // Derive catalog status from deployments
  useEffect(() => {
    if (!catalogue.length) return;

    const processed = catalogue.map(template => {
      // Find any deployments of this template across all basestations
      let activeDeployment = null;
      
      for (const bs of basestations) {
        const found = bs.deployedRapps?.find(d => d.templateId === template.id);
        if (found) {
          // Priority to ACTIVE status if multiple exist
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
        
        // Map backend status to user-friendly text
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

    setProcessedCatalogue(processed);
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
      throw err;
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
      // Logic to find current player from data.players or data.player if available
      // For now we assume we might need another endpoint or the join/create response
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

  // Initial load
  useEffect(() => {
    if (sessionCode && sessionToken) {
        refreshSession();
        refreshBasestations();
        refreshCatalogue();
    }
  }, [sessionCode, sessionToken, refreshSession, refreshBasestations, refreshCatalogue]);

  // Polling for updates (fallback until WebSockets are fully integrated)
  useEffect(() => {
    if (gameState === 'ACTIVE') {
      const interval = setInterval(() => {
        refreshBasestations();
        // Also refresh leaderboard/player stats here
      }, 5000);
      return () => clearInterval(interval);
    }
  }, [gameState, refreshBasestations]);

  const createSession = async (hostName) => {
    const data = await apiFetch('/api/sessions', {
      method: 'POST',
      body: JSON.stringify({ hostName }),
    });
    setSessionCode(data.sessionCode);
    setSessionToken(data.hostPlayer.sessionToken);
    localStorage.setItem('sessionCode', data.sessionCode);
    localStorage.setItem('sessionToken', data.hostPlayer.sessionToken);
    return data;
  };

  const joinSession = async (code, displayName) => {
    const data = await apiFetch(`/api/sessions/${code}/join`, {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    });
    setSessionCode(code);
    setSessionToken(data.player.sessionToken);
    localStorage.setItem('sessionCode', code);
    localStorage.setItem('sessionToken', data.player.sessionToken);
    return data;
  };

  const startSession = async () => {
    const data = await apiFetch(`/api/sessions/${sessionCode}/start`, {
      method: 'POST',
    });
    setGameState('ACTIVE');
    return data;
  };

  return (
    <GameContext.Provider value={{
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
    }}>
      {children}
    </GameContext.Provider>
  );
};
