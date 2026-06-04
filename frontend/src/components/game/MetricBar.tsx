import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Heart,
  Users,
  Zap,
  Cpu,
  Shield,
  DollarSign,
  type LucideIcon,
} from 'lucide-react';

export type MetricKey =
  | 'health'
  | 'customerExperience'
  | 'energyEfficiency'
  | 'automationReliability'
  | 'slaCompliance'
  | 'cost';

interface MetricConfig {
  label: string;
  icon: LucideIcon;
  isCost: boolean;
}

const metricConfigs: Record<MetricKey, MetricConfig> = {
  health: { label: 'Health', icon: Heart, isCost: false },
  customerExperience: { label: 'Customer Experience', icon: Users, isCost: false },
  energyEfficiency: { label: 'Energy Efficiency', icon: Zap, isCost: false },
  automationReliability: { label: 'Automation Reliability', icon: Cpu, isCost: false },
  slaCompliance: { label: 'SLA Compliance', icon: Shield, isCost: false },
  cost: { label: 'Cost', icon: DollarSign, isCost: true },
};

/**
 * Interpolates between red (0) → yellow (50) → green (100)
 */
function getBarColour(value: number): string {
  const clamped = Math.max(0, Math.min(100, value));
  if (clamped <= 50) {
    // Red to Yellow
    const t = clamped / 50;
    const r = 239;
    const g = Math.round(68 + (181) * t); // 68 → 249
    const b = Math.round(68 * (1 - t)); // 68 → 0
    return `rgb(${r}, ${g}, ${b})`;
  }
  // Yellow to Green
  const t = (clamped - 50) / 50;
  const r = Math.round(249 * (1 - t) + 16 * t); // 249 → 16
  const g = Math.round(249 * (1 - t) + 185 * t); // 249 → 185
  const b = Math.round(0 + 129 * t); // 0 → 129
  return `rgb(${r}, ${g}, ${b})`;
}

interface MetricBarProps {
  metricKey: MetricKey;
  value: number;
}

export function MetricBar({ metricKey, value }: MetricBarProps) {
  const config = metricConfigs[metricKey];
  const Icon = config.icon;
  const prevValueRef = useRef(value);
  const [flash, setFlash] = useState(false);

  // Detect value changes and trigger flash
  useEffect(() => {
    if (prevValueRef.current !== value) {
      setFlash(true);
      prevValueRef.current = value;
      const timer = setTimeout(() => setFlash(false), 300);
      return () => clearTimeout(timer);
    }
  }, [value]);

  if (config.isCost) {
    return (
      <div className="flex items-center gap-2 py-1.5">
        <Icon size={14} className="text-text-muted shrink-0" />
        <span className="text-xs text-text-muted flex-1 truncate">{config.label}</span>
        <span className="text-sm font-medium text-text tabular-nums">
          €{value.toFixed(2)}
        </span>
      </div>
    );
  }

  const barColour = getBarColour(value);
  const percentage = Math.max(0, Math.min(100, value));

  return (
    <div className="py-1.5">
      <div className="flex items-center gap-2 mb-1">
        <Icon size={14} className="text-text-muted shrink-0" />
        <span className="text-xs text-text-muted flex-1 truncate">{config.label}</span>
        <span className="text-xs font-medium text-text tabular-nums">
          {value.toFixed(1)}%
        </span>
      </div>
      <div className="relative h-2 rounded-full bg-surface-lighter overflow-hidden">
        <motion.div
          className="absolute inset-y-0 left-0 rounded-full"
          style={{ backgroundColor: barColour }}
          animate={{ width: `${percentage}%` }}
          transition={{ type: 'spring', stiffness: 100, damping: 15 }}
        />
        {flash && (
          <motion.div
            className="absolute inset-0 rounded-full"
            initial={{ opacity: 0.6 }}
            animate={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
            style={{
              background: 'rgba(255, 255, 255, 0.4)',
              boxShadow: '0 0 8px 2px rgba(255, 255, 255, 0.3)',
            }}
          />
        )}
      </div>
    </div>
  );
}
