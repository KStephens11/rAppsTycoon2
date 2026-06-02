import React from 'react';
import { GameProvider, useGame } from './GameContext';
import Dashboard from './Dashboard';
import './App.css';

function AppContent() {
  const { sessionToken, createSession, joinSession, error } = useGame();
  const [name, setName] = React.useState('');
  const [code, setCode] = React.useState('');

  if (sessionToken) {
    return <Dashboard />;
  }

  return (
    <div style={{ padding: '50px', textAlign: 'center' }}>
      <h1>rApp Tycoon</h1>
      <div style={{ maxWidth: '400px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '20px' }}>
        <input 
          type="text" 
          placeholder="Your Name" 
          value={name} 
          onChange={(e) => setName(e.target.value)}
          style={{ padding: '10px' }}
        />
        <button className="counter" onClick={() => createSession(name)}>Create New Game</button>
        <div style={{ margin: '10px 0' }}>OR</div>
        <input 
          type="text" 
          placeholder="Session Code" 
          value={code} 
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          style={{ padding: '10px' }}
        />
        <button className="counter" onClick={() => joinSession(code, name)}>Join Existing Game</button>
        {error && <p style={{ color: 'red' }}>{error}</p>}
      </div>
    </div>
  );
}

function App() {
  return (
    <GameProvider>
      <AppContent />
    </GameProvider>
  );
}

export default App;
