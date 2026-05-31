import { useEffect, useState, useCallback } from 'react';
import {
  Zap,
  Maximize,
  Shield,
  FileCheck,
  Settings,
  GitBranch,
  BellOff,
  type LucideIcon,
} from 'lucide-react';
import { useGame } from '../../context/GameContext';
import { apiGet } from '../../services/api';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { CatalogueSkeleton } from '../ui/Skeleton';

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

interface RappCatalogueProps {
  onDeploy: (rapp: RappTemplate) => void;
}

export function RappCatalogue({ onDeploy }: RappCatalogueProps) {
  const { token } = useGame();
  const [rapps, setRapps] = useState<RappTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
    <div className="space-y-2">
      <h4 className="text-xs font-semibold text-text-muted uppercase tracking-wide mb-3">
        rApp Catalogue
      </h4>
      {rapps.map((rapp) => {
        const Icon = getRappIcon(rapp.name);
        return (
          <div
            key={rapp.id}
            tabIndex={0}
            role="button"
            aria-label={`Deploy ${rapp.name} - €${rapp.cost}`}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onDeploy(rapp);
              }
            }}
            className="p-3 rounded-lg bg-surface-light border border-surface-lighter hover:border-primary/30 focus:border-primary/50 focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors"
          >
            <div className="flex items-start gap-2.5">
              <div className="shrink-0 w-8 h-8 rounded-md bg-primary/10 flex items-center justify-center">
                <Icon size={16} className="text-primary" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-0.5">
                  <span className="text-sm font-semibold text-text truncate">
                    {rapp.name}
                  </span>
                  <Badge variant="warning">€{rapp.cost}</Badge>
                </div>
                <p className="text-xs text-text-muted line-clamp-1 mb-1.5">
                  {rapp.benefit}
                </p>
                <div className="flex items-center gap-3">
                  <RiskIndicator value={rapp.risk} label="Risk" />
                  <RiskIndicator value={100 - rapp.confidence} label="Conf" />
                </div>
              </div>
              <Button
                size="sm"
                variant="primary"
                onClick={(e) => {
                  e.stopPropagation();
                  onDeploy(rapp);
                }}
                aria-label={`Deploy ${rapp.name}`}
                className="shrink-0"
              >
                Deploy
              </Button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
