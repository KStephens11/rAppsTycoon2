import { useRef, useEffect, useCallback } from 'react';
import { Canvas, useThree, useFrame } from '@react-three/fiber';
import { OrthographicCamera, OrbitControls } from '@react-three/drei';
import * as THREE from 'three';
import { MapEnvironment } from './MapEnvironment';
import { BasestationModel } from './BasestationModel';
import { useDragOptional } from '../../context/DragContext';

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
  /** Callback triggered when a dragged rApp is dropped on a basestation */
  onDrop?: (basestationId: number) => void;
  /** Callback reporting screen coordinates of the selected basestation (throttled to ~10fps) */
  onScreenPositionUpdate?: (position: { x: number; y: number } | null) => void;
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

  // Center and scale
  const rawX = ((posX - avgX) / 40);
  const rawZ = ((posY - avgY) / 40);

  // Snap to nearest grid block center to avoid landing on roads.
  // Grid spacing = 3, grid goes from -7.5 to 7.5, block centers at -6, -3, 0, 3, 6
  const GRID_SPACING = 3;
  const snappedX = Math.round(rawX / GRID_SPACING) * GRID_SPACING;
  const snappedZ = Math.round(rawZ / GRID_SPACING) * GRID_SPACING;

  return [snappedX, 0, snappedZ];
}

/**
 * Inner R3F component that projects a 3D world position to 2D screen coordinates
 * relative to the canvas container. Throttled to ~10fps to avoid excessive updates.
 */
function ScreenPositionTracker({
  worldPosition,
  containerRef,
  onUpdate,
}: {
  worldPosition: [number, number, number] | null;
  containerRef: React.RefObject<HTMLDivElement | null>;
  onUpdate?: (position: { x: number; y: number } | null) => void;
}) {
  const { camera, gl } = useThree();
  const lastUpdateRef = useRef(0);
  const vec3 = useRef(new THREE.Vector3());

  useFrame(() => {
    if (!onUpdate) return;

    // Throttle to ~10fps (every 100ms)
    const now = performance.now();
    if (now - lastUpdateRef.current < 100) return;
    lastUpdateRef.current = now;

    if (!worldPosition) {
      onUpdate(null);
      return;
    }

    const container = containerRef.current;
    if (!container) return;

    // Project 3D position to normalized device coordinates
    vec3.current.set(worldPosition[0], worldPosition[1] + 1.5, worldPosition[2]);
    vec3.current.project(camera);

    // Convert NDC (-1 to 1) to pixel coordinates relative to the canvas
    const canvas = gl.domElement;
    const x = ((vec3.current.x + 1) / 2) * canvas.clientWidth;
    const y = ((-vec3.current.y + 1) / 2) * canvas.clientHeight;

    onUpdate({ x, y });
  });

  // Report null when worldPosition becomes null
  useEffect(() => {
    if (!worldPosition && onUpdate) {
      onUpdate(null);
    }
  }, [worldPosition, onUpdate]);

  return null;
}

/**
 * Inner R3F component that continuously projects all basestation 3D positions
 * to 2D screen coordinates for HTML5 drop detection. Throttled to ~10fps.
 */
function BasestationScreenPositions({
  basestations,
  positions,
  containerRef,
  screenPositionsRef,
}: {
  basestations: MapBasestation[];
  positions: [number, number, number][];
  containerRef: React.RefObject<HTMLDivElement | null>;
  screenPositionsRef: React.MutableRefObject<Map<number, { x: number; y: number }>>;
}) {
  const { camera, gl } = useThree();
  const lastUpdateRef = useRef(0);
  const vec3 = useRef(new THREE.Vector3());

  useFrame(() => {
    // Throttle to ~10fps
    const now = performance.now();
    if (now - lastUpdateRef.current < 100) return;
    lastUpdateRef.current = now;

    const container = containerRef.current;
    if (!container) return;

    const canvas = gl.domElement;
    const newPositions = new Map<number, { x: number; y: number }>();

    for (let i = 0; i < basestations.length; i++) {
      const bs = basestations[i];
      const pos = positions[i];

      vec3.current.set(pos[0], pos[1] + 1.0, pos[2]);
      vec3.current.project(camera);

      const x = ((vec3.current.x + 1) / 2) * canvas.clientWidth;
      const y = ((-vec3.current.y + 1) / 2) * canvas.clientHeight;

      newPositions.set(bs.id, { x, y });
    }

    screenPositionsRef.current = newPositions;
  });

  return null;
}

export function IsometricMap({ basestations, selectedBasestationId, onSelectBasestation, onDrop, onScreenPositionUpdate }: IsometricMapProps) {
  const dragContext = useDragOptional();
  const dragState = dragContext?.dragState ?? null;
  const hoveredTargetId = dragContext?.hoveredTargetId ?? null;
  const setHoveredTarget = dragContext?.setHoveredTarget;
  const containerRef = useRef<HTMLDivElement>(null);
  // Store projected screen positions of basestations for drop detection
  const screenPositionsRef = useRef<Map<number, { x: number; y: number }>>(new Map());

  const allPositions = basestations.map((bs) => ({ positionX: bs.positionX, positionY: bs.positionY }));
  const positions = basestations.map((bs) => toWorldPosition(bs.positionX, bs.positionY, allPositions));

  // Find the 3D position of the selected basestation
  const selectedIndex = basestations.findIndex((bs) => bs.id === selectedBasestationId);
  const selectedWorldPos = selectedIndex >= 0 ? positions[selectedIndex] : null;

  // Handle HTML5 dragover on the container — find nearest basestation and highlight it
  const handleContainerDragOver = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';

    if (!dragState || !setHoveredTarget) return;

    const container = containerRef.current;
    if (!container) return;

    const rect = container.getBoundingClientRect();
    const dropX = e.clientX - rect.left;
    const dropY = e.clientY - rect.top;

    // Find the closest basestation to the cursor
    let closestId: number | null = null;
    let closestDist = Infinity;
    const HIT_RADIUS = 60; // pixels — how close cursor needs to be to a basestation

    for (const [bsId, pos] of screenPositionsRef.current.entries()) {
      const dist = Math.sqrt((pos.x - dropX) ** 2 + (pos.y - dropY) ** 2);
      if (dist < closestDist && dist < HIT_RADIUS) {
        closestDist = dist;
        closestId = bsId;
      }
    }

    setHoveredTarget(closestId);
  }, [dragState, setHoveredTarget]);

  // Handle HTML5 drop on the container
  const handleContainerDrop = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();

    if (!dragState || !onDrop) return;

    const container = containerRef.current;
    if (!container) return;

    const rect = container.getBoundingClientRect();
    const dropX = e.clientX - rect.left;
    const dropY = e.clientY - rect.top;

    // Find the closest basestation to the drop point
    let closestId: number | null = null;
    let closestDist = Infinity;
    const HIT_RADIUS = 60;

    for (const [bsId, pos] of screenPositionsRef.current.entries()) {
      const dist = Math.sqrt((pos.x - dropX) ** 2 + (pos.y - dropY) ** 2);
      if (dist < closestDist && dist < HIT_RADIUS) {
        closestDist = dist;
        closestId = bsId;
      }
    }

    if (closestId !== null) {
      onDrop(closestId);
    }
  }, [dragState, onDrop]);

  const handleDragLeave = useCallback(() => {
    if (setHoveredTarget) {
      setHoveredTarget(null);
    }
  }, [setHoveredTarget]);

  return (
    <div
      ref={containerRef}
      className="w-full h-full"
      role="img"
      aria-label="Isometric map showing basestations and their status"
      onDragOver={handleContainerDragOver}
      onDrop={handleContainerDrop}
      onDragLeave={handleDragLeave}
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

        <MapEnvironment basestationPositions={positions} />

        {/* Project selected basestation position to screen coordinates */}
        <ScreenPositionTracker
          worldPosition={selectedWorldPos}
          containerRef={containerRef}
          onUpdate={onScreenPositionUpdate}
        />

        {/* Track all basestation screen positions for drop detection */}
        <BasestationScreenPositions
          basestations={basestations}
          positions={positions}
          containerRef={containerRef}
          screenPositionsRef={screenPositionsRef}
        />

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
            isDragActive={!!dragState}
            isHoveredTarget={hoveredTargetId === bs.id}
            basestationId={bs.id}
            onDrop={onDrop}
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
