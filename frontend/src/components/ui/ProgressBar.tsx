import { motion } from 'framer-motion';

interface ProgressBarProps {
  value: number; // 0-100
  color?: string;
  className?: string;
  label?: string;
}

export function ProgressBar({
  value,
  color = 'bg-primary',
  className = '',
  label,
}: ProgressBarProps) {
  const clampedValue = Math.max(0, Math.min(100, value));

  return (
    <div className={`w-full ${className}`}>
      {label && (
        <div className="mb-1 flex items-center justify-between text-xs">
          <span className="text-text-muted">{label}</span>
          <span className="text-text font-medium">{clampedValue.toFixed(0)}%</span>
        </div>
      )}
      <div className="h-2 w-full overflow-hidden rounded-full bg-surface-lighter">
        <motion.div
          className={`h-full rounded-full ${color}`}
          initial={{ width: 0 }}
          animate={{ width: `${clampedValue}%` }}
          transition={{ type: 'spring', stiffness: 100, damping: 20 }}
        />
      </div>
    </div>
  );
}
