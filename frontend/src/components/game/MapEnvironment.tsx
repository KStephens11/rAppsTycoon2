import { useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

// Seeded random for deterministic placement
function seededRandom(seed: number): () => number {
  let s = seed;
  return () => {
    s = (s * 16807 + 0) % 2147483647;
    return s / 2147483647;
  };
}

/** Multi-story building with window rows and rooftop detail */
function Building({ position, width, depth, height, style }: {
  position: [number, number, number];
  width: number;
  depth: number;
  height: number;
  style: number;
}) {
  const wallColor = ['#e2e8f0', '#cbd5e1', '#d6d3d1', '#e7e5e4'][style];
  const trimColor = ['#64748b', '#475569', '#78716c', '#57534e'][style];

  return (
    <group position={position}>
      {/* Main body */}
      <mesh position={[0, height / 2, 0]} castShadow receiveShadow>
        <boxGeometry args={[width, height, depth]} />
        <meshStandardMaterial color={wallColor} roughness={0.8} />
      </mesh>
      {/* Dark trim/base */}
      <mesh position={[0, 0.03, 0]}>
        <boxGeometry args={[width + 0.01, 0.06, depth + 0.01]} />
        <meshStandardMaterial color={trimColor} roughness={0.6} />
      </mesh>
      {/* Rooftop structure */}
      {height > 0.3 && (
        <mesh position={[width * 0.15, height + 0.04, 0]} castShadow>
          <boxGeometry args={[width * 0.3, 0.08, depth * 0.4]} />
          <meshStandardMaterial color={trimColor} roughness={0.7} />
        </mesh>
      )}
      {/* Window strips */}
      {Array.from({ length: Math.floor(height / 0.12) }, (_, i) => (
        <mesh key={i} position={[0, 0.1 + i * 0.12, depth / 2 + 0.001]}>
          <planeGeometry args={[width * 0.8, 0.04]} />
          <meshStandardMaterial color="#94a3b8" emissive="#94a3b8" emissiveIntensity={0.3} />
        </mesh>
      ))}
    </group>
  );
}

/** Tree — trunk + leafy sphere */
function Tree({ position }: { position: [number, number, number] }) {
  return (
    <group position={position}>
      <mesh position={[0, 0.12, 0]} castShadow>
        <cylinderGeometry args={[0.02, 0.03, 0.24, 5]} />
        <meshStandardMaterial color="#78350f" roughness={0.9} />
      </mesh>
      <mesh position={[0, 0.3, 0]} castShadow>
        <sphereGeometry args={[0.12, 8, 6]} />
        <meshStandardMaterial color="#22c55e" roughness={0.8} />
      </mesh>
    </group>
  );
}

/** Street lamp */
function StreetLamp({ position }: { position: [number, number, number] }) {
  return (
    <group position={position}>
      <mesh position={[0, 0.2, 0]}>
        <cylinderGeometry args={[0.01, 0.015, 0.4, 5]} />
        <meshStandardMaterial color="#71717a" metalness={0.7} roughness={0.3} />
      </mesh>
      <mesh position={[0.05, 0.38, 0]} rotation={[0, 0, Math.PI / 4]}>
        <cylinderGeometry args={[0.005, 0.005, 0.1, 4]} />
        <meshStandardMaterial color="#71717a" metalness={0.7} roughness={0.3} />
      </mesh>
      <mesh position={[0.08, 0.4, 0]}>
        <sphereGeometry args={[0.025, 6, 6]} />
        <meshStandardMaterial color="#fef9c3" emissive="#fde047" emissiveIntensity={2} />
      </mesh>
    </group>
  );
}

/** Animated car */
function Car({ start, end, speed, color }: {
  start: [number, number, number];
  end: [number, number, number];
  speed: number;
  color: string;
}) {
  const ref = useRef<THREE.Mesh>(null);
  useFrame(({ clock }) => {
    if (!ref.current) return;
    const t = ((clock.getElapsedTime() * speed) % 1);
    ref.current.position.x = start[0] + (end[0] - start[0]) * t;
    ref.current.position.z = start[2] + (end[2] - start[2]) * t;
  });
  return (
    <mesh ref={ref} position={[start[0], 0.04, start[2]]} castShadow>
      <boxGeometry args={[0.1, 0.05, 0.06]} />
      <meshStandardMaterial color={color} roughness={0.5} metalness={0.3} />
    </mesh>
  );
}

/** The full city scene — grid roads with buildings/trees filling the blocks */
function CityScene({ basestationPositions = [] }: { basestationPositions?: [number, number, number][] }) {
  const { roadSegments, buildings, trees, lamps, cars } = useMemo(() => {
    const rng = seededRandom(42);

    // Grid parameters
    const GRID_SPACING = 3;
    const GRID_COUNT = 5;
    const HALF = (GRID_COUNT * GRID_SPACING) / 2;
    const ROAD_WIDTH = 0.25;

    // Generate grid road segments
    const roadSegments: Array<{ x: number; z: number; len: number; vert: boolean }> = [];
    for (let i = 0; i <= GRID_COUNT; i++) {
      const z = -HALF + i * GRID_SPACING;
      roadSegments.push({ x: 0, z, len: GRID_COUNT * GRID_SPACING, vert: false });
    }
    for (let i = 0; i <= GRID_COUNT; i++) {
      const x = -HALF + i * GRID_SPACING;
      roadSegments.push({ x, z: 0, len: GRID_COUNT * GRID_SPACING, vert: true });
    }

    // Helper: check if a point is too close to any basestation
    function nearBasestation(x: number, z: number, minDist: number): boolean {
      for (const pos of basestationPositions) {
        const dx = x - pos[0];
        const dz = z - pos[2];
        if (dx * dx + dz * dz < minDist * minDist) return true;
      }
      return false;
    }

    // Fill each city block with buildings and trees (with overlap prevention)
    const buildings: Array<{ x: number; z: number; w: number; d: number; h: number; style: number }> = [];
    const trees: Array<{ x: number; z: number }> = [];
    const occupied: Array<{ x: number; z: number; r: number }> = []; // placed items with radius

    function overlaps(x: number, z: number, radius: number): boolean {
      // Check against other placed items
      for (const o of occupied) {
        const dx = x - o.x;
        const dz = z - o.z;
        if (dx * dx + dz * dz < (radius + o.r) ** 2) return true;
      }
      // Check against basestations
      if (nearBasestation(x, z, radius + 0.6)) return true;
      return false;
    }

    for (let bx = 0; bx < GRID_COUNT; bx++) {
      for (let bz = 0; bz < GRID_COUNT; bz++) {
        const cx = -HALF + GRID_SPACING * 0.5 + bx * GRID_SPACING;
        const cz = -HALF + GRID_SPACING * 0.5 + bz * GRID_SPACING;

        // Skip blocks that are entirely within basestation zones
        if (nearBasestation(cx, cz, GRID_SPACING * 0.4)) continue;

        const blockInner = GRID_SPACING - ROAD_WIDTH - 0.3;
        const numBuildings = 2 + Math.floor(rng() * 3);

        for (let b = 0; b < numBuildings; b++) {
          // Try up to 10 times to find a non-overlapping position
          let placed = false;
          for (let attempt = 0; attempt < 10; attempt++) {
            const ox = (rng() - 0.5) * blockInner;
            const oz = (rng() - 0.5) * blockInner;
            const px = cx + ox;
            const pz = cz + oz;
            const w = 0.2 + rng() * 0.4;
            const d = 0.2 + rng() * 0.4;
            const radius = Math.max(w, d) * 0.6;

            if (!overlaps(px, pz, radius)) {
              const dist = Math.sqrt(cx * cx + cz * cz);
              const maxH = dist < 5 ? 0.9 : dist < 8 ? 0.6 : 0.35;
              const h = 0.15 + rng() * maxH;
              buildings.push({ x: px, z: pz, w, d, h, style: Math.floor(rng() * 4) });
              occupied.push({ x: px, z: pz, r: radius });
              placed = true;
              break;
            }
          }
          if (!placed) {
            // Consume the rng calls to keep determinism
            rng(); rng();
          }
        }

        // Add 1-2 trees per block (also avoid overlaps)
        const numTrees = 1 + Math.floor(rng() * 2);
        for (let t = 0; t < numTrees; t++) {
          for (let attempt = 0; attempt < 5; attempt++) {
            const ox = (rng() - 0.5) * blockInner;
            const oz = (rng() - 0.5) * blockInner;
            const px = cx + ox;
            const pz = cz + oz;
            if (!overlaps(px, pz, 0.15)) {
              trees.push({ x: px, z: pz });
              occupied.push({ x: px, z: pz, r: 0.15 });
              break;
            }
          }
        }
      }
    }

    // Street lamps along roads (at intersections)
    const lamps: Array<{ x: number; z: number }> = [];
    for (let i = 0; i <= GRID_COUNT; i++) {
      for (let j = 0; j <= GRID_COUNT; j++) {
        const x = -HALF + i * GRID_SPACING;
        const z = -HALF + j * GRID_SPACING;
        if (Math.abs(x) < 1 && Math.abs(z) < 1) continue;
        lamps.push({ x: x + 0.2, z: z + 0.2 });
      }
    }

    // Cars driving along some roads
    const carColors = ['#ef4444', '#3b82f6', '#f59e0b', '#10b981', '#8b5cf6', '#ffffff', '#f97316'];
    const cars: Array<{ sx: number; sz: number; ex: number; ez: number; speed: number; color: string }> = [];
    for (let i = 0; i < 10; i++) {
      const useHoriz = rng() > 0.5;
      const roadIdx = Math.floor(rng() * (GRID_COUNT + 1));
      const offset = (rng() - 0.5) * 0.06;
      const color = carColors[Math.floor(rng() * carColors.length)];
      const speed = 0.05 + rng() * 0.1;

      if (useHoriz) {
        const z = -HALF + roadIdx * GRID_SPACING + offset;
        cars.push({ sx: -HALF, sz: z, ex: HALF, ez: z, speed, color });
      } else {
        const x = -HALF + roadIdx * GRID_SPACING + offset;
        cars.push({ sx: x, sz: -HALF, ex: x, ez: HALF, speed, color });
      }
    }

    return { roadSegments, buildings, trees, lamps, cars };
  }, [basestationPositions]);

  return (
    <group>
      {/* Roads */}
      {roadSegments.map((r, i) => {
        const rotation: [number, number, number] = [-Math.PI / 2, 0, r.vert ? Math.PI / 2 : 0];
        return (
          <group key={`road-${i}`} position={[r.x, 0, r.z]}>
            {/* Asphalt */}
            <mesh rotation={rotation} position={[0, 0.003, 0]}>
              <planeGeometry args={[r.len, 0.25]} />
              <meshStandardMaterial color="#4b5563" roughness={0.95} />
            </mesh>
            {/* Center line */}
            <mesh rotation={rotation} position={[0, 0.004, 0]}>
              <planeGeometry args={[r.len, 0.02]} />
              <meshStandardMaterial color="#fbbf24" roughness={0.7} />
            </mesh>
          </group>
        );
      })}

      {/* Buildings */}
      {buildings.map((b, i) => (
        <Building key={`bld-${i}`} position={[b.x, 0, b.z]} width={b.w} depth={b.d} height={b.h} style={b.style} />
      ))}

      {/* Trees */}
      {trees.map((t, i) => (
        <Tree key={`tree-${i}`} position={[t.x, 0, t.z]} />
      ))}

      {/* Street lamps */}
      {lamps.map((l, i) => (
        <StreetLamp key={`lamp-${i}`} position={[l.x, 0, l.z]} />
      ))}

      {/* Cars */}
      {cars.map((c, i) => (
        <Car
          key={`car-${i}`}
          start={[c.sx, 0.04, c.sz]}
          end={[c.ex, 0.04, c.ez]}
          speed={c.speed}
          color={c.color}
        />
      ))}
    </group>
  );
}

export function MapEnvironment({ basestationPositions = [] }: { basestationPositions?: [number, number, number][] }) {
  return (
    <>
      {/* Bright daytime lighting */}
      <ambientLight intensity={1.1} color="#f8fafc" />
      <directionalLight position={[6, 14, 6]} intensity={1.5} color="#ffffff" castShadow />
      <directionalLight position={[-5, 8, -5]} intensity={0.4} color="#bfdbfe" />
      <hemisphereLight args={['#87ceeb', '#4ade80', 0.5]} />

      {/* Ground — green grass */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.01, 0]} receiveShadow>
        <planeGeometry args={[40, 40]} />
        <meshStandardMaterial color="#4ade80" roughness={0.92} metalness={0} />
      </mesh>

      {/* City grid scene */}
      <CityScene basestationPositions={basestationPositions} />
    </>
  );
}
