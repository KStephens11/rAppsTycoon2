import { useEffect, useRef, useState, useCallback } from 'react';
import { AlertTriangle, Sliders, Power, RotateCcw, X } from 'lucide-react';
import { MetricBar, type MetricKey } from './MetricBar';
import { Badge } from '../ui/Badge';
import type { BasestationDetailData } from './BasestationDetail';

export interface BasestationPopoverProps {
  basestation: BasestationDetailData;
  anchorPosition: { x: number; y: number }; // screen coords from R3F projection
  onClose: () => void;
  onTune: (rappId: number, rappName: string, threshold?: number, aggressiveness?: string) => void;
  onDisable: (rappId: number) => void;
  onRollback: (rappId: number) => void;
}

const RIGHT_PANEL_WIDTH = 288; // w-72 = 18rem = 288px
const POPOVER_WIDTH = 320;
const POPOVER_OFFSET_X = 60;
const POPOVER_OFFSET_Y = -40;

const metricKeys: MetricKey[] = [
  'health',
  'customerExperience',
  'energyEfficiency',
  'automationReliability',
  'slaCompliance',
  'cost',
];

function getRappStatusBadge(status: string) {
  switch (status) {
    case 'DEPLOYING':
      return <Badge variant="warning">Deploying</Badge>;
    case 'ACTIVE':
      return <Badge variant="success">Active</Badge>;
    case 'DISABLED':
      return <Badge className="bg-surface-lighter text-text-muted">Disabled</Badge>;
    default:
      return <Badge>{status}</Badge>;
  }
}

function getSeverityColour(severity: string): string {
  switch (severity) {
    case 'LOW':
      return 'text-blue-400';
    case 'MEDIUM':
      return 'text-amber-400';
    case 'HIGH':
      return 'text-orange-400';
    case 'CRITICAL':
      return 'text-red-400';
    default:
      return 'text-text-muted';
  }
}

function getSeverityBadgeVariant(severity: string): 'info' | 'warning' | 'danger' {
  switch (severity) {
    case 'LOW':
      return 'info';
    case 'MEDIUM':
    case 'HIGH':
      return 'warning';
    case 'CRITICAL':
      return 'danger';
    default:
      return 'info';
  }
}

function EscalationDots({ level, max = 4 }: { level: number; max?: number }) {
  return (
    <span className="inline-flex gap-0.5 text-xs">
      {Array.from({ length: max }, (_, i) => (
        <span
          key={i}
          className={i < level ? 'text-warning' : 'text-surface-lighter'}
        >
          ●
        </span>
      ))}
    </span>
  );
}

/**
 * Computes the popover position constrained within the map container.
 * Ensures the popover does not overflow into the right panel area.
 */
function computePosition(
  anchor: { x: number; y: number },
  containerWidth: number,
  containerHeight: number,
): { left: number; top: number } {
  // Position to the right of the anchor by default
  let left = anchor.x + POPOVER_OFFSET_X;
  let top = anchor.y + POPOVER_OFFSET_Y;

  // Constrain: don't overflow into the right panel area
  const maxLeft = containerWidth - RIGHT_PANEL_WIDTH - POPOVER_WIDTH - 8;
  if (left > maxLeft) {
    // Flip to the left of the anchor
    left = anchor.x - POPOVER_WIDTH - POPOVER_OFFSET_X;
  }

  // Clamp left to at least 8px from the edge
  left = Math.max(8, left);

  // Clamp top to stay within container
  top = Math.max(8, top);
  // We don't know exact popover height, but cap at a reasonable max
  const estimatedMaxHeight = 500;
  if (top + estimatedMaxHeight > containerHeight) {
    top = Math.max(8, containerHeight - estimatedMaxHeight - 8);
  }

  return { left, top };
}

export function BasestationPopover({
  basestation,
  anchorPosition,
  onClose,
  onTune,
  onDisable,
  onRollback,
}: BasestationPopoverProps) {
  const popoverRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<{ left: number; top: number }>({ left: 0, top: 0 });

  // Recalculate position when anchor changes (throttled via anchorPosition prop updates)
  useEffect(() => {
    const container = popoverRef.current?.parentElement;
    if (!container) return;

    const containerRect = container.getBoundingClientRect();
    const pos = computePosition(
      anchorPosition,
      containerRect.width,
      containerRect.height,
    );
    setPosition(pos);
  }, [anchorPosition]);

  // Close on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (popoverRef.current && !popoverRef.current.contains(event.target as Node)) {
        onClose();
      }
    }

    // Delay adding the listener to avoid the click that opened the popover from closing it
    const timer = setTimeout(() => {
      document.addEventListener('mousedown', handleClickOutside);
    }, 0);

    return () => {
      clearTimeout(timer);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [onClose]);

  // Close on Escape key
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const handleTune = useCallback(
    (rappId: number, rappName: string, threshold?: number, aggressiveness?: string) => {
      onTune(rappId, rappName, threshold, aggressiveness);
    },
    [onTune],
  );

  return (
    <div
      ref={popoverRef}
      data-testid="basestation-popover"
      className="absolute z-30 bg-surface border border-surface-lighter rounded-lg shadow-xl overflow-hidden"
      style={{
        left: position.left,
        top: position.top,
        width: POPOVER_WIDTH,
        maxHeight: '80%',
      }}
      role="dialog"
      aria-label={`${basestation.name} details`}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-surface-lighter bg-surface-light">
        <h3 className="text-sm font-semibold text-text truncate">{basestation.name}</h3>
        <button
          onClick={onClose}
          className="p-1 rounded hover:bg-surface-lighter transition-colors text-text-muted hover:text-text cursor-pointer"
          aria-label="Close popover"
        >
          <X size={14} />
        </button>
      </div>

      {/* Scrollable content */}
      <div className="overflow-y-auto p-4 space-y-4" style={{ maxHeight: 'calc(80vh - 48px)' }}>
        {/* Metrics */}
        <div className="space-y-0.5">
          {metricKeys.map((key) => (
            <MetricBar key={key} metricKey={key} value={basestation.metrics[key]} />
          ))}
        </div>

        {/* Deployed rApps */}
        <div>
          <h4 className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-2">
            Deployed rApps
          </h4>
          {basestation.deployedRapps.length === 0 ? (
            <p className="text-xs text-text-muted">No rApps deployed</p>
          ) : (
            <div className="space-y-1.5">
              {basestation.deployedRapps.map((rapp) => (
                <div
                  key={rapp.id}
                  className="p-2 rounded bg-surface-light"
                >
                  <div className="flex items-center gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-medium text-text truncate">
                          {rapp.name}
                        </span>
                        <span className="text-xs text-text-muted">v{rapp.version}</span>
                      </div>
                      {rapp.configuration?.aggressiveness && (
                        <span className="text-xs text-text-muted">
                          {rapp.configuration.aggressiveness}
                        </span>
                      )}
                    </div>
                    {getRappStatusBadge(rapp.status)}
                  </div>
                  {/* Management actions for ACTIVE rApps */}
                  {rapp.status === 'ACTIVE' && (
                    <div className="flex items-center gap-1.5 mt-2 pt-2 border-t border-surface-lighter">
                      <button
                        onClick={() =>
                          handleTune(
                            rapp.id,
                            rapp.name,
                            rapp.configuration?.threshold,
                            rapp.configuration?.aggressiveness,
                          )
                        }
                        className="flex items-center gap-1 px-2 py-1 text-[10px] font-medium text-primary bg-primary/10 rounded hover:bg-primary/20 transition-colors cursor-pointer"
                        title="Tune configuration"
                        aria-label={`Tune ${rapp.name} configuration`}
                      >
                        <Sliders size={10} aria-hidden="true" />
                        Tune
                      </button>
                      <button
                        onClick={() => onDisable(rapp.id)}
                        className="flex items-center gap-1 px-2 py-1 text-[10px] font-medium text-warning bg-warning/10 rounded hover:bg-warning/20 transition-colors cursor-pointer"
                        title="Disable rApp"
                        aria-label={`Disable ${rapp.name}`}
                      >
                        <Power size={10} aria-hidden="true" />
                        Disable
                      </button>
                      {rapp.version > 1 && (
                        <button
                          onClick={() => onRollback(rapp.id)}
                          className="flex items-center gap-1 px-2 py-1 text-[10px] font-medium text-text-muted bg-surface-lighter rounded hover:bg-surface-light transition-colors cursor-pointer"
                          title="Rollback to previous version"
                          aria-label={`Rollback ${rapp.name} to previous version`}
                        >
                          <RotateCcw size={10} aria-hidden="true" />
                          Rollback
                        </button>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Active Events */}
        <div>
          <h4 className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-2">
            Active Events
          </h4>
          {basestation.activeEvents.length === 0 ? (
            <p className="text-xs text-text-muted">No active events</p>
          ) : (
            <div className="space-y-1.5">
              {basestation.activeEvents.map((event) => (
                <div
                  key={event.id}
                  className="p-2 rounded bg-surface-light space-y-1"
                >
                  <div className="flex items-center gap-2">
                    <AlertTriangle
                      size={12}
                      className={getSeverityColour(event.severity)}
                    />
                    <span className="text-xs font-medium text-text flex-1 truncate">
                      {event.eventType.replace(/_/g, ' ')}
                    </span>
                    <Badge variant={getSeverityBadgeVariant(event.severity)}>
                      {event.severity}
                    </Badge>
                  </div>
                  <div className="flex items-center gap-2">
                    <EscalationDots level={event.escalationLevel} />
                    <span className="text-xs text-text-muted truncate">
                      {event.description}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
