import 'react';
import { useGame } from './GameContext';
import Map from './components/map/Map';
import './Dashboard.css';

const Dashboard = () => {
  const { sessionCode, gameState, startSession, processedCatalogue, leaderboard } = useGame();

  // Only show top 3 players
  const topPlayers = leaderboard ? leaderboard.slice(0, 3) : [];

  const getRappIcon = (name) => {
    if (name.includes("Energy")) return "⚡";
    if (name.includes("Capacity")) return "📊";
    if (name.includes("SLA")) return "🛡️";
    if (name.includes("Fault")) return "🧠";
    if (name.includes("Traffic")) return "📈";
    return "⚙️";
  };

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div className="logo-section">
          <h2 style={{ margin: 0 }}>rApp Tycoon</h2>
          <span style={{ fontSize: '12px', color: '#a0acbd' }}>Session: {sessionCode}</span>
        </div>
        
        <div className="player-stats">
          <div className="stat-item">
            <span className="stat-value">€1,000</span>
            <span className="stat-label">Money</span>
          </div>
          <div className="stat-item">
            <span className="stat-value">100%</span>
            <span className="stat-label">Satisfaction</span>
          </div>
          <div className="stat-item">
            <span className="stat-value">100%</span>
            <span className="stat-label">Stability</span>
          </div>
        </div>

        <div className="actions">
          {gameState === 'LOBBY' && (
            <button className="counter" onClick={startSession}>Start Game</button>
          )}
          {gameState === 'ACTIVE' && (
            <span style={{ color: '#52c41a', fontWeight: 'bold' }}>● LIVE</span>
          )}
        </div>
      </header>

      <main className="dashboard-main">
        <Map />

        {/* Floating rApp Catalogue Card */}
        <div className="floating-catalogue">
          <div className="catalogue-header">
            <h4>rApp Catalog</h4>
          </div>
          <div className="catalogue-list-mini">
            {processedCatalogue && processedCatalogue.length > 0 ? (
              processedCatalogue.map((rapp) => (
                <div key={rapp.id} className={`catalogue-item-mini ${rapp.status}`} title={rapp.purpose}>
                  <div className="rapp-icon-container-mini">
                    {getRappIcon(rapp.name)}
                  </div>
                  <div className="rapp-info-mini">
                    <div className="rapp-name-mini">{rapp.name}</div>
                    <div className="rapp-status-mini">{rapp.statusText}</div>
                  </div>
                  <div className="rapp-controls-mini">
                    {rapp.status === 'active' && (
                      <>
                        <span className="control-icon-mini">▲</span>
                        <span className="level-indicator-mini">{rapp.level}</span>
                      </>
                    )}
                    {rapp.status === 'locked' && <span className="lock-icon-mini">🔒</span>}
                    {rapp.status === 'unlocked' && <span className="lock-icon-mini" style={{color: '#52b788'}}>🔓</span>}
                  </div>
                </div>
              ))
            ) : (
              <div className="catalogue-loading">Catalogue loading...</div>
            )}
          </div>
        </div>
        
        {/* Floating Leaderboard Card */}
        <div className="floating-leaderboard">
          <div className="leaderboard-header">
            <h4>Top Ranking</h4>
          </div>
          <div className="leaderboard-list-mini">
            {topPlayers.length > 0 ? (
              topPlayers.map((entry, index) => (
                <div key={entry.playerId} className={`leaderboard-item-mini rank-${index + 1}`}>
                  <div className="rank-badge">{index + 1}</div>
                  <div className="player-avatar-mini">
                    {entry.displayName.charAt(0).toUpperCase()}
                  </div>
                  <div className="player-info-mini">
                    <div className="player-name-mini">{entry.displayName}</div>
                    <div className="player-score-mini">{Math.round(entry.compositeScore).toLocaleString()} pts</div>
                  </div>
                </div>
              ))
            ) : (
              <div className="leaderboard-loading">Waiting for data...</div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
