import { useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown, DollarSign, Users, Activity } from 'lucide-react';
import type { LeaderboardEntry } from '../../hooks/useGameState';

interface ScoreSummaryProps {
  /** The current player's leaderboard entry (or undefined if not yet available) */
  entry: LeaderboardEntry | undefined;
}

interface TrendIndicatorProps {
  current: number;
  previous: number | null;
}

function TrendIndicator({ current, previous }: TrendIndicatorProps) {
  if (previous === null) return null;

  const diff = current - previous;
  if (Math.abs(diff) < 0.1) return null;

  if (diff > 0) {
    return <TrendingUp size={12} className="text-emerald-400" />;
  }
  return <TrendingDown size={12} className="text-red-400" />;
}

export function ScoreSummary({ entry }: ScoreSummaryProps) {
  // Track previous values for trend arrows
  const prevScoresRef = useRef<{ money: number; satisfaction: number; stability: number } | null>(null);
  const currentPrev = useRef<{ money: number; satisfaction: number; stability: number } | null>(null);

  useEffect(() => {
    if (entry) {
      // Store the previous values before updating
      currentPrev.current = prevScoresRef.current;
      prevScoresRef.current = {
        money: entry.scores.money,
        satisfaction: entry.scores.customerSatisfaction,
        stability: entry.scores.networkStability,
      };
    }
  }, [entry]);

  if (!entry) return null;

  const prev = currentPrev.current;

  const items = [
    {
      icon: DollarSign,
      label: 'Money',
      value: `€${entry.scores.money.toFixed(0)}`,
      color: 'text-emerald-400',
      current: entry.scores.money,
      previous: prev?.money ?? null,
    },
    {
      icon: Users,
      label: 'Satisfaction',
      value: `${entry.scores.customerSatisfaction.toFixed(0)}%`,
      color: 'text-cyan-400',
      current: entry.scores.customerSatisfaction,
      previous: prev?.satisfaction ?? null,
    },
    {
      icon: Activity,
      label: 'Stability',
      value: `${entry.scores.networkStability.toFixed(0)}%`,
      color: 'text-purple-400',
      current: entry.scores.networkStability,
      previous: prev?.stability ?? null,
    },
  ];

  return (
    <motion.div
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      className="absolute top-3 left-3 z-10 flex items-center gap-3 bg-surface/90 backdrop-blur-sm border border-surface-lighter rounded-lg px-3 py-2 shadow-lg"
    >
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <div key={item.label} className="flex items-center gap-1.5" title={item.label}>
            <Icon size={14} className={item.color} />
            <span className="text-xs font-medium text-text tabular-nums">{item.value}</span>
            <TrendIndicator current={item.current} previous={item.previous} />
          </div>
        );
      })}
    </motion.div>
  );
}
