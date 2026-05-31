import { useEffect, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Zap, Wifi, HardDrive, Shield, Cloud, AlertTriangle } from 'lucide-react';
import type { GameEvent } from '../../hooks/useGameState';

// --- Types ---

export interface EventAlertData {
  id: string;
  event: GameEvent;
  createdAt: number;
}

// --- Icon mapping by event type ---

const EVENT_TYPE_ICONS: Record<string, typeof Zap> = {
  POWER_OUTAGE: Zap,
  NETWORK_CONGESTION: Wifi,
  HARDWARE_FAILURE: HardDrive,
  SECURITY_BREACH: Shield,
  WEATHER_EVENT: Cloud,
};

// --- Severity colour mapping ---

const SEVERITY_COLOURS: Record<string, { bg: string; border: string; text: string; progress: string }> = {
  LOW: {
    bg: 'bg-blue-500/10',
    border: 'border-blue-500/40',
    text: 'text-blue-400',
    progress: 'bg-blue-500',
  },
  MEDIUM: {
    bg: 'bg-amber-500/10',
    border: 'border-amber-500/40',
    text: 'text-amber-400',
    progress: 'bg-amber-500',
  },
  HIGH: {
    bg: 'bg-orange-500/10',
    border: 'border-orange-500/40',
    text: 'text-orange-400',
    progress: 'bg-orange-500',
  },
  CRITICAL: {
    bg: 'bg-red-500/10',
    border: 'border-red-500/40',
    text: 'text-red-400',
    progress: 'bg-red-500',
  },
};

const AUTO_DISMISS_MS = 5000;

// --- Single EventAlert toast ---

function EventAlertItem({
  alert,
  onDismiss,
}: {
  alert: EventAlertData;
  onDismiss: (id: string) => void;
}) {
  const [progress, setProgress] = useState(100);

  useEffect(() => {
    const startTime = alert.createdAt;
    let rafId: number;

    function tick() {
      const elapsed = Date.now() - startTime;
      const remaining = Math.max(0, 100 - (elapsed / AUTO_DISMISS_MS) * 100);
      setProgress(remaining);

      if (remaining <= 0) {
        onDismiss(alert.id);
        return;
      }
      rafId = requestAnimationFrame(tick);
    }

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [alert.id, alert.createdAt, onDismiss]);

  const { event } = alert;
  const Icon = EVENT_TYPE_ICONS[event.eventType] || AlertTriangle;
  const colours = SEVERITY_COLOURS[event.severity] || SEVERITY_COLOURS.LOW;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, x: 100 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 100 }}
      transition={{ type: 'spring', stiffness: 400, damping: 30 }}
      className={`relative overflow-hidden rounded-lg border backdrop-blur-sm ${colours.bg} ${colours.border}`}
    >
      <div className="flex items-start gap-3 p-3">
        {/* Event type icon */}
        <div className={`shrink-0 mt-0.5 ${colours.text}`}>
          <Icon size={20} />
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5">
            <span className={`text-xs font-semibold uppercase tracking-wide ${colours.text}`}>
              {event.severity}
            </span>
            <span className="text-xs text-text-muted">•</span>
            <span className="text-xs text-text-muted truncate">
              {event.basestationName}
            </span>
          </div>
          <p className="text-sm text-text leading-snug">{event.description}</p>
        </div>

        {/* Close button */}
        <button
          onClick={() => onDismiss(alert.id)}
          className="shrink-0 text-text-muted hover:text-text transition-colors cursor-pointer"
          aria-label="Dismiss event alert"
        >
          <X size={14} />
        </button>
      </div>

      {/* Progress bar countdown */}
      <div className="h-0.5 w-full bg-white/5">
        <motion.div
          className={`h-full ${colours.progress}`}
          style={{ width: `${progress}%` }}
          transition={{ duration: 0 }}
        />
      </div>
    </motion.div>
  );
}

// --- EventAlertContainer: manages multiple stacked alerts ---

export function EventAlertContainer({
  alerts,
  onDismiss,
}: {
  alerts: EventAlertData[];
  onDismiss: (id: string) => void;
}) {
  return (
    <div
      className="fixed top-4 right-4 z-[60] flex flex-col gap-2 w-80 pointer-events-none"
      aria-live="polite"
      aria-atomic="false"
      role="log"
      aria-label="Event notifications"
    >
      <AnimatePresence mode="popLayout">
        {alerts.map((alert) => (
          <div key={alert.id} className="pointer-events-auto">
            <EventAlertItem alert={alert} onDismiss={onDismiss} />
          </div>
        ))}
      </AnimatePresence>
    </div>
  );
}

// --- Hook to manage event alerts state ---

export function useEventAlerts() {
  const [alerts, setAlerts] = useState<EventAlertData[]>([]);

  const addEventAlert = useCallback((event: GameEvent) => {
    const id = crypto.randomUUID();
    setAlerts((prev) => [...prev, { id, event, createdAt: Date.now() }]);
  }, []);

  const dismissEventAlert = useCallback((id: string) => {
    setAlerts((prev) => prev.filter((a) => a.id !== id));
  }, []);

  return { alerts, addEventAlert, dismissEventAlert };
}
