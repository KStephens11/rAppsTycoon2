import { motion } from 'framer-motion';
import type { ConnectionState } from '../../hooks/useWebSocket';

interface ConnectionStatusProps {
  state: ConnectionState;
}

const statusConfig: Record<ConnectionState, { color: string; bgColor: string; label: string }> = {
  connected: {
    color: 'bg-emerald-400',
    bgColor: 'bg-emerald-400/10 border-emerald-400/30',
    label: 'Connected',
  },
  connecting: {
    color: 'bg-amber-400',
    bgColor: 'bg-amber-400/10 border-amber-400/30',
    label: 'Reconnecting...',
  },
  disconnected: {
    color: 'bg-red-400',
    bgColor: 'bg-red-400/10 border-red-400/30',
    label: 'Disconnected',
  },
};

export function ConnectionStatus({ state }: ConnectionStatusProps) {
  const config = statusConfig[state];

  return (
    <motion.div
      className={`fixed top-4 right-4 z-50 flex items-center gap-2 px-3 py-1.5 rounded-full border text-xs font-medium ${config.bgColor}`}
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      role="status"
      aria-live="polite"
      aria-label={`Connection status: ${config.label}`}
    >
      <motion.span
        className={`w-2 h-2 rounded-full ${config.color}`}
        animate={
          state === 'connecting'
            ? { opacity: [1, 0.4, 1] }
            : { opacity: 1 }
        }
        transition={
          state === 'connecting'
            ? { duration: 1.2, repeat: Infinity, ease: 'easeInOut' }
            : {}
        }
      />
      <span className="text-text-muted">{config.label}</span>
    </motion.div>
  );
}
