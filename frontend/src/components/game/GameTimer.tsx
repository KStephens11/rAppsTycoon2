import { useEffect, useState } from 'react';
import { Timer } from 'lucide-react';

interface GameTimerProps {
  /** ISO-8601 timestamp of when the game started (from server) */
  startedAt: string;
  /** Total game duration in seconds — defaults to 300 (60 ticks × 5 s) */
  totalDurationSeconds?: number;
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

function parseUtcTimestamp(timestamp: string): number {
  // LocalDateTime from Spring Boot serialises without a timezone (no Z, no +offset).
  // Appending Z forces the browser to treat it as UTC, matching Date.now().
  const normalised = /[Zz]$|[+-]\d{2}:\d{2}$/.test(timestamp)
    ? timestamp
    : `${timestamp}Z`;
  return new Date(normalised).getTime();
}

function getRemainingSeconds(startedAt: string, totalDurationSeconds: number): number {
  const startMs = parseUtcTimestamp(startedAt);
  if (isNaN(startMs)) return totalDurationSeconds;
  const elapsedSec = (Date.now() - startMs) / 1000;
  return Math.max(0, totalDurationSeconds - elapsedSec);
}

export function GameTimer({
  startedAt,
  totalDurationSeconds = 300,
}: GameTimerProps) {
  const [remainingSec, setRemainingSec] = useState(() =>
    getRemainingSeconds(startedAt, totalDurationSeconds),
  );

  // Recalculate every second from the wall clock — survives refreshes perfectly
  useEffect(() => {
    // Sync immediately when startedAt changes (e.g. fetched after mount)
    setRemainingSec(getRemainingSeconds(startedAt, totalDurationSeconds));

    const id = setInterval(() => {
      setRemainingSec(getRemainingSeconds(startedAt, totalDurationSeconds));
    }, 1_000);

    return () => clearInterval(id);
  }, [startedAt, totalDurationSeconds]);

  const fraction = totalDurationSeconds > 0 ? remainingSec / totalDurationSeconds : 0;
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
        {formatTime(remainingSec)}
      </span>
    </div>
  );
}
