import { AlertTriangle, Sliders, Power, RotateCcw, Rocket } from 'lucide-react';
import { MetricBar, type MetricKey } from './MetricBar';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';

interface DeployedRapp {
  id: number;
  templateId: number;
  name: string;
  status: string;
  version: number;
  deployedAt: string;
  configuration?: {
    threshold?: number;
    aggressiveness?: string;
  };
}

interface ActiveEvent {
  id: number;
  eventType: string;
  severity: string;
  description: string;
  escalationLevel: number;
  createdAt: string;
}

export interface BasestationDetailData {
  id: number;
  name: string;
  metrics: {
    health: number;
    customerExperience: number;
    cost: number;
    energyEfficiency: number;
    automationReliability: number;
    slaCompliance: number;
  };
  deployedRapps: DeployedRapp[];
  activeEvents: ActiveEvent[];
}

interface BasestationDetailProps {
  basestation: BasestationDetailData;
  onTune?: (rappId: number, rappName: string, threshold?: number, aggressiveness?: string) => void;
  onDisable?: (rappId: number) => void;
  onRollback?: (rappId: number) => void;
  onDeployRapp?: () => void;
}

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

export function BasestationDetail({
  basestation,
  onTune,
  onDisable,
  onRollback,
  onDeployRapp,
}: BasestationDetailProps) {
  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-text">{basestation.name}</h3>
        {onDeployRapp && (
          <Button size="sm" variant="primary" onClick={onDeployRapp}>
            <Rocket size={12} className="mr-1" />
            Deploy rApp
          </Button>
        )}
      </div>

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
                {rapp.status === 'ACTIVE' && (onTune || onDisable || onRollback) && (
                  <div className="flex items-center gap-1.5 mt-2 pt-2 border-t border-surface-lighter">
                    {onTune && (
                      <button
                        onClick={() =>
                          onTune(
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
                    )}
                    {onDisable && (
                      <button
                        onClick={() => onDisable(rapp.id)}
                        className="flex items-center gap-1 px-2 py-1 text-[10px] font-medium text-warning bg-warning/10 rounded hover:bg-warning/20 transition-colors cursor-pointer"
                        title="Disable rApp"
                        aria-label={`Disable ${rapp.name}`}
                      >
                        <Power size={10} aria-hidden="true" />
                        Disable
                      </button>
                    )}
                    {onRollback && rapp.version > 1 && (
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
  );
}
