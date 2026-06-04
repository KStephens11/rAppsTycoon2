import { useEffect, useRef, useCallback } from 'react';

interface ActiveEvent {
  id: number;
  severity: string;
}

export interface MapBasestationData {
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
  activeEvents: ActiveEvent[];
}

interface GameMapProps {
  basestations: MapBasestationData[];
  onSelectBasestation?: (id: number, screenX: number, screenY: number) => void;
  onDropDeploy?: (templateId: number, basestationId: number) => void;
  dragState?: { templateId: number; name: string; icon: string } | null;
}

// ── constants ──────────────────────────────────────────────────────────────
const COLS = 60;
const ROWS = 60;
const WORLD_SIZE = 600;
const CELL = WORLD_SIZE / COLS;

// Tile pixel dimensions (base, before zoom)
const TW = 18;
const TH = 9;
const GAP = 1.5;
const MAX_BH = 80;

// Camera defaults
const DEFAULT_ZOOM = 2.0;
const MIN_ZOOM = 0.8;
const MAX_ZOOM = 4.0;
const ZOOM_SPEED = 0.1;

// ── seeded RNG ─────────────────────────────────────────────────────────────
function srand(seed: number) {
  let s = seed;
  return () => {
    s = Math.imul(s ^ (s >>> 17), 0x45d9f3b);
    s = Math.imul(s ^ (s >>> 13), 0x119de1f3);
    s ^= s >>> 15;
    return (s >>> 0) / 0xffffffff;
  };
}

function buildHeightMap(): number[][] {
  const rng = srand(0xdeadbeef);
  const f: number[][] = [];
  for (let r = 0; r < ROWS; r++) {
    f[r] = [];
    for (let c = 0; c < COLS; c++) f[r][c] = rng();
  }
  // smooth
  for (let p = 0; p < 5; p++) {
    for (let r = 1; r < ROWS - 1; r++) {
      for (let c = 1; c < COLS - 1; c++) {
        f[r][c] = (f[r - 1][c] + f[r + 1][c] + f[r][c - 1] + f[r][c + 1] + f[r][c] * 4) / 8;
      }
    }
  }
  // normalise + curve
  let mn = Infinity, mx = -Infinity;
  for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) { mn = Math.min(mn, f[r][c]); mx = Math.max(mx, f[r][c]); }
  for (let r = 0; r < ROWS; r++) {
    for (let c = 0; c < COLS; c++) {
      const t = (f[r][c] - mn) / (mx - mn);
      f[r][c] = t < 0.45 ? t * 0.35 * MAX_BH : (0.15 + Math.pow((t - 0.45) / 0.55, 1.4) * 0.85) * MAX_BH;
    }
  }
  return f;
}

const HEIGHT_MAP = buildHeightMap();

// ── helpers ────────────────────────────────────────────────────────────────
function lerp(a: number, b: number, t: number) { return a + (b - a) * t; }

function worldToGrid(v: number): number {
  return Math.min(COLS - 1, Math.max(0, Math.floor(v / CELL)));
}

function iso(c: number, r: number, ox: number, oy: number): [number, number] {
  return [
    ox + (c - r) * (TW / 2),
    oy + (c + r) * (TH / 2),
  ];
}

function hRGB(h: number): [number, number, number] {
  const t = Math.min(1, h / MAX_BH);
  if (t < 0.15) return [lerp(22, 28, t / 0.15), lerp(32, 40, t / 0.15), lerp(55, 72, t / 0.15)];
  if (t < 0.4)  { const f = (t - 0.15) / 0.25; return [lerp(28, 38, f), lerp(40, 68, f), lerp(72, 115, f)]; }
  if (t < 0.65) { const f = (t - 0.4)  / 0.25; return [lerp(38, 52, f), lerp(68, 108, f), lerp(115, 165, f)]; }
                { const f = (t - 0.65) / 0.35; return [lerp(52, 78, f), lerp(108, 162, f), lerp(165, 220, f)]; }
}

function faceColor(h: number, pulse: number, pulseColor: [number, number, number], face: 'top' | 'left' | 'right'): string {
  let [r, g, b] = hRGB(h);
  if (pulse > 0) {
    // Blend 50% original cyan with 50% status color for a subtler effect
    const cyanR = 6, cyanG = 182, cyanB = 212;
    const blendedR = (cyanR + pulseColor[0]) / 2;
    const blendedG = (cyanG + pulseColor[1]) / 2;
    const blendedB = (cyanB + pulseColor[2]) / 2;
    
    r = lerp(r, blendedR, pulse * 0.45);
    g = lerp(g, blendedG, pulse * 0.5);
    b = lerp(b, blendedB, pulse * 0.65);
  }
  const dim = face === 'left' ? 0.50 : face === 'right' ? 0.70 : 1.0;
  return `rgb(${Math.round(Math.min(255, r * dim))},${Math.round(Math.min(255, g * dim))},${Math.round(Math.min(255, b * dim))})`;
}

function getStatus(bs: MapBasestationData): 'ok' | 'warn' | 'critical' {
  const hasCritical = bs.activeEvents.some(e => e.severity === 'critical' || e.severity === 'CRITICAL');
  const hasWarn = bs.activeEvents.length > 0;
  if (hasCritical || bs.metrics.health < 50) return 'critical';
  if (hasWarn || bs.metrics.health < 80) return 'warn';
  return 'ok';
}

const STATUS_COLOR: Record<string, string> = {
  ok: '#4ade80',
  warn: '#facc15',
  critical: '#f87171',
};

// ── camera state ───────────────────────────────────────────────────────────
interface Camera {
  x: number;   // pan offset in canvas pixels
  y: number;
  zoom: number;
}

// ── component ──────────────────────────────────────────────────────────────
export function GameMap({ basestations, onSelectBasestation, onDropDeploy, dragState }: GameMapProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const rafRef = useRef<number>(0);
  const t0Ref = useRef<number | null>(null);
  const cameraRef = useRef<Camera>({ x: 0, y: 0, zoom: DEFAULT_ZOOM });
  const isDraggingRef = useRef(false);
  const lastMouseRef = useRef({ x: 0, y: 0 });
  const hasDraggedRef = useRef(false);
  const basestationsRef = useRef(basestations);
  const onSelectBasestationRef = useRef(onSelectBasestation);
  const onDropDeployRef = useRef(onDropDeploy);
  const dragStateRef = useRef(dragState);
  const hoveredStationRef = useRef<number | null>(null);

  // Update refs without triggering re-mount
  basestationsRef.current = basestations;
  onSelectBasestationRef.current = onSelectBasestation;
  onDropDeployRef.current = onDropDeploy;
  dragStateRef.current = dragState;

  const stationCells = useCallback(() =>
    basestationsRef.current.map(bs => ({
      ...bs,
      gc: worldToGrid(bs.positionX),
      gr: worldToGrid(bs.positionY),
      status: getStatus(bs),
    })),
    []
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const ctx = canvas.getContext('2d')!;

    function resize() {
      const { width, height } = container!.getBoundingClientRect();
      canvas!.width = width;
      canvas!.height = height;
    }
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(container);

    function getPulse(gc: number, gr: number, t: number): { intensity: number; color: [number, number, number] } {
      const stations = stationCells();
      let maxP = 0;
      let pulseColor: [number, number, number] = [6, 182, 212]; // Default cyan
      
      for (const st of stations) {
        const dist = Math.sqrt((gc - st.gc) ** 2 + (gr - st.gr) ** 2);
        const maxR = 8, speed = 0.7;
        for (let w = 0; w < 2; w++) {
          const front = ((t * speed + w * 0.5) % 1) * maxR;
          const delta = Math.abs(dist - front);
          if (delta < 2.0) {
            const intensity = Math.pow(1 - delta / 2, 2) * Math.max(0, 1 - dist / maxR);
            if (intensity * 0.8 > maxP) {
              maxP = intensity * 0.8;
              // Use the basestation's status color
              const statusColor = STATUS_COLOR[st.status];
              if (statusColor.startsWith('#')) {
                pulseColor = [
                  parseInt(statusColor.slice(1, 3), 16),
                  parseInt(statusColor.slice(3, 5), 16),
                  parseInt(statusColor.slice(5, 7), 16)
                ];
              }
            }
          }
        }
      }
      return { intensity: maxP, color: pulseColor };
    }

    function drawBlock(
      ctx: CanvasRenderingContext2D,
      gc: number, gr: number,
      bh: number, pulseData: { intensity: number; color: [number, number, number] },
      ox: number, oy: number,
    ) {
      const [tx, ty] = iso(gc, gr, ox, oy);
      const hw = TW / 2 - GAP;
      const pulse = pulseData.intensity;
      const pulseColor = pulseData.color;
      const hh = TH / 2 - GAP * 0.5;

      // left face (west)
      ctx.beginPath();
      ctx.moveTo(tx,      ty - bh);        // top center
      ctx.lineTo(tx - hw, ty - hh - bh);   // top left
      ctx.lineTo(tx - hw, ty - hh);        // bottom left
      ctx.lineTo(tx,      ty);             // bottom center
      ctx.closePath();
      ctx.fillStyle = faceColor(bh, pulse, pulseColor, 'left');
      ctx.fill();

      // right face (east)
      ctx.beginPath();
      ctx.moveTo(tx,      ty - bh);        // top center
      ctx.lineTo(tx + hw, ty - hh - bh);   // top right
      ctx.lineTo(tx + hw, ty - hh);        // bottom right
      ctx.lineTo(tx,      ty);             // bottom center
      ctx.closePath();
      ctx.fillStyle = faceColor(bh, pulse, pulseColor, 'right');
      ctx.fill();

      // top face (proper isometric diamond)
      ctx.beginPath();
      ctx.moveTo(tx,      ty - bh);          // front
      ctx.lineTo(tx + hw, ty - hh - bh);     // right
      ctx.lineTo(tx,      ty - hh - hh - bh); // back (symmetric with front-right-left)
      ctx.lineTo(tx - hw, ty - hh - bh);     // left
      ctx.closePath();
      ctx.fillStyle = faceColor(bh, pulse, pulseColor, 'top');
      ctx.fill();

      // subtle edge highlight on tall buildings
      if (bh > 25) {
        ctx.strokeStyle = `rgba(100,200,255,${0.05 + pulse * 0.1})`;
        ctx.lineWidth = 0.3;
        ctx.beginPath();
        ctx.moveTo(tx - hw, ty - hh - bh);
        ctx.lineTo(tx,      ty - TH - bh);
        ctx.lineTo(tx + hw, ty - hh - bh);
        ctx.stroke();
      }
    }

    function drawStation(
      ctx: CanvasRenderingContext2D,
      gc: number, gr: number,
      bh: number, status: string, t: number,
      ox: number, oy: number,
    ) {
      const [tx, ty] = iso(gc, gr, ox, oy);
      const sc = STATUS_COLOR[status];
      
      // Tower dimensions (isometric box)
      const towerHeight = 48;
      const towerWidthBase = 8;  // Half-width at base for isometric
      const towerWidthTop = 5;   // Half-width at top (tapered)
      
      // Platform dimensions
      const platHeight = 3;
      const platHw = TW / 2 - GAP;
      const platHh = TH / 2 - GAP * 0.5;
      
      // Base of platform sits exactly on building top
      const baseY = ty - bh;
      
      // === Platform (thin isometric box matching tile dimensions) ===
      // Platform bottom vertices (match building top exactly)
      const platBotFront = { x: tx, y: baseY };
      const platBotRight = { x: tx + platHw, y: baseY - platHh };
      const platBotLeft = { x: tx - platHw, y: baseY - platHh };
      
      // Platform top vertices
      const platTopFront = { x: tx, y: baseY - platHeight };
      const platTopRight = { x: tx + platHw, y: baseY - platHh - platHeight };
      const platTopBack = { x: tx, y: baseY - TH - platHeight };
      const platTopLeft = { x: tx - platHw, y: baseY - platHh - platHeight };
      
      // Draw platform left face
      ctx.fillStyle = 'rgba(25, 45, 85, 0.95)';
      ctx.beginPath();
      ctx.moveTo(platTopFront.x, platTopFront.y);
      ctx.lineTo(platTopLeft.x, platTopLeft.y);
      ctx.lineTo(platBotLeft.x, platBotLeft.y);
      ctx.lineTo(platBotFront.x, platBotFront.y);
      ctx.closePath();
      ctx.fill();
      
      // Draw platform right face
      ctx.fillStyle = 'rgba(35, 60, 100, 0.95)';
      ctx.beginPath();
      ctx.moveTo(platTopFront.x, platTopFront.y);
      ctx.lineTo(platTopRight.x, platTopRight.y);
      ctx.lineTo(platBotRight.x, platBotRight.y);
      ctx.lineTo(platBotFront.x, platBotFront.y);
      ctx.closePath();
      ctx.fill();
      
      // Draw platform top face
      ctx.fillStyle = 'rgba(45, 75, 120, 0.95)';
      ctx.beginPath();
      ctx.moveTo(platTopFront.x, platTopFront.y);
      ctx.lineTo(platTopRight.x, platTopRight.y);
      ctx.lineTo(platTopBack.x, platTopBack.y);
      ctx.lineTo(platTopLeft.x, platTopLeft.y);
      ctx.closePath();
      ctx.fill();

      // === Tower (isometric box with taper) ===
      // Tower sits on platform top, centered on the tile
      const towerBaseY = baseY - platHeight;
      const towerTopY = towerBaseY - towerHeight;
      
      // Tower bottom vertices (on platform top)
      const towerBotFront = { x: tx, y: towerBaseY };
      const towerBotRight = { x: tx + towerWidthBase, y: towerBaseY - towerWidthBase * 0.5 };
      const towerBotLeft = { x: tx - towerWidthBase, y: towerBaseY - towerWidthBase * 0.5 };
      
      // Tower top vertices (tapered)
      const towerTopFront = { x: tx, y: towerTopY };
      const towerTopRight = { x: tx + towerWidthTop, y: towerTopY - towerWidthTop * 0.5 };
      const towerTopBack = { x: tx, y: towerTopY - towerWidthTop };
      const towerTopLeft = { x: tx - towerWidthTop, y: towerTopY - towerWidthTop * 0.5 };
      
      // Draw tower left face
      const gradientLeft = ctx.createLinearGradient(towerBotLeft.x, towerBotLeft.y, towerTopLeft.x, towerTopLeft.y);
      gradientLeft.addColorStop(0, 'rgba(20, 36, 72, 0.95)');
      gradientLeft.addColorStop(1, 'rgba(30, 50, 95, 1)');
      ctx.fillStyle = gradientLeft;
      ctx.beginPath();
      ctx.moveTo(towerTopFront.x, towerTopFront.y);
      ctx.lineTo(towerTopLeft.x, towerTopLeft.y);
      ctx.lineTo(towerBotLeft.x, towerBotLeft.y);
      ctx.lineTo(towerBotFront.x, towerBotFront.y);
      ctx.closePath();
      ctx.fill();

      // Draw tower right face
      const gradientRight = ctx.createLinearGradient(towerBotRight.x, towerBotRight.y, towerTopRight.x, towerTopRight.y);
      gradientRight.addColorStop(0, 'rgba(30, 50, 95, 0.95)');
      gradientRight.addColorStop(1, 'rgba(40, 65, 115, 1)');
      ctx.fillStyle = gradientRight;
      ctx.beginPath();
      ctx.moveTo(towerTopFront.x, towerTopFront.y);
      ctx.lineTo(towerTopRight.x, towerTopRight.y);
      ctx.lineTo(towerBotRight.x, towerBotRight.y);
      ctx.lineTo(towerBotFront.x, towerBotFront.y);
      ctx.closePath();
      ctx.fill();

      // Draw tower top face
      ctx.fillStyle = 'rgba(50, 80, 130, 1)';
      ctx.beginPath();
      ctx.moveTo(towerTopFront.x, towerTopFront.y);
      ctx.lineTo(towerTopRight.x, towerTopRight.y);
      ctx.lineTo(towerTopBack.x, towerTopBack.y);
      ctx.lineTo(towerTopLeft.x, towerTopLeft.y);
      ctx.closePath();
      ctx.fill();

      // Tower accent stripes
      ctx.lineWidth = 1.5;
      for (let i = 0; i < 3; i++) {
        const ratio = 0.25 + i * 0.25;
        const stripeY = towerBaseY - towerHeight * ratio;
        const stripeW = towerWidthBase + (towerWidthTop - towerWidthBase) * ratio;
        const stripeHh = stripeW * 0.5;
        
        ctx.strokeStyle = 'rgba(60, 100, 170, 0.6)';
        ctx.beginPath();
        ctx.moveTo(tx - stripeW, stripeY - stripeHh);
        ctx.lineTo(tx, stripeY);
        ctx.lineTo(tx + stripeW, stripeY - stripeHh);
        ctx.stroke();
      }

      // === Antenna dish (isometric) ===
      const dishY = towerTopY - 8;
      const dishSize = 10;
      const dishDepth = 4;

      // Dish support arm (vertical)
      ctx.strokeStyle = 'rgba(50, 80, 130, 0.95)';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(tx, towerTopY);
      ctx.lineTo(tx, dishY);
      ctx.stroke();

      // Dish as isometric hexagon
      // Dish left face
      ctx.fillStyle = 'rgba(35, 60, 110, 0.95)';
      ctx.beginPath();
      ctx.moveTo(tx, dishY - dishDepth);
      ctx.lineTo(tx - dishSize, dishY - dishSize * 0.5 - dishDepth);
      ctx.lineTo(tx - dishSize, dishY - dishSize * 0.5);
      ctx.lineTo(tx, dishY);
      ctx.closePath();
      ctx.fill();

      // Dish right face
      ctx.fillStyle = 'rgba(45, 75, 125, 0.95)';
      ctx.beginPath();
      ctx.moveTo(tx, dishY - dishDepth);
      ctx.lineTo(tx + dishSize, dishY - dishSize * 0.5 - dishDepth);
      ctx.lineTo(tx + dishSize, dishY - dishSize * 0.5);
      ctx.lineTo(tx, dishY);
      ctx.closePath();
      ctx.fill();

      // Dish front face
      ctx.fillStyle = 'rgba(55, 90, 145, 0.95)';
      ctx.beginPath();
      ctx.moveTo(tx, dishY - dishDepth);
      ctx.lineTo(tx + dishSize, dishY - dishSize * 0.5 - dishDepth);
      ctx.lineTo(tx, dishY - dishSize - dishDepth);
      ctx.lineTo(tx - dishSize, dishY - dishSize * 0.5 - dishDepth);
      ctx.closePath();
      ctx.fill();

      // === Status light ===
      const lightY = dishY - dishSize - dishDepth - 4;
      const pulse = 0.5 + 0.5 * Math.sin(t * 3.5 + gc * 1.3);
      
      // Light glow
      ctx.shadowColor = sc;
      ctx.shadowBlur = 20 * pulse;
      
      // Outer sphere
      ctx.fillStyle = sc;
      ctx.globalAlpha = 0.85 + 0.15 * pulse;
      ctx.beginPath();
      ctx.arc(tx, lightY, 5, 0, Math.PI * 2);
      ctx.fill();
      
      // Inner highlight
      ctx.globalAlpha = 1;
      ctx.shadowBlur = 0;
      const highlightGradient = ctx.createRadialGradient(tx - 1.5, lightY - 1.5, 0, tx, lightY, 5);
      highlightGradient.addColorStop(0, 'rgba(255, 255, 255, 0.9)');
      highlightGradient.addColorStop(0.3, 'rgba(255, 255, 255, 0.3)');
      highlightGradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
      ctx.fillStyle = highlightGradient;
      ctx.beginPath();
      ctx.arc(tx, lightY, 5, 0, Math.PI * 2);
      ctx.fill();

      // Radio waves with status color
      const scRgb = sc.startsWith('#') ? {
        r: parseInt(sc.slice(1, 3), 16),
        g: parseInt(sc.slice(3, 5), 16),
        b: parseInt(sc.slice(5, 7), 16)
      } : { r: 74, g: 222, b: 128 };
      
      const cyanR = 6, cyanG = 182, cyanB = 212;
      const blendR = Math.round(cyanR * 0.3 + scRgb.r * 0.7);
      const blendG = Math.round(cyanG * 0.3 + scRgb.g * 0.7);
      const blendB = Math.round(cyanB * 0.3 + scRgb.b * 0.7);
      
      ctx.strokeStyle = `rgba(${blendR}, ${blendG}, ${blendB}, ${0.2 * pulse})`;
      ctx.lineWidth = 1.5;
      for (let r = 1; r <= 3; r++) {
        ctx.beginPath();
        ctx.arc(tx, lightY, 8 + r * 4, 0, Math.PI * 2);
        ctx.stroke();
      }
    }

    function drawNameplate(
      ctx: CanvasRenderingContext2D,
      tx: number,
      baseY: number,
      name: string,
    ) {
      ctx.shadowBlur = 0;
      ctx.globalAlpha = 1;
      
      // Nameplate position (below platform)
      const nameplateY = baseY + 8;
      
      // Measure text
      ctx.font = 'bold 9px sans-serif';
      const textWidth = ctx.measureText(name).width;
      const padding = 4;
      const plateWidth = textWidth + padding * 2;
      const plateHeight = 14;
      
      // Nameplate background (rounded rectangle)
      const plateLeft = tx - plateWidth / 2;
      const plateTop = nameplateY;
      const radius = 3;
      
      ctx.fillStyle = 'rgba(15, 20, 35, 0.85)';
      ctx.beginPath();
      ctx.roundRect(plateLeft, plateTop, plateWidth, plateHeight, radius);
      ctx.fill();
      
      // Nameplate border
      ctx.strokeStyle = 'rgba(60, 80, 120, 0.6)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.roundRect(plateLeft, plateTop, plateWidth, plateHeight, radius);
      ctx.stroke();
      
      // Nameplate text
      ctx.fillStyle = '#e5e7eb';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(name, tx, nameplateY + plateHeight / 2);
    }

    function frame(ts: number) {
      if (!t0Ref.current) t0Ref.current = ts;
      const t = (ts - t0Ref.current) / 1000;
      const wt = t / 4.5;

      const W = canvas!.width;
      const H = canvas!.height;
      const cam = cameraRef.current;

      ctx.clearRect(0, 0, W, H);
      ctx.fillStyle = '#06091a';
      ctx.fillRect(0, 0, W, H);

      // Apply camera transform
      ctx.save();
      ctx.translate(W / 2 + cam.x, H / 2 + cam.y);
      ctx.scale(cam.zoom, cam.zoom);

      // Origin for iso grid (centred at 0,0 in world space)
      const ox = 0;
      const oy = -(ROWS * TH) / 4;

      // Draw infinite isometric grid plane (only visible portion)
      // Isometric grid lines follow the tile edges, forming diamond shapes
      const visibleLeft = (-W / 2 - cam.x) / cam.zoom;
      const visibleRight = (W / 2 - cam.x) / cam.zoom;
      const visibleTop = (-H / 2 - cam.y) / cam.zoom;
      const visibleBottom = (H / 2 - cam.y) / cam.zoom;
      
      const gridPadding = 300;
      
      ctx.strokeStyle = 'rgba(60, 80, 120, 0.12)';
      ctx.lineWidth = 0.5;
      
      // Estimate which grid cells might be visible
      // Reduce range for better performance
      const minCol = Math.floor((visibleLeft - gridPadding) / (TW / 2)) - 10;
      const maxCol = Math.ceil((visibleRight + gridPadding) / (TW / 2)) + 10;
      const minRow = Math.floor((visibleTop - gridPadding) / (TH / 2)) - 20;
      const maxRow = Math.ceil((visibleBottom + gridPadding) / (TH / 2)) + 20;
      
      // Batch all lines into a single path for better performance
      const hw = TW / 2 - GAP;
      const hh = TH / 2 - GAP * 0.5;
      
      ctx.beginPath();
      
      // Draw column lines (constant c, varying r)
      for (let c = minCol; c <= maxCol; c++) {
        const [cx1, cy1] = iso(c, minRow, ox, oy);
        const [cx2, cy2] = iso(c, maxRow, ox, oy);
        ctx.moveTo(cx1 + hw, cy1 - hh);
        ctx.lineTo(cx2 + hw, cy2 - hh);
      }
      
      // Draw row lines (constant r, varying c)
      for (let r = minRow; r <= maxRow; r++) {
        const [cx1, cy1] = iso(minCol, r, ox, oy);
        const [cx2, cy2] = iso(maxCol, r, ox, oy);
        ctx.moveTo(cx1 - hw, cy1 - hh);
        ctx.lineTo(cx2 - hw, cy2 - hh);
      }
      
      ctx.stroke();

      const stations = stationCells();

      // Create a map of stations by grid position for quick lookup
      const stationMap = new Map<string, typeof stations[0]>();
      for (const st of stations) {
        stationMap.set(`${st.gc},${st.gr}`, st);
      }

      // painter's order: back-to-front diagonal, interleaving buildings and stations
      for (let diag = 0; diag < COLS + ROWS - 1; diag++) {
        for (let r = 0; r < ROWS; r++) {
          const c = diag - r;
          if (c < 0 || c >= COLS) continue;
          const bh = HEIGHT_MAP[r][c];
          const pulseData = getPulse(c, r, wt);
          drawBlock(ctx, c, r, bh, pulseData, ox, oy);
          
          // If there's a station at this grid position, draw it immediately after the building
          const station = stationMap.get(`${c},${r}`);
          if (station) {
            drawStation(ctx, station.gc, station.gr, bh, station.status, t, ox, oy);
          }
        }
      }

      // Draw nameplates on top of everything
      for (const st of stations) {
        const [tx, ty] = iso(st.gc, st.gr, ox, oy);
        const bh = HEIGHT_MAP[st.gr][st.gc];
        const baseY = ty - bh;
        drawNameplate(ctx, tx, baseY, st.name);
      }

      // Draw drop zone highlights (on top of everything)
      for (const st of stations) {
        // Draw drop zone highlight if dragging over this station
        if (hoveredStationRef.current === st.id && dragStateRef.current) {
          const [tx, ty] = iso(st.gc, st.gr, ox, oy);
          const bh = HEIGHT_MAP[st.gr][st.gc];
          const topY = ty - TH - bh;
          
          ctx.strokeStyle = 'rgba(6, 182, 212, 0.6)';
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.arc(tx, topY - 30, 25, 0, Math.PI * 2);
          ctx.stroke();
          
          ctx.fillStyle = 'rgba(6, 182, 212, 0.15)';
          ctx.beginPath();
          ctx.arc(tx, topY - 30, 25, 0, Math.PI * 2);
          ctx.fill();
        }
      }

      ctx.restore();

      rafRef.current = requestAnimationFrame(frame);
    }

    rafRef.current = requestAnimationFrame(frame);

    // ── Mouse pan ──────────────────────────────────────────────────────────
    function onMouseDown(e: MouseEvent) {
      if (e.button !== 0) return;
      isDraggingRef.current = true;
      hasDraggedRef.current = false;
      lastMouseRef.current = { x: e.clientX, y: e.clientY };
    }

    function onMouseMove(e: MouseEvent) {
      if (isDraggingRef.current) {
        const dx = e.clientX - lastMouseRef.current.x;
        const dy = e.clientY - lastMouseRef.current.y;
        if (Math.abs(dx) > 2 || Math.abs(dy) > 2) {
          hasDraggedRef.current = true;
        }
        cameraRef.current.x += dx;
        cameraRef.current.y += dy;
        lastMouseRef.current = { x: e.clientX, y: e.clientY };
      } else if (dragStateRef.current) {
        // Check if hovering over a station during drag-and-drop
        const canvas = canvasRef.current;
        if (!canvas) return;
        
        const rect = canvas.getBoundingClientRect();
        const cam = cameraRef.current;
        const sx = e.clientX - rect.left - rect.width / 2 - cam.x;
        const sy = e.clientY - rect.top - rect.height / 2 - cam.y;
        const wx = sx / cam.zoom;
        const wy = sy / cam.zoom;
        const ox = 0;
        const oy = -(ROWS * TH) / 4;
        
        let foundStation: number | null = null;
        const stations = stationCells();
        for (const st of stations) {
          const [tx, ty] = iso(st.gc, st.gr, ox, oy);
          const bh = HEIGHT_MAP[st.gr][st.gc];
          const baseY = ty - TH - bh;
          const topY = baseY - 62;
          
          // Rectangular hitbox covering the entire tower
          const towerHalfWidth = 16;
          const isInHorizontalRange = Math.abs(wx - tx) < towerHalfWidth;
          const isInVerticalRange = wy > topY - 15 && wy < baseY + 6;
          
          if (isInHorizontalRange && isInVerticalRange) {
            foundStation = st.id;
            break;
          }
        }
        hoveredStationRef.current = foundStation;
      }
    }

    function onMouseUp() {
      isDraggingRef.current = false;
    }

    // ── Touch pan ──────────────────────────────────────────────────────────
    let lastTouchDist = 0;
    let lastTouchCenter = { x: 0, y: 0 };

    function onTouchStart(e: TouchEvent) {
      if (e.touches.length === 1) {
        isDraggingRef.current = true;
        hasDraggedRef.current = false;
        lastMouseRef.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
      } else if (e.touches.length === 2) {
        isDraggingRef.current = false;
        const dx = e.touches[1].clientX - e.touches[0].clientX;
        const dy = e.touches[1].clientY - e.touches[0].clientY;
        lastTouchDist = Math.sqrt(dx * dx + dy * dy);
        lastTouchCenter = {
          x: (e.touches[0].clientX + e.touches[1].clientX) / 2,
          y: (e.touches[0].clientY + e.touches[1].clientY) / 2,
        };
      }
      e.preventDefault();
    }

    function onTouchMove(e: TouchEvent) {
      if (e.touches.length === 1 && isDraggingRef.current) {
        const dx = e.touches[0].clientX - lastMouseRef.current.x;
        const dy = e.touches[0].clientY - lastMouseRef.current.y;
        if (Math.abs(dx) > 2 || Math.abs(dy) > 2) hasDraggedRef.current = true;
        cameraRef.current.x += dx;
        cameraRef.current.y += dy;
        lastMouseRef.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
      } else if (e.touches.length === 2) {
        const dx = e.touches[1].clientX - e.touches[0].clientX;
        const dy = e.touches[1].clientY - e.touches[0].clientY;
        const dist = Math.sqrt(dx * dx + dy * dy);
        const center = {
          x: (e.touches[0].clientX + e.touches[1].clientX) / 2,
          y: (e.touches[0].clientY + e.touches[1].clientY) / 2,
        };

        if (lastTouchDist > 0) {
          const scale = dist / lastTouchDist;
          const newZoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, cameraRef.current.zoom * scale));
          cameraRef.current.zoom = newZoom;

          // Pan with pinch centre
          cameraRef.current.x += center.x - lastTouchCenter.x;
          cameraRef.current.y += center.y - lastTouchCenter.y;
        }
        lastTouchDist = dist;
        lastTouchCenter = center;
      }
      e.preventDefault();
    }

    function onTouchEnd(e: TouchEvent) {
      if (e.touches.length < 2) lastTouchDist = 0;
      if (e.touches.length === 0) isDraggingRef.current = false;
    }

    // ── Scroll zoom ────────────────────────────────────────────────────────
    function onWheel(e: WheelEvent) {
      e.preventDefault();
      const cam = cameraRef.current;
      const rect = canvas!.getBoundingClientRect();
      const mx = e.clientX - rect.left - rect.width / 2 - cam.x;
      const my = e.clientY - rect.top - rect.height / 2 - cam.y;

      const direction = e.deltaY < 0 ? 1 : -1;
      const factor = 1 + ZOOM_SPEED * direction;
      const newZoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, cam.zoom * factor));
      const scale = newZoom / cam.zoom;

      // Zoom towards cursor
      cam.x -= mx * (scale - 1);
      cam.y -= my * (scale - 1);
      cam.zoom = newZoom;
    }

    // ── Click detection ────────────────────────────────────────────────────
    function onClick(e: MouseEvent) {
      const callback = onSelectBasestationRef.current;
      const dropCallback = onDropDeployRef.current;
      const currentDragState = dragStateRef.current;
      
      // If dragging a rApp and clicking on a station, deploy it
      if (currentDragState && !hasDraggedRef.current && dropCallback) {
        const rect = canvas!.getBoundingClientRect();
        const cam = cameraRef.current;
        const sx = e.clientX - rect.left - rect.width / 2 - cam.x;
        const sy = e.clientY - rect.top - rect.height / 2 - cam.y;
        const wx = sx / cam.zoom;
        const wy = sy / cam.zoom;
        const ox = 0;
        const oy = -(ROWS * TH) / 4;
        
        const stations = stationCells();
        for (const st of stations) {
          const [tx, ty] = iso(st.gc, st.gr, ox, oy);
          const bh = HEIGHT_MAP[st.gr][st.gc];
          const baseY = ty - TH - bh;
          const topY = baseY - 62;  // Tower height (48 + 14)
          
          // Rectangular hitbox covering the entire tower width and height
          const towerHalfWidth = 16;  // Wider than just the light
          const isInHorizontalRange = Math.abs(wx - tx) < towerHalfWidth;
          const isInVerticalRange = wy > topY - 15 && wy < baseY + 6;
          
          if (isInHorizontalRange && isInVerticalRange) {
            dropCallback(currentDragState.templateId, st.id);
            hoveredStationRef.current = null;
            return;
          }
        }
        // Clicked elsewhere while dragging - clear hover
        hoveredStationRef.current = null;
        return;
      }
      
      // Normal click to select basestation
      if (!callback || hasDraggedRef.current || currentDragState) return;
      const rect = canvas!.getBoundingClientRect();
      const cam = cameraRef.current;

      // Convert screen coords to world (pre-transform) coords
      const sx = e.clientX - rect.left - rect.width / 2 - cam.x;
      const sy = e.clientY - rect.top - rect.height / 2 - cam.y;
      const wx = sx / cam.zoom;
      const wy = sy / cam.zoom;

      const ox = 0;
      const oy = -(ROWS * TH) / 4;

      const stations = stationCells();
      for (const st of stations) {
        const [tx, ty] = iso(st.gc, st.gr, ox, oy);
        const bh = HEIGHT_MAP[st.gr][st.gc];
        const baseY = ty - TH - bh;
        const topY = baseY - 62;  // Tower height (48 + 14)
        
        // Rectangular hitbox covering the entire tower
        const towerHalfWidth = 16;
        const isInHorizontalRange = Math.abs(wx - tx) < towerHalfWidth;
        const isInVerticalRange = wy > topY - 15 && wy < baseY + 6;
        
        if (isInHorizontalRange && isInVerticalRange) {
          // Convert back to screen coords for popover positioning
          const screenX = rect.left + rect.width / 2 + cam.x + tx * cam.zoom;
          const screenY = rect.top + rect.height / 2 + cam.y + (topY - 8) * cam.zoom;
          callback(st.id, screenX, screenY);
          return;
        }
      }
    }

    // ── HTML5 Drag Events (for rApp deployment) ──────────────────────────
    function onDragOver(e: DragEvent) {
      e.preventDefault(); // Required to allow drop
      e.dataTransfer!.dropEffect = 'move';
      
      // Update hover state based on mouse position
      if (dragStateRef.current) {
        const rect = canvas!.getBoundingClientRect();
        const cam = cameraRef.current;
        const sx = e.clientX - rect.left - rect.width / 2 - cam.x;
        const sy = e.clientY - rect.top - rect.height / 2 - cam.y;
        const wx = sx / cam.zoom;
        const wy = sy / cam.zoom;
        const ox = 0;
        const oy = -(ROWS * TH) / 4;
        
        let foundStation: number | null = null;
        const stations = stationCells();
        for (const st of stations) {
          const [tx, ty] = iso(st.gc, st.gr, ox, oy);
          const bh = HEIGHT_MAP[st.gr][st.gc];
          const baseY = ty - TH - bh;
          const topY = baseY - 62;
          
          const towerHalfWidth = 16;
          const isInHorizontalRange = Math.abs(wx - tx) < towerHalfWidth;
          const isInVerticalRange = wy > topY - 15 && wy < baseY + 6;
          
          if (isInHorizontalRange && isInVerticalRange) {
            foundStation = st.id;
            break;
          }
        }
        hoveredStationRef.current = foundStation;
      }
    }

    function onDrop(e: DragEvent) {
      e.preventDefault();
      const dropCallback = onDropDeployRef.current;
      const currentDragState = dragStateRef.current;
      
      if (!currentDragState || !dropCallback) return;
      
      const rect = canvas!.getBoundingClientRect();
      const cam = cameraRef.current;
      const sx = e.clientX - rect.left - rect.width / 2 - cam.x;
      const sy = e.clientY - rect.top - rect.height / 2 - cam.y;
      const wx = sx / cam.zoom;
      const wy = sy / cam.zoom;
      const ox = 0;
      const oy = -(ROWS * TH) / 4;
      
      const stations = stationCells();
      for (const st of stations) {
        const [tx, ty] = iso(st.gc, st.gr, ox, oy);
        const bh = HEIGHT_MAP[st.gr][st.gc];
        const baseY = ty - TH - bh;
        const topY = baseY - 62;
        
        const towerHalfWidth = 16;
        const isInHorizontalRange = Math.abs(wx - tx) < towerHalfWidth;
        const isInVerticalRange = wy > topY - 15 && wy < baseY + 6;
        
        if (isInHorizontalRange && isInVerticalRange) {
          dropCallback(currentDragState.templateId, st.id);
          hoveredStationRef.current = null;
          return;
        }
      }
      hoveredStationRef.current = null;
    }

    canvas.addEventListener('mousedown', onMouseDown);
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
    canvas.addEventListener('wheel', onWheel, { passive: false });
    canvas.addEventListener('touchstart', onTouchStart, { passive: false });
    canvas.addEventListener('touchmove', onTouchMove, { passive: false });
    canvas.addEventListener('touchend', onTouchEnd);
    canvas.addEventListener('click', onClick);
    canvas.addEventListener('dragover', onDragOver);
    canvas.addEventListener('drop', onDrop);

    return () => {
      cancelAnimationFrame(rafRef.current);
      ro.disconnect();
      canvas.removeEventListener('mousedown', onMouseDown);
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
      canvas.removeEventListener('wheel', onWheel);
      canvas.removeEventListener('touchstart', onTouchStart);
      canvas.removeEventListener('touchmove', onTouchMove);
      canvas.removeEventListener('touchend', onTouchEnd);
      canvas.removeEventListener('click', onClick);
      canvas.removeEventListener('dragover', onDragOver);
      canvas.removeEventListener('drop', onDrop);
    };
  }, [stationCells]);

  return (
    <div ref={containerRef} className="w-full h-full">
      <canvas
        ref={canvasRef}
        className="w-full h-full"
        style={{ cursor: dragStateRef.current ? 'crosshair' : (isDraggingRef.current ? 'grabbing' : 'grab') }}
      />
    </div>
  );
}
