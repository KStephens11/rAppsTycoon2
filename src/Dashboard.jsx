import React from 'react';
import { useGame } from './GameContext';
import Map from './components/map/Map';
import './Dashboard.css';

const Dashboard = () => {
  const { sessionCode, gameState, startSession, processedCatalogue } = useGame();

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
          <span style={{ fontSize: '12px', color: 'var(--text)' }}>Session: {sessionCode}</span>
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

      <aside className="dashboard-sidebar">
        <div className="sidebar-header">
          <h3>rApp Catalog</h3>
        </div>
        <div className="catalogue-list">
          {processedCatalogue && processedCatalogue.length > 0 ? (
            processedCatalogue.map((rapp) => (
              <div key={rapp.id} className={`catalogue-item ${rapp.status}`} title={rapp.purpose}>
                <div className="rapp-icon-container">
                  {getRappIcon(rapp.name)}
                </div>
                <div className="rapp-info">
                  <div className="rapp-name">{rapp.name}</div>
                  <div className="rapp-status">{rapp.statusText}</div>
                </div>
                <div className="rapp-controls">
                  {rapp.status === 'active' && (
                    <>
                      <span className="control-icon">▲</span>
                      <span className="level-indicator">{rapp.level}</span>
                    </>
                  )}
                  {rapp.status === 'locked' && <span className="lock-icon">🔒</span>}
                  {rapp.status === 'unlocked' && <span className="lock-icon" style={{color: '#52b788'}}>🔓</span>}
                </div>
              </div>
            ))
          ) : (
            <div style={{ opacity: 0.5, fontStyle: 'italic', padding: '20px' }}>Catalogue loading...</div>
          )}
        </div>
      </aside>

      <main className="dashboard-main">
        <Map />
      </main>
    </div>
  );
};

export default Dashboard;
