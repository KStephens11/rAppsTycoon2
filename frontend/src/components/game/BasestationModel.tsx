import { useRef, useState } from 'react';
import { useFrame } from '@react-three/fiber';
import { Html } from '@react-three/drei';
import * as THREE from 'three';
import type { EventSeverity } from './IsometricMap';
import { useDragOptional } from '../../context/DragContext';

interface BasestationModelProps {
  position: [number, number, number];
  name: string;
  health: number;
  selected: boolean;
  hasEvent: boolean;
  highestSeverity?: EventSeverity;
  highestEscalation: number;
  activeRapps: number;
  showResolution?: boolean;
  onClick: () => void;
  /** When true, show a subtle cyan glow indicating this is a valid drop target */
  isDragActive?: boolean;
  /** When true, show a strong highlight (brighter glow, scale bump) as the drop indicator */
  isHoveredTarget?: boolean;
  /** Basestation ID for context updates during drag-and-drop */
  basestationId?: number;
  /** Callback triggered when a dragged rApp is dropped on this basestation */
  onDrop?: (basestationId: number) => void;
}

function getHealthColor(health: number): string {
  if (health > 70) return '#10b981';
  if (health > 40) return '#f59e0b';
  return '#ef4444';
}

/**
 * Blends a hex colour toward red based on escalation level (0-3).
 * At escalation 0, returns the original colour. At escalation 3, heavily shifted toward red.
 */
function blendTowardRed(hexColor: string, escalation: number): string {
  if (escalation <= 0) return hexColor;
  const factor = Math.min(escalation, 3) * 0.25; // 0→0, 1→0.25, 2→0.5, 3→0.75
  const r = parseInt(hexColor.slice(1, 3), 16);
  const g = parseInt(hexColor.slice(3, 5), 16);
  const b = parseInt(hexColor.slice(5, 7), 16);
  const targetR = 239; // #ef4444 red channel
  const targetG = 68;
  const targetB = 68;
  const newR = Math.round(r + (targetR - r) * factor);
  const newG = Math.round(g + (targetG - g) * factor);
  const newB = Math.round(b + (targetB - b) * factor);
  return `#${newR.toString(16).padStart(2, '0')}${newG.toString(16).padStart(2, '0')}${newB.toString(16).padStart(2, '0')}`;
}

/** Returns the hex colour for a given event severity level */
function getSeverityColor(severity?: EventSeverity): string {
  switch (severity) {
    case 'LOW': return '#3b82f6';
    case 'MEDIUM': return '#f59e0b';
    case 'HIGH': return '#f97316';
    case 'CRITICAL': return '#ef4444';
    default: return '#ef4444';
  }
}

/** Returns pulse speed multiplier based on severity (higher = faster pulse) */
function getSeverityPulseSpeed(severity?: EventSeverity): number {
  switch (severity) {
    case 'LOW': return 2.0;
    case 'MEDIUM': return 3.0;
    case 'HIGH': return 4.0;
    case 'CRITICAL': return 5.5;
    default: return 3.0;
  }
}

export function BasestationModel({
  position,
  name,
  health,
  selected,
  hasEvent,
  highestSeverity,
  highestEscalation,
  activeRapps,
  showResolution,
  onClick,
  isDragActive = false,
  isHoveredTarget = false,
  basestationId,
  onDrop,
}: BasestationModelProps) {
  const groupRef = useRef<THREE.Group>(null);
  const antennaRef = useRef<THREE.Mesh>(null);
  const [hovered, setHovered] = useState(false);
  const dragContext = useDragOptional();
  const setHoveredTarget = dragContext?.setHoveredTarget;

  const healthColor = getHealthColor(health);
  // Shift health ring glow toward red as escalation increases
  const escalatedHealthColor = hasEvent ? blendTowardRed(healthColor, highestEscalation) : healthColor;
  const ringIntensity = selected ? 2.0 : hovered ? 1.2 : 0.5;
  // Boost ring intensity at high escalation to make the red glow more dramatic
  const escalatedRingIntensity = hasEvent ? ringIntensity + highestEscalation * 0.3 : ringIntensity;

  // Compute scale: hovered drop target gets a scale bump
  const computedScale = isHoveredTarget ? 1.2 : selected ? 1.15 : 1;

  // Rotate antenna
  useFrame((_, delta) => {
    if (antennaRef.current) {
      antennaRef.current.rotation.y += delta * 0.5;
    }
  });

  const handlePointerEnter = (e: { stopPropagation: () => void }) => {
    e.stopPropagation();
    setHovered(true);
    document.body.style.cursor = 'pointer';
    if (isDragActive && basestationId != null && setHoveredTarget) {
      setHoveredTarget(basestationId);
    }
  };

  const handlePointerLeave = () => {
    setHovered(false);
    document.body.style.cursor = 'auto';
    if (isDragActive && setHoveredTarget) {
      setHoveredTarget(null);
    }
  };

  const handlePointerUp = (e: { stopPropagation: () => void }) => {
    e.stopPropagation();
    if (isDragActive && basestationId != null && onDrop) {
      onDrop(basestationId);
    }
  };

  return (
    <group
      ref={groupRef}
      position={position}
      scale={computedScale}
      onClick={(e) => { e.stopPropagation(); onClick(); }}
      onPointerOver={(e) => { handlePointerEnter(e); }}
      onPointerOut={() => { handlePointerLeave(); }}
      onPointerUp={(e) => { handlePointerUp(e); }}
    >
      {/* Base platform */}
      <mesh position={[0, 0.1, 0]} castShadow>
        <cylinderGeometry args={[0.6, 0.7, 0.2, 16]} />
        <meshStandardMaterial color="#1f2937" metalness={0.6} roughness={0.4} />
      </mesh>

      {/* Glowing health ring */}
      <mesh position={[0, 0.22, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <torusGeometry args={[0.65, 0.06, 8, 32]} />
        <meshStandardMaterial
          color={escalatedHealthColor}
          emissive={escalatedHealthColor}
          emissiveIntensity={escalatedRingIntensity}
          transparent
          opacity={0.9}
        />
      </mesh>

      {/* Drop target indicator — subtle cyan glow when drag is active */}
      {isDragActive && (
        <mesh position={[0, 0.15, 0]} rotation={[-Math.PI / 2, 0, 0]}>
          <torusGeometry args={[0.85, 0.04, 8, 32]} />
          <meshStandardMaterial
            color="#06b6d4"
            emissive="#06b6d4"
            emissiveIntensity={isHoveredTarget ? 4.0 : 1.5}
            transparent
            opacity={isHoveredTarget ? 0.95 : 0.6}
          />
        </mesh>
      )}

      {/* Hovered drop target — additional bright outer ring */}
      {isHoveredTarget && (
        <mesh position={[0, 0.15, 0]} rotation={[-Math.PI / 2, 0, 0]}>
          <torusGeometry args={[1.0, 0.05, 8, 32]} />
          <meshStandardMaterial
            color="#22d3ee"
            emissive="#22d3ee"
            emissiveIntensity={5.0}
            transparent
            opacity={0.85}
          />
        </mesh>
      )}

      {/* Main tower pole */}
      <mesh position={[0, 1.2, 0]} castShadow>
        <cylinderGeometry args={[0.08, 0.12, 2.0, 8]} />
        <meshStandardMaterial color="#4b5563" metalness={0.8} roughness={0.3} />
      </mesh>

      {/* Cross arms */}
      <mesh position={[0, 1.8, 0]} rotation={[0, 0, Math.PI / 2]}>
        <cylinderGeometry args={[0.03, 0.03, 0.8, 6]} />
        <meshStandardMaterial color="#6b7280" metalness={0.7} roughness={0.3} />
      </mesh>

      {/* Antenna dish (rotating) */}
      <mesh ref={antennaRef} position={[0, 2.3, 0]} castShadow>
        <coneGeometry args={[0.2, 0.3, 8]} />
        <meshStandardMaterial color="#9ca3af" metalness={0.9} roughness={0.2} />
      </mesh>

      {/* Antenna tip light */}
      <mesh position={[0, 2.5, 0]}>
        <sphereGeometry args={[0.06, 8, 8]} />
        <meshStandardMaterial
          color="#06b6d4"
          emissive="#06b6d4"
          emissiveIntensity={1.5}
        />
      </mesh>

      {/* Event warning marker — pulsing 3D triangle coloured by severity */}
      {hasEvent && (
        <>
          {/* Expanding ring at base — colour matches severity */}
          <EventRing severity={highestSeverity} escalation={highestEscalation} />
          {/* 3D pulsing warning triangle above the tower */}
          <EventMarker severity={highestSeverity} escalation={highestEscalation} />
        </>
      )}

      {/* Active rApp indicators */}
      {activeRapps > 0 && (
        <RappOrbiters count={activeRapps} />
      )}

      {/* Event resolution burst — green checkmark + expanding ring */}
      {showResolution && (
        <ResolutionBurst />
      )}

      {/* Hover tooltip */}
      {hovered && (
        <Html position={[0, 3.2, 0]} center>
          <div className="bg-slate-900/95 border border-slate-700 rounded-lg px-3 py-2 text-center whitespace-nowrap shadow-xl">
            <p className="text-white text-sm font-semibold">{name}</p>
            <p className="text-slate-400 text-xs">
              Health: <span style={{ color: healthColor }}>{health.toFixed(0)}%</span>
            </p>
          </div>
        </Html>
      )}
    </group>
  );
}

function RappOrbiters({ count }: { count: number }) {
  const groupRef = useRef<THREE.Group>(null);
  const COLORS = ['#06b6d4', '#8b5cf6', '#f43f5e', '#14b8a6', '#f97316', '#6366f1'];

  useFrame((_, delta) => {
    if (groupRef.current) {
      groupRef.current.rotation.y += delta * 0.8;
    }
  });

  return (
    <group ref={groupRef} position={[0, 1.5, 0]}>
      {Array.from({ length: Math.min(count, 6) }, (_, i) => {
        const angle = (i / Math.min(count, 6)) * Math.PI * 2;
        const color = COLORS[i % COLORS.length];
        return (
          <mesh
            key={i}
            position={[Math.cos(angle) * 0.5, 0, Math.sin(angle) * 0.5]}
          >
            <sphereGeometry args={[0.06, 8, 8]} />
            <meshStandardMaterial color={color} emissive={color} emissiveIntensity={1} />
          </mesh>
        );
      })}
    </group>
  );
}

/**
 * Pulsing 3D warning triangle marker that floats above the basestation tower.
 * Colour is determined by the highest severity event on the basestation.
 * Uses a cone geometry rotated to look like a warning triangle pointing up.
 * Escalation (0-3) makes the marker larger and pulse faster.
 */
function EventMarker({ severity, escalation = 0 }: { severity?: EventSeverity; escalation?: number }) {
  const meshRef = useRef<THREE.Mesh>(null);
  const color = getSeverityColor(severity);
  const basePulseSpeed = getSeverityPulseSpeed(severity);
  // Escalation multiplier: 0→1.0, 1→1.3, 2→1.6, 3→1.9
  const pulseSpeed = basePulseSpeed * (1 + escalation * 0.3);
  // Base scale grows with escalation: 0→1.0, 1→1.2, 2→1.4, 3→1.6
  const baseScale = 1.0 + escalation * 0.2;

  useFrame(({ clock }) => {
    if (meshRef.current) {
      // Pulsing scale animation — oscillates around the escalation-boosted base scale
      const t = clock.getElapsedTime() * pulseSpeed;
      const scale = baseScale * (1.0 + 0.2 * Math.sin(t));
      meshRef.current.scale.set(scale, scale, scale);

      // Gentle bob up and down
      meshRef.current.position.y = 3.0 + 0.1 * Math.sin(t * 0.5);
    }
  });

  return (
    <mesh ref={meshRef} position={[0, 3.0, 0]} rotation={[0, 0, 0]}>
      {/* Cone pointing up — acts as a warning triangle shape */}
      <coneGeometry args={[0.2, 0.35, 3]} />
      <meshStandardMaterial
        color={color}
        emissive={color}
        emissiveIntensity={2.5 + escalation * 0.5}
        transparent
        opacity={0.95}
        side={THREE.DoubleSide}
      />
    </mesh>
  );
}

/**
 * Expanding ring at the base of the tower that pulses outward.
 * Colour matches the event severity.
 * Escalation (0-3) makes the ring expand further and pulse faster.
 */
function EventRing({ severity, escalation = 0 }: { severity?: EventSeverity; escalation?: number }) {
  const meshRef = useRef<THREE.Mesh>(null);
  const color = getSeverityColor(severity);
  const basePulseSpeed = getSeverityPulseSpeed(severity);
  // Escalation multiplier: 0→1.0, 1→1.3, 2→1.6, 3→1.9
  const pulseSpeed = basePulseSpeed * (1 + escalation * 0.3);
  // Ring radius grows with escalation: base 1.0, up to 1.6 at escalation 3
  const baseRadius = 1.0 + escalation * 0.2;
  // Expansion range grows with escalation for more dramatic effect
  const expansionRange = 0.4 + escalation * 0.15;

  useFrame(({ clock }) => {
    if (meshRef.current) {
      const t = clock.getElapsedTime() * pulseSpeed;
      // Pulse opacity and scale
      const progress = (Math.sin(t) + 1) / 2; // 0 to 1
      const scale = (baseRadius - expansionRange * 0.5) + progress * expansionRange;
      meshRef.current.scale.set(scale, 1, scale);
      (meshRef.current.material as THREE.MeshStandardMaterial).opacity = 0.3 + progress * 0.5;
    }
  });

  return (
    <mesh ref={meshRef} position={[0, 0.25, 0]} rotation={[-Math.PI / 2, 0, 0]}>
      <torusGeometry args={[1.0, 0.04, 8, 32]} />
      <meshStandardMaterial
        color={color}
        emissive={color}
        emissiveIntensity={2 + escalation * 0.4}
        transparent
        opacity={0.7}
      />
    </mesh>
  );
}



/**
 * Green checkmark burst animation shown when an event is resolved on a basestation.
 * Displays an expanding green ring + a checkmark shape that scales up and fades out over ~2 seconds.
 * Uses useFrame for smooth animation — auto-fades to invisible after the duration.
 */
function ResolutionBurst() {
  const ringRef = useRef<THREE.Mesh>(null);
  const checkRef = useRef<THREE.Group>(null);
  const startTimeRef = useRef<number | null>(null);
  const DURATION = 2.0; // seconds
  const RESOLUTION_COLOR = '#10b981';

  useFrame(({ clock }) => {
    if (startTimeRef.current === null) {
      startTimeRef.current = clock.getElapsedTime();
    }

    const elapsed = clock.getElapsedTime() - startTimeRef.current;
    const progress = Math.min(elapsed / DURATION, 1.0); // 0 → 1

    // Ring: expands from scale 1 to 3, fades out
    if (ringRef.current) {
      const ringScale = 1.0 + progress * 2.0;
      ringRef.current.scale.set(ringScale, 1, ringScale);
      (ringRef.current.material as THREE.MeshStandardMaterial).opacity = 0.8 * (1 - progress);
    }

    // Checkmark: scales up quickly then holds, fades out in second half
    if (checkRef.current) {
      const scaleUp = Math.min(progress * 4, 1.0); // reaches full scale at 25% of duration
      checkRef.current.scale.set(scaleUp, scaleUp, scaleUp);
      const fadeStart = 0.5;
      const opacity = progress > fadeStart ? 1 - ((progress - fadeStart) / (1 - fadeStart)) : 1;
      checkRef.current.children.forEach((child) => {
        if ((child as THREE.Mesh).material) {
          ((child as THREE.Mesh).material as THREE.MeshStandardMaterial).opacity = opacity;
        }
      });
    }
  });

  return (
    <group>
      {/* Expanding green ring */}
      <mesh ref={ringRef} position={[0, 0.3, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <torusGeometry args={[0.7, 0.06, 8, 32]} />
        <meshStandardMaterial
          color={RESOLUTION_COLOR}
          emissive={RESOLUTION_COLOR}
          emissiveIntensity={3}
          transparent
          opacity={0.8}
        />
      </mesh>

      {/* Checkmark made of two thin cylinders forming a ✓ shape */}
      <group ref={checkRef} position={[0, 2.8, 0]}>
        {/* Short left stroke of checkmark */}
        <mesh position={[-0.1, -0.05, 0]} rotation={[0, 0, Math.PI / 4]}>
          <cylinderGeometry args={[0.03, 0.03, 0.2, 6]} />
          <meshStandardMaterial
            color={RESOLUTION_COLOR}
            emissive={RESOLUTION_COLOR}
            emissiveIntensity={3}
            transparent
            opacity={1}
          />
        </mesh>
        {/* Long right stroke of checkmark */}
        <mesh position={[0.12, 0.05, 0]} rotation={[0, 0, -Math.PI / 5]}>
          <cylinderGeometry args={[0.03, 0.03, 0.35, 6]} />
          <meshStandardMaterial
            color={RESOLUTION_COLOR}
            emissive={RESOLUTION_COLOR}
            emissiveIntensity={3}
            transparent
            opacity={1}
          />
        </mesh>
      </group>
    </group>
  );
}
