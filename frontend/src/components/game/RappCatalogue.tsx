import { useEffect, useState, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import {
  Zap,
  Maximize,
  Shield,
  FileCheck,
  Settings,
  GitBranch,
  BellOff,
  Lightbulb,
  AlertOctagon,
  type LucideIcon,
} from 'lucide-react';
import { useGame } from '../../context/GameContext';
import { useDrag } from '../../context/DragContext';
import { apiGet } from '../../services/api';
import { Badge } from '../ui';
import { Button } from '../ui';
import { CatalogueSkeleton } from '../ui';
import { Tooltip } from '../ui';
import { DeploymentPicker } from './DeploymentPicker';

export interface RappTemplate {
  id: number;
  name: string;
  purpose: string;
  cost: number;
  benefit: string;
  risk: number;
  confidence: number;
  sideEffects: string;
  impact: {
    health: number;
    customerExperience: number;
    cost: number;
    energyEfficiency: number;
    automationReliability: number;
    slaCompliance: number;
  };
}

interface CatalogueResponse {
  rapps: RappTemplate[];
}

const rappIcons: Record<string, LucideIcon> = {
  'Energy Saver': Zap,
  'Capacity Optimiser': Maximize,
  'Fault Predictor': Shield,
  'SLA Guardian': FileCheck,
  'Configuration Drift Detector': Settings,
  'Traffic Balancer': GitBranch,
  'Alarm Noise Reducer': BellOff,
};

function getRappIcon(name: string): LucideIcon {
  return rappIcons[name] || Settings;
}

const rappHelpfulWhen: Record<string, string[]> = {
  'Energy Saver': [
    'Energy costs are rising or capacity is underused',
    'Low-traffic periods where you can afford slight latency trade-offs',
    'SLA compliance is healthy and you want to improve cost score',
  ],
  'Capacity Optimiser': [
    'Traffic spikes are causing congestion or dropped connections',
    'Customer satisfaction is falling due to overloaded cells',
    'After deploying Energy Saver to offset its latency side-effects',
  ],
  'Fault Predictor': [
    'Hardware failure events are appearing on basestations',
    'Automation reliability is declining unexpectedly',
    'You want early warning before incidents escalate to CRITICAL',
  ],
  'SLA Guardian': [
    'SLA compliance is below 80% or showing a downward trend',
    'Security breach or high-severity events are active',
    'You need to protect customer experience during busy periods',
  ],
  'Configuration Drift Detector': [
    'Automation reliability metrics are drifting without clear cause',
    'After rolling back another rApp that may have left stale config',
    'Multiple rApps are deployed and you suspect conflicts',
  ],
  'Traffic Balancer': [
    'Network congestion events are active across multiple cells',
    'One basestation is overloaded while others are idle',
    'Weather or traffic-spike incidents are degrading handovers',
  ],
  'Alarm Noise Reducer': [
    'The incident feed is flooded with LOW or MEDIUM severity alerts',
    'You need to focus on CRITICAL events without distraction',
    'Automation reliability is suffering from alert fatigue',
  ],
};

export function RappTooltipContent({ rapp }: { rapp: RappTemplate }) {
  const situations = rappHelpfulWhen[rapp.name] ?? [];
  return (
    <div className="p-3 space-y-2.5">
      <div>
        <p className="text-[10px] font-bold uppercase tracking-wider text-text-muted mb-1">Purpose</p>
        <p className="text-xs text-text leading-snug">{rapp.purpose}</p>
      </div>
      {rapp.sideEffects && (
        <div className="flex items-start gap-1.5 px-2 py-1.5 rounded bg-danger/10 border border-danger/20">
          <AlertOctagon size={11} className="shrink-0 mt-0.5 text-danger/70" />
          <p className="text-[11px] text-danger/80 leading-snug">{rapp.sideEffects}</p>
        </div>
      )}
      {situations.length > 0 && (
        <div>
          <p className="text-[10px] font-bold uppercase tracking-wider text-text-muted mb-1.5">
            Helpful when…
          </p>
          <ul className="space-y-1">
            {situations.map((s) => (
              <li key={s} className="flex items-start gap-1.5">
                <Lightbulb size={11} className="shrink-0 mt-0.5 text-amber-400/80" />
                <span className="text-[11px] text-text-muted leading-snug">{s}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function RiskIndicator({ value, label }: { value: number; label: string }) {
  const colour =
    value <= 20 ? 'bg-accent' : value <= 50 ? 'bg-warning' : 'bg-danger';
  return (
    <div className="flex items-center gap-1">
      <span className="text-[10px] text-text-muted">{label}</span>
      <div className="w-8 h-1.5 rounded-full bg-surface-lighter overflow-hidden">
        <div
          className={`h-full rounded-full ${colour}`}
          style={{ width: `${Math.min(100, value)}%` }}
        />
      </div>
    </div>
  );
}

/** Portaled dropdown for selecting a basestation to deploy to */
function RappPickerDropdown({ rapp, basestations, onConfirmDeploy, onClose, anchorEl }: {
  rapp: RappTemplate;
  basestations: Array<{ id: number; name: string }>;
  onConfirmDeploy: (templateId: number, basestationId: number) => void;
  onClose: () => void;
  anchorEl: HTMLElement | null;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ left: number; top: number }>({ left: 0, top: 0 });

  // Position above the anchor element
  useEffect(() => {
    if (anchorEl) {
      const rect = anchorEl.getBoundingClientRect();
      setPos({
        left: rect.left + rect.width / 2,
        top: rect.top,
      });
    }
  }, [anchorEl]);

  // Close on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        onClose();
      }
    }
    const timer = setTimeout(() => document.addEventListener('mousedown', handleClick), 0);
    return () => { clearTimeout(timer); document.removeEventListener('mousedown', handleClick); };
  }, [onClose]);

  return (
    <div
      ref={ref}
      className="fixed z-[9999] w-48 rounded-lg bg-surface border border-surface-lighter shadow-2xl p-2 space-y-0.5"
      style={{ left: pos.left, bottom: window.innerHeight - pos.top + 8, transform: 'translateX(-50%)' }}
    >
      <p className="text-[10px] font-bold uppercase tracking-wider text-text-muted px-2 py-1">
        Deploy {rapp.name} to:
      </p>
      {basestations.map((bs) => (
        <button
          key={bs.id}
          onClick={() => {
            onConfirmDeploy(rapp.id, bs.id);
            onClose();
          }}
          className="w-full text-left px-2 py-1.5 text-xs text-text rounded hover:bg-primary/10 hover:text-primary transition-colors cursor-pointer"
        >
          {bs.name}
        </button>
      ))}
    </div>
  );
}

interface RappCatalogueProps {
  onDeploy?: (rapp: RappTemplate) => void;
  basestations?: Array<{ id: number; name: string }>;
  onConfirmDeploy?: (templateId: number, basestationId: number) => void;
}

export function RappCatalogue({ onDeploy, basestations: basestationsProp, onConfirmDeploy }: RappCatalogueProps) {
  const { token } = useGame();
  const { dragState, startDrag, endDrag } = useDrag();
  const [rapps, setRapps] = useState<RappTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pickerOpenForId, setPickerOpenForId] = useState<number | null>(null);

  const fetchCatalogue = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const data = await apiGet<CatalogueResponse>('/api/rapps/catalogue', token);
      setRapps(data.rapps);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to load catalogue';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    fetchCatalogue();
  }, [fetchCatalogue]);

  // Close picker when a drag starts
  useEffect(() => {
    if (dragState) {
      setPickerOpenForId(null);
    }
  }, [dragState]);

  const handleDragStart = useCallback(
    (e: React.DragEvent<HTMLDivElement>, rapp: RappTemplate) => {
      e.dataTransfer.setData('text/plain', String(rapp.id));
      e.dataTransfer.effectAllowed = 'move';
      // Hide the native drag ghost image — we use a custom DragPreview instead
      const emptyImg = new Image();
      emptyImg.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
      e.dataTransfer.setDragImage(emptyImg, 0, 0);
      startDrag({ templateId: rapp.id, name: rapp.name, icon: rapp.name });
      // Close picker when dragging starts
      setPickerOpenForId(null);
    },
    [startDrag]
  );

  const handleDragEnd = useCallback(() => {
    endDrag();
  }, [endDrag]);

  if (loading) {
    return <CatalogueSkeleton />;
  }

  if (error) {
    return (
      <div className="text-center py-4">
        <p className="text-sm text-danger mb-2">{error}</p>
        <Button size="sm" variant="secondary" onClick={fetchCatalogue}>
          Retry
        </Button>
      </div>
    );
  }

  return (
    <div className="flex gap-3 pb-1">
      {rapps.map((rapp) => {
        const Icon = getRappIcon(rapp.name);
        const isDragging = dragState?.templateId === rapp.id;
        const isExpanded = pickerOpenForId === rapp.id;
        return (
          <div key={rapp.id} className="flex-1 min-w-0 relative">
            <Tooltip
              content={<RappTooltipContent rapp={rapp} />}
              side="top"
              className="block"
              disabled={!!dragState}
            >
              <div
                draggable
                tabIndex={0}
                role="button"
                data-rapp-id={rapp.id}
                aria-label={`Deploy ${rapp.name} - €${rapp.cost}`}
                onDragStart={(e) => handleDragStart(e, rapp)}
                onDragEnd={handleDragEnd}
                onClick={() => {
                  if (basestationsProp && onConfirmDeploy) {
                    setPickerOpenForId(isExpanded ? null : rapp.id);
                  } else {
                    onDeploy?.(rapp);
                  }
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    if (basestationsProp && onConfirmDeploy) {
                      setPickerOpenForId(isExpanded ? null : rapp.id);
                    } else {
                      onDeploy?.(rapp);
                    }
                  }
                }}
                className={`relative p-3 rounded-lg bg-surface-light border border-surface-lighter hover:border-primary/30 focus:border-primary/50 focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors cursor-grab ${isDragging ? 'opacity-50' : ''} ${isExpanded ? 'border-primary/50 ring-1 ring-primary/30' : ''}`}
              >
                <div className="flex items-center gap-2 mb-1">
                  <div className="shrink-0 w-7 h-7 rounded-md bg-primary/10 flex items-center justify-center">
                    <Icon size={14} className="text-primary" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <span className="text-xs font-semibold text-text truncate block">
                      {rapp.name}
                    </span>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <Badge variant="warning">€{rapp.cost}</Badge>
                  <div className="flex items-center gap-2">
                    <RiskIndicator value={rapp.risk} label="Risk" />
                  </div>
                </div>
              </div>
            </Tooltip>
            {/* Expanded basestation picker — portaled to body to avoid clipping */}
            {isExpanded && basestationsProp && onConfirmDeploy &&
              createPortal(
                <RappPickerDropdown
                  rapp={rapp}
                  basestations={basestationsProp}
                  onConfirmDeploy={onConfirmDeploy}
                  onClose={() => setPickerOpenForId(null)}
                  anchorEl={document.querySelector(`[data-rapp-id="${rapp.id}"]`)}
                />,
                document.body,
              )
            }
          </div>
        );
      })}
    </div>
  );
}
