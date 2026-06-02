import { useEffect, useRef } from 'react';
import { motion, AnimatePresence, useMotionValue, useTransform, animate } from 'framer-motion';
import { Crown } from 'lucide-react';
import type { LeaderboardEntry } from '../../hooks/useGameState';

interface LeaderboardProps {
  entries: LeaderboardEntry[];
  currentPlayerId: number | null;
}

/** Animated number that counts up/down when value changes */
function AnimatedScore({ value, className }: { value: number; className?: string }) {
  const motionValue = useMotionValue(value);
  const rounded = useTransform(motionValue, (v) => v.toFixed(1));
  const ref = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const controls = animate(motionValue, value, {
      duration: 0.6,
      ease: 'easeOut',
    });
    return controls.stop;
  }, [value, motionValue]);

  useEffect(() => {
    const unsubscribe = rounded.on('change', (v) => {
      if (ref.current) {
        ref.current.textContent = v;
      }
    });
    return unsubscribe;
  }, [rounded]);

  return <span ref={ref} className={className}>{value.toFixed(1)}</span>;
}

/** Mini breakdown dots showing money/satisfaction/stability as coloured bars */
function ScoreBreakdown({ scores }: { scores: LeaderboardEntry['scores'] }) {
  const items = [
    { value: scores.money, color: 'bg-emerald-400', label: 'Money' },
    { value: scores.customerSatisfaction, color: 'bg-cyan-400', label: 'Satisfaction' },
    { value: scores.networkStability, color: 'bg-purple-400', label: 'Stability' },
  ];

  return (
    <div className="flex items-center gap-1.5 mt-1">
      {items.map((item) => (
        <div key={item.label} className="flex items-center gap-0.5" title={`${item.label}: ${item.value.toFixed(0)}%`}>
          <div className={`w-2 h-2 rounded-full ${item.color}`} />
          <span className="text-[10px] text-text-muted">{item.value.toFixed(0)}</span>
        </div>
      ))}
    </div>
  );
}

// NOTE: If the leaderboard grows significantly (50+ players), consider adding
// list virtualisation (e.g., @tanstack/react-virtual) for better scroll performance.

export function Leaderboard({ entries, currentPlayerId }: LeaderboardProps) {
  const sorted = [...entries].sort((a, b) => a.rank - b.rank);

  if (sorted.length === 0) {
    return (
      <div className="text-text-muted text-sm">
        <p>Leaderboard will update when the game starts.</p>
      </div>
    );
  }

  return (
    <div className="space-y-1">
      <AnimatePresence mode="popLayout">
        {sorted.map((entry) => {
          const isCurrentPlayer = entry.playerId === currentPlayerId;
          const isFirst = entry.rank === 1;

          return (
            <motion.div
              key={entry.playerId}
              layout
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{
                layout: { type: 'spring', stiffness: 500, damping: 35 },
                opacity: { duration: 0.2 },
              }}
              className={`flex items-center gap-3 p-2.5 rounded-lg transition-colors ${
                isCurrentPlayer
                  ? 'bg-primary/10 border border-primary/40'
                  : 'bg-surface-light'
              }`}
            >
              {/* Rank */}
              <div className="flex items-center justify-center w-7 h-7 shrink-0">
                {isFirst ? (
                  <Crown size={18} className="text-amber-400" />
                ) : (
                  <span className="text-sm font-bold text-text-muted">
                    #{entry.rank}
                  </span>
                )}
              </div>

              {/* Player info */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className={`text-sm font-medium truncate ${isCurrentPlayer ? 'text-primary' : 'text-text'}`}>
                    {entry.displayName}
                    {isCurrentPlayer && <span className="text-[10px] ml-1 text-text-muted">(you)</span>}
                  </span>
                  <AnimatedScore
                    value={entry.compositeScore}
                    className="text-sm font-bold text-primary tabular-nums"
                  />
                </div>
                <ScoreBreakdown scores={entry.scores} />
              </div>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
