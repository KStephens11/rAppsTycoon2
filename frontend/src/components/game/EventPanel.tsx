import { useMemo, useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Zap, Wifi, HardDrive, Shield, Cloud, AlertTriangle, Lightbulb } from 'lucide-react';

// --- Types ---

export interface ActiveEvent {
  id: number;
  eventType: string;
  severity: string;
  description: string;
  escalationLevel: number;
  createdAt: string;
  basestationName: string;
}

interface EventPanelProps {
  events: ActiveEvent[];
}

// --- Constants ---

const SEVERITY_ORDER: Record<string, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
};

const SEVERITY_COLOURS: Record<string, { bg: string; text: string; badge: string }> = {
  LOW: {
    bg: 'bg-blue-500/10',
    text: 'text-blue-400',
    badge: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
  },
  MEDIUM: {
    bg: 'bg-amber-500/10',
    text: 'text-amber-400',
    badge: 'bg-amber-500/20 text-amber-400 border-amber-500/30',
  },
  HIGH: {
    bg: 'bg-orange-500/10',
    text: 'text-orange-400',
    badge: 'bg-orange-500/20 text-orange-400 border-orange-500/30',
  },
  CRITICAL: {
    bg: 'bg-red-500/10',
    text: 'text-red-400',
    badge: 'bg-red-500/20 text-red-400 border-red-500/30',
  },
};

const EVENT_TYPE_ICONS: Record<string, typeof Zap> = {
  POWER_OUTAGE: Zap,
  NETWORK_CONGESTION: Wifi,
  HARDWARE_FAILURE: HardDrive,
  SECURITY_BREACH: Shield,
  WEATHER_EVENT: Cloud,
};

const EVENT_RAPP_RECOMMENDATIONS: Record<string, string[]> = {
  POWER_OUTAGE: ['Energy Saver', 'Fault Predictor'],
  NETWORK_CONGESTION: ['Traffic Balancer', 'Capacity Optimiser'],
  HARDWARE_FAILURE: ['Fault Predictor', 'Config Drift Detector'],
  SECURITY_BREACH: ['SLA Guardian', 'Capacity Optimiser'],
  WEATHER_EVENT: ['Energy Saver', 'Traffic Balancer'],
};

const MAX_ESCALATION = 3;

// --- Helpers ---

function formatRelativeTime(createdAt: string): string {
  if (!createdAt) return '';
  const created = new Date(createdAt).getTime();
  if (isNaN(created)) return '';
  const now = Date.now();
  const diffMs = now - created;
  const diffSeconds = Math.floor(diffMs / 1000);

  if (diffSeconds < 60) return `${diffSeconds}s ago`;
  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.floor(diffMinutes / 60);
  return `${diffHours}h ago`;
}

// --- Sub-components ---

function EscalationDots({ level }: { level: number }) {
  return (
    <div className="flex items-center gap-1" title={`Escalation level ${level}/${MAX_ESCALATION}`}>
      {Array.from({ length: MAX_ESCALATION }).map((_, i) => (
        <div
          key={i}
          className={`w-2 h-2 rounded-full transition-colors ${
            i < level ? 'bg-orange-400' : 'bg-white/10'
          }`}
        />
      ))}
    </div>
  );
}

function EventCard({ event }: { event: ActiveEvent }) {
  const Icon = EVENT_TYPE_ICONS[event.eventType] || AlertTriangle;
  const colours = SEVERITY_COLOURS[event.severity] || SEVERITY_COLOURS.LOW;
  const [relativeTime, setRelativeTime] = useState(() => formatRelativeTime(event.createdAt));

  // Update relative time every 10 seconds
  useEffect(() => {
    if (!event.createdAt) return;
    const interval = setInterval(() => {
      setRelativeTime(formatRelativeTime(event.createdAt));
    }, 10_000);
    return () => clearInterval(interval);
  }, [event.createdAt]);

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      transition={{ type: 'spring', stiffness: 400, damping: 30 }}
      className={`rounded-lg border border-surface-lighter p-3 ${colours.bg}`}
    >
      <div className="flex items-start gap-3">
        {/* Event type icon */}
        <div className={`shrink-0 mt-0.5 ${colours.text}`}>
          <Icon size={18} />
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          {/* Header row: severity badge + basestation name */}
          <div className="flex items-center gap-2 mb-1">
            <span
              className={`text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded border ${colours.badge}`}
            >
              {event.severity}
            </span>
            <span className="text-xs text-text-muted truncate">
              {event.basestationName}
            </span>
          </div>

          {/* Description */}
          <p className="text-sm text-text leading-snug mb-2">{event.description}</p>

          {/* Recommended rApp hint */}
          {EVENT_RAPP_RECOMMENDATIONS[event.eventType] && (
            <div className="flex items-start gap-1.5 mb-2 px-2 py-1.5 rounded border border-dashed border-white/10 bg-white/[0.03]">
              <Lightbulb size={13} className="shrink-0 mt-0.5 text-amber-400/70" />
              <span className="text-[11px] text-text-muted leading-snug">
                Deploy {EVENT_RAPP_RECOMMENDATIONS[event.eventType].join(' or ')}
              </span>
            </div>
          )}

          {/* Footer: escalation dots + time */}
          <div className="flex items-center justify-between">
            <EscalationDots level={event.escalationLevel} />
            {relativeTime && (
              <span className="text-[11px] text-text-muted">{relativeTime}</span>
            )}
          </div>
        </div>
      </div>
    </motion.div>
  );
}

// --- Main Component ---

// NOTE: If the event list grows significantly (50+ items), consider adding
// list virtualisation (e.g., @tanstack/react-virtual) for better scroll performance.

export function EventPanel({ events }: EventPanelProps) {
  const sortedEvents = useMemo(() => {
    return [...events].sort((a, b) => {
      const aOrder = SEVERITY_ORDER[a.severity] ?? 4;
      const bOrder = SEVERITY_ORDER[b.severity] ?? 4;
      return aOrder - bOrder;
    });
  }, [events]);

  if (sortedEvents.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-text-muted">
        <AlertTriangle size={32} className="mb-3 opacity-40" />
        <p className="text-sm">No active events</p>
        <p className="text-xs mt-1 opacity-60">Events will appear here when they occur</p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-text">
          Active Events
        </h3>
        <span className="text-xs text-text-muted">
          {sortedEvents.length} event{sortedEvents.length !== 1 ? 's' : ''}
        </span>
      </div>

      <AnimatePresence mode="popLayout">
        {sortedEvents.map((event) => (
          <EventCard key={event.id} event={event} />
        ))}
      </AnimatePresence>
    </div>
  );
}
