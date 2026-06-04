import { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { MascotByte } from '../ui';
import { apiGet } from '../../services/api';
import { X, Lightbulb, RefreshCw } from 'lucide-react';

interface Recommendation {
  action: string;
  rappTemplateId: number | null;
  deploymentId: number | null;
  basestationId: number | null;
  confidence: number;
  reasoning: string;
}

interface RecommendationResponse {
  recommendation: Recommendation | null;
}

export interface ActiveEventInfo {
  eventType: string;
  severity: string;
  basestationName: string;
}

interface RecommendationBubbleProps {
  sessionCode: string;
  token: string;
  activeEvents?: ActiveEventInfo[];
}

/** Maps event types to recommended rApp names (mirrors backend EVENT_RAPP_EFFECTIVENESS) */
const EVENT_RAPP_RECOMMENDATIONS: Record<string, string[]> = {
  POWER_OUTAGE: ['Energy Saver', 'Fault Predictor'],
  TRAFFIC_SPIKE: ['Capacity Optimiser', 'Traffic Balancer'],
  HARDWARE_FAILURE: ['Fault Predictor', 'Configuration Drift Detector'],
  SLA_BREACH: ['SLA Guardian', 'Capacity Optimiser'],
  INTERFERENCE: ['Traffic Balancer', 'Alarm Noise Reducer'],
  CAPACITY_OVERFLOW: ['Capacity Optimiser', 'Traffic Balancer'],
  NETWORK_CONGESTION: ['Traffic Balancer', 'Capacity Optimiser'],
  SECURITY_BREACH: ['SLA Guardian', 'Capacity Optimiser'],
  WEATHER_EVENT: ['Energy Saver', 'Traffic Balancer'],
};

/** Severity priority for sorting events */
const SEVERITY_PRIORITY: Record<string, number> = {
  CRITICAL: 4,
  HIGH: 3,
  MEDIUM: 2,
  LOW: 1,
};

function generateLocalAdvice(events: ActiveEventInfo[]): string | null {
  if (events.length === 0) return null;

  // Sort by severity (highest first)
  const sorted = [...events].sort(
    (a, b) => (SEVERITY_PRIORITY[b.severity] ?? 0) - (SEVERITY_PRIORITY[a.severity] ?? 0),
  );

  const top = sorted[0];
  const rapps = EVENT_RAPP_RECOMMENDATIONS[top.eventType];
  if (!rapps) {
    return `${top.basestationName} has a ${top.severity.toLowerCase()} ${top.eventType.replace(/_/g, ' ').toLowerCase()}. Try deploying an rApp that targets this issue!`;
  }

  return `${top.basestationName} has a ${top.severity.toLowerCase()} ${top.eventType.replace(/_/g, ' ').toLowerCase()}. Try deploying ${rapps.join(' or ')} to fix it!`;
}

export function RecommendationBubble({ sessionCode, token, activeEvents = [] }: RecommendationBubbleProps) {
  const [recommendation, setRecommendation] = useState<Recommendation | null>(null);
  const [loading, setLoading] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [apiUnavailable, setApiUnavailable] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchRecommendation = useCallback(async () => {
    if (!sessionCode || !token) return;
    setLoading(true);
    try {
      const data = await apiGet<RecommendationResponse>(
        `/api/sessions/${sessionCode}/recommendations`,
        token,
      );
      setRecommendation(data.recommendation);
      setApiUnavailable(data.recommendation === null);
    } catch {
      setApiUnavailable(true);
      setRecommendation(null);
    } finally {
      setLoading(false);
    }
  }, [sessionCode, token]);

  // Fetch on mount and every 15 seconds
  useEffect(() => {
    fetchRecommendation();
    intervalRef.current = setInterval(fetchRecommendation, 15000);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [fetchRecommendation]);

  // Local fallback advice when API returns null
  const localAdvice = useMemo(
    () => (apiUnavailable || !recommendation) ? generateLocalAdvice(activeEvents) : null,
    [apiUnavailable, recommendation, activeEvents],
  );

  // Determine what text to show
  const displayText = recommendation?.reasoning ?? localAdvice;
  const hasAdvice = !!displayText;

  // If dismissed, show a small icon to reopen
  if (dismissed) {
    return (
      <button
        onClick={() => {
          setDismissed(false);
          fetchRecommendation();
        }}
        className="absolute top-12 right-2 z-10 bg-surface/90 backdrop-blur-sm border border-primary/30 rounded-full p-2 shadow-lg hover:border-primary/60 transition-colors cursor-pointer"
        title="Show Byte's advice"
        aria-label="Show recommendation"
      >
        <Lightbulb size={16} className="text-primary" />
      </button>
    );
  }

  return (
    <div className="absolute top-12 right-2 z-10 flex items-start gap-2 max-w-[280px]">
      {/* Mascot */}
      <div className="shrink-0 mt-1">
        <MascotByte mood={loading ? 'thinking' : hasAdvice ? 'excited' : 'happy'} size={44} />
      </div>

      {/* Speech bubble */}
      <div className="relative bg-surface/90 backdrop-blur-sm border border-surface-lighter rounded-lg shadow-lg px-3 py-2 text-xs flex-1">
        {/* Close button */}
        <button
          onClick={() => setDismissed(true)}
          className="absolute top-1 right-1 text-text-muted hover:text-text transition-colors cursor-pointer"
          aria-label="Dismiss recommendation"
        >
          <X size={12} />
        </button>

        {/* Refresh button */}
        <button
          onClick={fetchRecommendation}
          disabled={loading}
          className="absolute top-1 right-5 text-text-muted hover:text-primary transition-colors cursor-pointer disabled:opacity-50"
          aria-label="Refresh recommendation"
          title="Get new advice"
        >
          <RefreshCw size={11} className={loading ? 'animate-spin' : ''} />
        </button>

        {/* Content */}
        {loading && !hasAdvice && (
          <p className="text-text-muted italic pr-8">Thinking...</p>
        )}

        {hasAdvice && (
          <div className="pr-8">
            <p className="text-text leading-relaxed">{displayText}</p>
            {recommendation?.confidence != null && recommendation.confidence > 0 && (
              <div className="mt-1 flex items-center gap-1">
                <span className="text-text-muted">Confidence:</span>
                <span className="text-primary font-medium">
                  {Math.round(recommendation.confidence * 100)}%
                </span>
              </div>
            )}
          </div>
        )}

        {!loading && !hasAdvice && (
          <p className="text-text-muted italic pr-8">Everything looks good! No action needed.</p>
        )}
      </div>
    </div>
  );
}
