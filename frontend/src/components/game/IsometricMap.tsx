import { Canvas } from '@react-three/fiber';
import { OrthographicCamera, OrbitControls } from '@react-three/drei';
import { MapEnvironment } from './MapEnvironment';
import { BasestationModel } from './BasestationModel';

export type EventSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface MapBasestation {
  id: number;
  name: string;
  positionX: number;
  positionY: number;
  metrics: {
    health: number;
    customerExperience: number;
    cost: number;
    energyEfficiency: number;
    automationReliability: number;
    slaCompliance: number;
  };
  activeRappsCount: number;
  hasEvent: boolean;
  highestSeverity?: EventSeverity;
  highestEscalation: number;
  showResolution?: boolean;
}

interface IsometricMapProps {
  basestations: MapBasestation[];
  selectedBasestationId: number | null;
  onSelectBasestation: (id: number | null) => void;
}

/**
 * Maps backend positions to centered 3D world coordinates.
 * Backend gives positions like: (100,150), (200,250), (300,350)
 * We center them around origin and spread them out.
 */
function toWorldPosition(
  posX: number,
  posY: number,
  allPositions: { positionX: number; positionY: number }[],
): [number, number, number] {
  // Calculate center of all positions
  const avgX = allPositions.reduce((sum, p) => sum + p.positionX, 0) / allPositions.length;
  const avgY = allPositions.reduce((sum, p) => sum + p.positionY, 0) / allPositions.length;

  // Center and scale — spread them further apart (divide by smaller number = more spread)
  const x = ((posX - avgX) / 40);
  const z = ((posY - avgY) / 40);
  return [x, 0, z];
}

export function IsometricMap({ basestations, selectedBasestationId, onSelectBasestation }: IsometricMapProps) {
  const allPositions = basestations.map((bs) => ({ positionX: bs.positionX, positionY: bs.positionY }));
  const positions = basestations.map((bs) => toWorldPosition(bs.positionX, bs.positionY, allPositions));

  return (
    <div
      className="w-full h-full"
      role="img"
      aria-label="Isometric map showing basestations and their status"
      style={{
        background: 'linear-gradient(180deg, #0f1629 0%, #1a1040 50%, #141b2d 100%)',
      }}
    >
      <Canvas gl={{ antialias: true }}>
        <OrthographicCamera
          makeDefault
          position={[8, 8, 8]}
          zoom={60}
          near={0.1}
          far={100}
        />
        <OrbitControls
          enableRotate={false}
          enableZoom={true}
          enablePan={true}
          minZoom={30}
          maxZoom={150}
        />

        <MapEnvironment />

        {basestations.map((bs, idx) => (
          <BasestationModel
            key={bs.id}
            position={positions[idx]}
            name={bs.name}
            health={bs.metrics.health}
            selected={selectedBasestationId === bs.id}
            hasEvent={bs.hasEvent}
            highestSeverity={bs.highestSeverity}
            highestEscalation={bs.highestEscalation}
            activeRapps={bs.activeRappsCount}
            showResolution={bs.showResolution}
            onClick={() => onSelectBasestation(
              selectedBasestationId === bs.id ? null : bs.id,
            )}
          />
        ))}
      </Canvas>
    </div>
  );
}

// Default export for React.lazy() support
export default IsometricMap;
