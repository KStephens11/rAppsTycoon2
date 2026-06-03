import { useEffect, useState, useRef } from 'react';
import { Timer } from 'lucide-react';

interface GameTimerProps {
  /** Current tick from the server */
  currentTick: number;
  /** Total ticks for the game */
  totalTicks: number;
  /** Tick interval in ms (default 5000) */
  tickIntervalMs?: number;
}

function formatTime(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const m = Math.floor(s / 60);
  const rem = s % 60;
  return `${m}:${String(rem).padStart(2, '0')}`;
}

function getColour(fraction: number): { text: string; ring: string } {
  if (fraction > 0.5) return { text: 'text-emerald-400', ring: 'bg-emerald-400' };
  if (fraction > 0.25) return { text: 'text-amber-400', ring: 'bg-amber-400' };
  return { text: 'text-red-400', ring: 'bg-red-400' };
}

export function GameTimer({
  currentTick,
  totalTicks,
  tickIntervalMs = 5000,
}: GameTimerProps) {
  const tickIntervalSec = tickIntervalMs / 1000;

  // Remaining ticks from server
  const remainingTicks = Math.max(0, totalTicks - currentTick);

  // Base remaining seconds from server state
  const serverRemainingSec = remainingTicks * tickIntervalSec;

  // Smooth interpolation: count down between server updates
  const [displaySec, setDisplaySec] = useState(serverRemainingSec);
  const lastServerUpdateRef = useRef(Date.now());
  const lastServerSecRef = useRef(serverRemainingSec);

  // When server data updates, reset the interpolation baseline
  useEffect(() => {
    lastServerUpdateRef.current = Date.now();
    lastServerSecRef.current = serverRemainingSec;
    setDisplaySec(serverRemainingSec);
  }, [serverRemainingSec]);

  // Tick down locally every second for smooth countdown
  useEffect(() => {
    const id = setInterval(() => {
      const elapsedSinceUpdate = (Date.now() - lastServerUpdateRef.current) / 1000;
      const interpolated = Math.max(0, lastServerSecRef.current - elapsedSinceUpdate);
      setDisplaySec(interpolated);
    }, 1000);
    return () => clearInterval(id);
  }, []);

  const fraction = totalTicks > 0 ? displaySec / (totalTicks * tickIntervalSec) : 0;
  const { text, ring } = getColour(fraction);
  const isUrgent = fraction <= 0.25;

  return (
    <div
      className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border transition-colors ${
        isUrgent
          ? 'bg-red-500/10 border-red-500/30'
          : fraction <= 0.5
          ? 'bg-amber-500/10 border-amber-500/30'
          : 'bg-surface-light border-surface-lighter'
      }`}
      title={`${Math.round(fraction * 100)}% time remaining`}
    >
      <span
        className={`w-2 h-2 rounded-full shrink-0 ${ring} ${isUrgent ? 'animate-pulse' : ''}`}
      />
      <Timer size={13} className={`shrink-0 ${text}`} />
      <span className={`text-xs font-bold tabular-nums ${text}`}>
        {formatTime(displaySec)}
      </span>
    </div>
  );
}
