import React from 'react';
import { useGame } from '../../GameContext';
import mapBg from '../../assets/map-bg.png';

const Basestation = ({ data }) => {
  const { metrics, name, positionX, positionY, activeEvents } = data;
  
  // Choose icon based on state
  const hasEvent = activeEvents && activeEvents.length > 0;
  
  return (
    <div 
      className="basestation" 
      style={{ left: positionX, top: positionY, borderColor: hasEvent ? '#ff4d4f' : 'var(--accent)' }}
      title={`${name} - Health: ${metrics.health}%`}
    >
      <div className="basestation-icon">
        {hasEvent ? '⚠️' : '📡'}
      </div>
      <div className="basestation-name">{name}</div>
      <div className="metrics-bar">
        <div 
          className="metrics-fill" 
          style={{ 
            width: `${metrics.health}%`,
            backgroundColor: metrics.health < 50 ? '#ff4d4f' : (metrics.health < 80 ? '#faad14' : '#52c41a')
          }} 
        />
      </div>
    </div>
  );
};

const Map = () => {
  const { basestations } = useGame();

  return (
    <div className="map-container" style={{ backgroundImage: `url(${mapBg})` }}>
      {basestations.map(bs => (
        <Basestation key={bs.id} data={bs} />
      ))}
    </div>
  );
};

export default Map;
