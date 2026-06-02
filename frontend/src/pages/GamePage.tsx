import { useEffect, useState, useCallback, useMemo, useRef, lazy, Suspense, memo } from 'react';
import { Radio, User, TrendingUp, TrendingDown, AlertTriangle, Settings, MoreHorizontal } from 'lucide-react';
import { useGame } from '../context/GameContext';
import { useWebSocket } from '../hooks/useWebSocket';
import { useGameState, type GameEvent } from '../hooks/useGameState';
import { useGameSubscriptions } from '../hooks/useGameSubscriptions';
import { useSoundEffects } from '../hooks/useSoundEffects';
import { RappCatalogue, type RappTemplate } from '../components/game/RappCatalogue';
import { CatalogueStrip } from '../components/game/CatalogueStrip';
import { TuneModal } from '../components/game/TuneModal';
import { ToastContainer, type ToastMessage } from '../components/ui/Toast';
import { EventAlertContainer, useEventAlerts } from '../components/game/EventAlert';
import { EventPanel, type ActiveEvent } from '../components/game/EventPanel';
import { Leaderboard } from '../components/game/Leaderboard';
import { SettingsToolbar } from '../components/ui/SettingsToolbar';
import { BottomSheet } from '../components/ui/BottomSheet';
import { ExpandableSection } from '../components/ui/ExpandableSection';
import { apiGet, apiPost, apiPut } from '../services/api';
import { BasestationsSkeleton, LeaderboardSkeleton } from '../components/ui/Skeleton';
import { DragProvider, useDrag } from '../context/DragContext';
import { DragPreview } from '../components/game/DragPreview';
import { BasestationPopover } from '../components/game/BasestationPopover';

const IsometricMap = lazy(() => import('../components/game/IsometricMap'));

const MemoizedEventPanel = memo(EventPanel);
const MemoizedLeaderboard = memo(Leaderboard);

interface BasestationApiData {
  id: number;
  name: string;
  positionX: number;
  positionY: number;
  metrics: {
    health: number;
    customerExperience: number;
    cost: number;
    energyEfficiency: number;
    automationReliability: number;
    slaCompliance: number;
  };
  deployedRapps: Array<{
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
  }>;
  activeEvents: Array<{
    id: number;
    eventType: string;
    severity: string;
    description: string;
    escalationLevel: number;
    createdAt: string;
  }>;
}

interface BasestationsResponse {
  basestations: BasestationApiData[];
}

interface CatalogueResponse {
  rapps: RappTemplate[];
}

// --- Inline sub-components for the new layout ---

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-1.5">
      <span className={`w-2.5 h-2.5 rounded-full ${color}`} />
      <span className="text-[11px] text-text-muted">{label}</span>
    </div>
  );
}

interface MetricCardProps {
  index: number;
  label: string;
  value: number;
  isCost?: boolean;
}

function getMetricStatus(value: number, isCost: boolean): { label: string; color: string; arrow: 'up' | 'down' } {
  if (isCost) {
    if (value > 70) return { label: 'Red/High', color: 'text-red-400', arrow: 'up' };
    if (value > 40) return { label: 'Yellow/Mid', color: 'text-amber-400', arrow: 'up' };
    return { label: 'Green/Low', color: 'text-emerald-400', arrow: 'down' };
  }
  if (value >= 70) return { label: 'Green/Up', color: 'text-emerald-400', arrow: 'up' };
  if (value >= 40) return { label: 'Yellow/Mid', color: 'text-amber-400', arrow: 'up' };
  return { label: 'Red/Down', color: 'text-red-400', arrow: 'down' };
}

function MetricCard({ index, label, value, isCost = false }: MetricCardProps) {
  const status = getMetricStatus(value, isCost);
  const Arrow = status.arrow === 'up' ? TrendingUp : TrendingDown;
  const displayValue = isCost ? `€${value.toFixed(1)}` : `${value.toFixed(0)}%`;

  return (
    <div className="flex-shrink-0 flex flex-col gap-1 px-3 py-2 rounded-lg bg-surface-light border border-surface-lighter min-w-[100px]">
      <div className="flex items-center gap-1">
        <span className="text-[10px] text-text-muted font-medium">{index}.</span>
        <span className="text-[10px] text-text-muted truncate">{label}</span>
      </div>
      <div className="flex items-baseline gap-1.5">
        <span className="text-base font-bold text-text tabular-nums leading-none">{displayValue}</span>
        <Arrow size={12} className={status.color} />
      </div>
      <span className={`text-[10px] font-medium ${status.color}`}>{status.label}</span>
    </div>
  );
}

// ---

export function GamePage() {
  return (
    <DragProvider>
      <GamePageInner />
    </DragProvider>
  );
}

function GamePageInner() {
  const { gameState, sessionCode, token, playerId, players } = useGame();
  const ws = useWebSocket();
  const realTimeState = useGameState();
  const { playDeploy, playEventAlert, playGameEnd } = useSoundEffects();
  const { dragState, endDrag } = useDrag();
  const [selectedBasestationId, setSelectedBasestationId] = useState<number | null>(null);
  const [basestations, setBasestations] = useState<BasestationApiData[]>([]);
  const [basestationsLoading, setBasestationsLoading] = useState(true);
  const [isDeploying, setIsDeploying] = useState(false);
  const [popoverAnchor, setPopoverAnchor] = useState<{ x: number; y: number } | null>(null);
  const [rapps, setRapps] = useState<RappTemplate[]>([]);

  const [tuneModalOpen, setTuneModalOpen] = useState(false);
  const [tuneTarget, setTuneTarget] = useState<{
    id: number;
    name: string;
    threshold?: number;
    aggressiveness?: string;
  } | null>(null);

  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const [resolvedBasestationIds, setResolvedBasestationIds] = useState<Set<number>>(new Set());
  const previousEventIdsRef = useRef<Map<number, Set<number>>>(new Map());

  const addToast = useCallback((message: string, type: 'error' | 'success' | 'info' = 'success') => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, message, type }]);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const { alerts: eventAlerts, addEventAlert, dismissEventAlert } = useEventAlerts();

  const handleEventReceived = useCallback((event: GameEvent) => {
    addEventAlert(event);
    playEventAlert();
  }, [addEventAlert, playEventAlert]);

  const subscriptionCallbacks = useMemo(() => ({
    onEventReceived: handleEventReceived,
  }), [handleEventReceived]);

  useGameSubscriptions(ws, realTimeState, subscriptionCallbacks);

  useEffect(() => {
    if (gameState === 'active') {
      ws.connect();
    }
    return () => { ws.disconnect(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gameState]);

  // When WebSocket connects/reconnects, refetch basestations to catch missed events
  const wsConnected = ws.connected;

  useEffect(() => {
    if (gameState === 'completed') playGameEnd();
  }, [gameState, playGameEnd]);

  const fetchBasestations = useCallback(() => {
    if (!sessionCode || !token) return;
    apiGet<BasestationsResponse>(`/api/sessions/${sessionCode}/basestations`, token)
      .then((data) => {
        const newResolvedBsIds: number[] = [];
        const currentEventMap = new Map<number, Set<number>>();
        for (const bs of data.basestations) {
          const currentEventIds = new Set(bs.activeEvents.map((e) => e.id));
          currentEventMap.set(bs.id, currentEventIds);
          const previousIds = previousEventIdsRef.current.get(bs.id);
          if (previousIds && previousIds.size > 0) {
            for (const prevId of previousIds) {
              if (!currentEventIds.has(prevId)) { newResolvedBsIds.push(bs.id); break; }
            }
          }
        }
        previousEventIdsRef.current = currentEventMap;
        if (newResolvedBsIds.length > 0) {
          setResolvedBasestationIds(new Set(newResolvedBsIds));
          setTimeout(() => setResolvedBasestationIds(new Set()), 2000);
        }
        setBasestations(data.basestations);
        setBasestationsLoading(false);
      })
      .catch((err) => {
        console.error('Failed to load basestations:', err);
        setBasestationsLoading(false);
      });
  }, [sessionCode, token]);

  useEffect(() => { fetchBasestations(); }, [fetchBasestations]);

  // Refetch when WebSocket connects/reconnects to catch missed events
  useEffect(() => {
    if (wsConnected) {
      fetchBasestations();
    }
  }, [wsConnected, fetchBasestations]);

  // Periodic basestations poll — safety net for missed WebSocket events
  useEffect(() => {
    if (gameState !== 'active') return;
    const interval = setInterval(() => {
      fetchBasestations();
    }, 10000); // every 10 seconds
    return () => clearInterval(interval);
  }, [gameState, fetchBasestations]);

  const fetchCatalogue = useCallback(() => {
    if (!token) return;
    apiGet<CatalogueResponse>('/api/rapps/catalogue', token)
      .then((data) => setRapps(data.rapps))
      .catch((err) => console.error('Failed to load catalogue:', err));
  }, [token]);

  useEffect(() => { fetchCatalogue(); }, [fetchCatalogue]);

  const handleSelectBasestation = useCallback((id: number | null) => {
    setSelectedBasestationId(id);
  }, []);

  const handleScreenPositionUpdate = useCallback((position: { x: number; y: number } | null) => {
    setPopoverAnchor(position);
  }, []);

  const selectedBasestation = useMemo(() => {
    if (!selectedBasestationId) return null;
    return basestations.find((bs) => bs.id === selectedBasestationId) ?? null;
  }, [selectedBasestationId, basestations]);

  const handleConfirmDeploy = useCallback(async (templateId: number, basestationId: number) => {
    if (!sessionCode || !token) return;
    await apiPost(`/api/sessions/${sessionCode}/rapps/deploy`, { templateId, basestationId }, token);
    addToast('rApp deployed successfully!', 'success');
    playDeploy();
    fetchBasestations();
  }, [sessionCode, token, addToast, fetchBasestations, playDeploy]);

  const handleDrop = useCallback(async (basestationId: number) => {
    if (!dragState || !sessionCode || !token || isDeploying) return;
    const { templateId } = dragState;
    setIsDeploying(true);
    try {
      await apiPost(`/api/sessions/${sessionCode}/rapps/deploy`, { templateId, basestationId }, token);
      addToast('rApp deployed successfully!', 'success');
      playDeploy();
      fetchBasestations();
    } catch (err: unknown) {
      addToast(err instanceof Error ? err.message : 'Failed to deploy rApp', 'error');
    } finally {
      endDrag();
      setIsDeploying(false);
    }
  }, [dragState, sessionCode, token, isDeploying, addToast, playDeploy, fetchBasestations, endDrag]);

  const handleTune = useCallback((rappId: number, rappName: string, threshold?: number, aggressiveness?: string) => {
    setTuneTarget({ id: rappId, name: rappName, threshold, aggressiveness });
    setTuneModalOpen(true);
  }, []);

  const handleConfirmTune = useCallback(async (rappId: number, threshold: number, aggressiveness: string) => {
    if (!sessionCode || !token) return;
    await apiPut(`/api/sessions/${sessionCode}/rapps/${rappId}/tune`, { threshold, aggressiveness }, token);
    addToast('rApp tuned successfully!', 'success');
    fetchBasestations();
  }, [sessionCode, token, addToast, fetchBasestations]);

  const handleDisable = useCallback(async (rappId: number) => {
    if (!sessionCode || !token) return;
    try {
      await apiPut(`/api/sessions/${sessionCode}/rapps/${rappId}/disable`, undefined, token);
      addToast('rApp disabled', 'info');
      fetchBasestations();
    } catch (err: unknown) {
      addToast(err instanceof Error ? err.message : 'Failed to disable rApp', 'error');
    }
  }, [sessionCode, token, addToast, fetchBasestations]);

  const handleRollback = useCallback(async (rappId: number) => {
    if (!sessionCode || !token) return;
    try {
      await apiPut(`/api/sessions/${sessionCode}/rapps/${rappId}/rollback`, undefined, token);
      addToast('rApp rolled back to previous version', 'success');
      fetchBasestations();
    } catch (err: unknown) {
      addToast(err instanceof Error ? err.message : 'Failed to rollback rApp', 'error');
    }
  }, [sessionCode, token, addToast, fetchBasestations]);

  const mergedBasestations = useMemo(() => basestations.map((bs) => {
    const rtState = realTimeState.basestations.find((rt) => rt.id === bs.id);
    const activeRapps = realTimeState.rappDeployments.filter(
      (r) => r.basestationId === bs.id && r.newStatus === 'ACTIVE',
    );
    const restActiveCount = bs.deployedRapps.filter((r) => r.status === 'ACTIVE').length;
    const restDeploymentIds = new Set(bs.deployedRapps.map((r) => r.id));
    const newRtDeployments = activeRapps.filter((r) => !restDeploymentIds.has(r.deploymentId));
    const totalActiveRapps = restActiveCount + newRtDeployments.length;

    const severityOrder: Record<string, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
    const allSeverities = [
      ...bs.activeEvents.map((e) => e.severity),
      ...realTimeState.events.filter((e) => e.basestationId === bs.id).map((e) => e.severity),
    ];
    const highestSeverity = allSeverities.length > 0
      ? allSeverities.reduce((highest, current) =>
          (severityOrder[current] || 0) > (severityOrder[highest] || 0) ? current : highest,
        ) as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
      : undefined;

    const highestEscalation = bs.activeEvents.length > 0
      ? Math.max(...bs.activeEvents.map((e) => e.escalationLevel))
      : 0;

    return {
      ...bs,
      metrics: rtState ? rtState.metrics : bs.metrics,
      activeRappsCount: totalActiveRapps,
      hasEvent: bs.activeEvents.length > 0 || realTimeState.events.some((e) => e.basestationId === bs.id),
      highestSeverity,
      highestEscalation,
      showResolution: resolvedBasestationIds.has(bs.id),
    };
  }), [basestations, realTimeState.basestations, realTimeState.rappDeployments, realTimeState.events, resolvedBasestationIds]);

  const combinedActiveEvents: ActiveEvent[] = useMemo(() => {
    const restEvents: ActiveEvent[] = basestations.flatMap((bs) =>
      bs.activeEvents.map((e) => ({
        id: e.id,
        eventType: e.eventType,
        severity: e.severity,
        description: e.description,
        escalationLevel: e.escalationLevel,
        createdAt: e.createdAt,
        basestationName: bs.name,
      })),
    );
    const rtEvents: ActiveEvent[] = realTimeState.events.map((e) => ({
      id: e.eventId,
      eventType: e.eventType,
      severity: e.severity,
      description: e.description,
      escalationLevel: 0,
      createdAt: '',
      basestationName: e.basestationName,
    }));
    const restIds = new Set(restEvents.map((e) => e.id));
    return [...restEvents, ...rtEvents.filter((e) => !restIds.has(e.id))];
  }, [basestations, realTimeState.events]);

  // Metrics for the bottom bar — use selected basestation, fall back to first
  const displayedBasestation = useMemo(
    () => mergedBasestations.find((bs) => bs.id === selectedBasestationId) ?? mergedBasestations[0],
    [mergedBasestations, selectedBasestationId],
  );

  const metricCards = displayedBasestation ? [
    { label: 'Network Health',    value: displayedBasestation.metrics.health,               isCost: false },
    { label: 'Customer Satis.',   value: displayedBasestation.metrics.customerExperience,   isCost: false },
    { label: 'Energy Efficiency', value: displayedBasestation.metrics.energyEfficiency,     isCost: false },
    { label: 'Auto. Reliability', value: displayedBasestation.metrics.automationReliability, isCost: false },
    { label: 'SLA Compliance',    value: displayedBasestation.metrics.slaCompliance,        isCost: false },
    { label: 'Operating Cost',    value: displayedBasestation.metrics.cost,                 isCost: true  },
  ] : [];

  // Quick-action helpers for bottom bar buttons
  const firstActiveRapp = selectedBasestation?.deployedRapps.find((r) => r.status === 'ACTIVE');

  const handleQuickRollback = useCallback(() => {
    if (firstActiveRapp) handleRollback(firstActiveRapp.id);
    else addToast('Select a basestation with an active rApp first', 'info');
  }, [firstActiveRapp, handleRollback, addToast]);

  const handleQuickTune = useCallback(() => {
    if (firstActiveRapp) {
      handleTune(
        firstActiveRapp.id,
        firstActiveRapp.name,
        firstActiveRapp.configuration?.threshold,
        firstActiveRapp.configuration?.aggressiveness,
      );
    } else {
      addToast('Select a basestation with an active rApp first', 'info');
    }
  }, [firstActiveRapp, handleTune, addToast]);

  const currentPlayerName = useMemo(
    () => players.find((p) => p.id === playerId)?.displayName ?? 'Player',
    [players, playerId],
  );

  return (
    <div className="flex flex-col h-full relative">
      {/* Overlays (unchanged) */}
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
      <EventAlertContainer alerts={eventAlerts} onDismiss={dismissEventAlert} />
      <DragPreview />

      {/* ── Header bar ── */}
      <header className="hidden md:flex h-12 items-center px-4 gap-4 bg-surface border-b border-surface-lighter shrink-0 z-20">
        {/* Logo */}
        <div className="flex items-center gap-2">
          <Radio size={18} className="text-primary" />
          <span className="text-sm font-bold tracking-wide text-text">
            rApp <span className="text-primary">TYCOON</span>
          </span>
        </div>

        {/* Session code pill */}
        {sessionCode && (
          <span className="text-[11px] font-mono px-2 py-0.5 rounded border border-surface-lighter text-text-muted bg-surface-light">
            {sessionCode}
          </span>
        )}

        <div className="flex-1" />

        {/* Settings + player */}
        <SettingsToolbar />
        <div className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-surface-light border border-surface-lighter">
          <div className="w-5 h-5 rounded-full bg-primary/20 flex items-center justify-center">
            <User size={11} className="text-primary" />
          </div>
          <span className="text-xs font-medium text-text">{currentPlayerName}</span>
        </div>
      </header>

      {/* ── 3-column main area ── */}
      <div className="flex flex-1 min-h-0">

        {/* Center — Map (full width, catalog and overlays float on top) */}
        <div className="flex-1 relative min-w-0 min-h-0">
          {/* Floating rApp Catalog — left side */}
          <div className="absolute top-3 left-3 z-10 w-60 hidden md:flex flex-col bg-surface/90 backdrop-blur-sm border border-surface-lighter/60 rounded-lg shadow-lg overflow-hidden max-h-[calc(100%-1.5rem)]">
            <div className="px-3 py-2 border-b border-surface-lighter/60 shrink-0">
              <h2 className="text-[10px] font-bold uppercase tracking-widest text-text-muted">
                rAPP Catalog
              </h2>
            </div>
            <div className="overflow-y-auto p-3">
              <RappCatalogue
                basestations={basestations.map((bs) => ({ id: bs.id, name: bs.name }))}
                onConfirmDeploy={handleConfirmDeploy}
              />
            </div>
          </div>

          {/* Region label */}
          <div className="absolute top-2 left-1/2 -translate-x-1/2 z-10 pointer-events-none">
            <span className="text-[11px] font-bold uppercase tracking-widest text-text bg-surface/85 backdrop-blur-sm px-3 py-1 rounded border border-surface-lighter shadow">
              {displayedBasestation ? displayedBasestation.name : 'Network Region'}
            </span>
          </div>

          {/* Legend */}
          <div className="absolute top-2 right-2 z-10 flex items-center gap-3 bg-surface/85 backdrop-blur-sm px-3 py-1.5 rounded border border-surface-lighter shadow">
            <LegendDot color="bg-emerald-400" label="Active" />
            <LegendDot color="bg-amber-400" label="Warning" />
            <LegendDot color="bg-red-500" label="Critical" />
          </div>

          <Suspense fallback={<BasestationsSkeleton />}>
            <IsometricMap
              basestations={mergedBasestations}
              selectedBasestationId={selectedBasestationId}
              onSelectBasestation={handleSelectBasestation}
              onDrop={isDeploying ? undefined : handleDrop}
              onScreenPositionUpdate={handleScreenPositionUpdate}
            />
          </Suspense>
          {basestationsLoading && <BasestationsSkeleton />}

          {selectedBasestation && popoverAnchor && (
            <BasestationPopover
              basestation={selectedBasestation}
              anchorPosition={popoverAnchor}
              onClose={() => setSelectedBasestationId(null)}
              onTune={handleTune}
              onDisable={handleDisable}
              onRollback={handleRollback}
            />
          )}
        </div>

        {/* Right panel — Session Scoreboard */}
        <div className="hidden md:flex w-56 bg-surface border-l border-surface-lighter flex-col shrink-0">
          <div className="px-3 py-2 border-b border-surface-lighter">
            <h2 className="text-[10px] font-bold uppercase tracking-widest text-text-muted">
              Session Scoreboard
            </h2>
          </div>
          <div className="flex-1 overflow-y-auto p-3">
            {realTimeState.leaderboard.length === 0
              ? <LeaderboardSkeleton />
              : <MemoizedLeaderboard entries={realTimeState.leaderboard} currentPlayerId={playerId} />
            }
          </div>

        </div>
      </div>

      {/* ── Bottom bar ── */}
      <div className="hidden md:flex h-56 border-t border-surface-lighter shrink-0">

        {/* Left: Network Metrics */}
        <div className="flex-1 flex flex-col bg-surface border-r border-surface-lighter overflow-hidden">
          <div className="px-3 py-2 border-b border-surface-lighter flex items-center justify-between">
            <h3 className="text-[10px] font-bold uppercase tracking-widest text-text-muted">
              Network Metrics
              {displayedBasestation && (
                <span className="ml-2 font-normal normal-case text-text-muted/70">
                  — {displayedBasestation.name}
                </span>
              )}
            </h3>
            <div className="flex items-center gap-1 text-text-muted">
              <Settings size={12} />
              <MoreHorizontal size={12} />
            </div>
          </div>

          <div className="flex-1 flex items-center gap-2 px-3 overflow-x-auto py-2">
            {metricCards.map((m, i) => (
              <MetricCard key={m.label} index={i + 1} label={m.label} value={m.value} isCost={m.isCost} />
            ))}
            {metricCards.length === 0 && (
              <span className="text-xs text-text-muted">Loading metrics…</span>
            )}
          </div>

          <div className="px-3 py-1.5 border-t border-surface-lighter flex items-center gap-2">
            <AlertTriangle size={11} className={combinedActiveEvents.length > 0 ? 'text-warning' : 'text-text-muted'} />
            <span className={`text-[10px] font-medium ${combinedActiveEvents.length > 0 ? 'text-warning' : 'text-text-muted'}`}>
              ALERTS ({combinedActiveEvents.length} Active)
            </span>
          </div>
        </div>

        {/* Right: Live Incident Feed */}
        <div className="w-[420px] flex flex-col bg-surface shrink-0">
          <div className="px-3 py-2 border-b border-surface-lighter">
            <h3 className="text-[10px] font-bold uppercase tracking-widest text-text-muted">
              Live Incident Feed
            </h3>
          </div>
          <div className="flex-1 overflow-y-auto px-2 py-2">
            <MemoizedEventPanel events={combinedActiveEvents} />
          </div>
          {/* Action buttons */}
          <div className="flex gap-1.5 px-2 py-2 border-t border-surface-lighter">
            <button
              onClick={handleQuickRollback}
              className="flex-1 px-2 py-1.5 text-[10px] font-semibold rounded bg-danger/10 text-danger border border-danger/20 hover:bg-danger/20 transition-colors truncate"
            >
              Roll Back App
            </button>
            <button
              onClick={handleQuickTune}
              className="flex-1 px-2 py-1.5 text-[10px] font-semibold rounded bg-primary/10 text-primary border border-primary/20 hover:bg-primary/20 transition-colors truncate"
            >
              Tune Settings
            </button>
            <button
              onClick={() => {}}
              className="flex-1 px-2 py-1.5 text-[10px] font-semibold rounded bg-surface-light text-text-muted border border-surface-lighter hover:text-text transition-colors truncate"
            >
              View Incidents
            </button>
            <button
              disabled
              className="flex-1 px-2 py-1.5 text-[10px] font-semibold rounded bg-surface-light text-text-muted/40 border border-surface-lighter truncate cursor-not-allowed"
              title="Coming soon"
            >
              Game Chat
            </button>
          </div>
        </div>
      </div>

      {/* ── Mobile Bottom Sheet (unchanged, hidden on md+) ── */}
      <BottomSheet
        strip={
          <CatalogueStrip
            rapps={rapps}
            onDeploy={() => {}}
            dragState={dragState}
            basestations={basestations.map((bs) => ({ id: bs.id, name: bs.name }))}
            onConfirmDeploy={handleConfirmDeploy}
          />
        }
      >
        <ExpandableSection
          title="Events"
          badge={combinedActiveEvents.length > 0 ? combinedActiveEvents.length : undefined}
        >
          <MemoizedEventPanel events={combinedActiveEvents} />
        </ExpandableSection>
        <ExpandableSection title="Leaderboard">
          {realTimeState.leaderboard.length === 0
            ? <LeaderboardSkeleton />
            : <MemoizedLeaderboard entries={realTimeState.leaderboard} currentPlayerId={playerId} />
          }
        </ExpandableSection>
      </BottomSheet>

      {/* Tune Modal */}
      {tuneTarget && (
        <TuneModal
          isOpen={tuneModalOpen}
          onClose={() => { setTuneModalOpen(false); setTuneTarget(null); }}
          rappName={tuneTarget.name}
          rappId={tuneTarget.id}
          currentThreshold={tuneTarget.threshold}
          currentAggressiveness={(tuneTarget.aggressiveness as 'LOW' | 'MODERATE' | 'HIGH') || undefined}
          onConfirm={handleConfirmTune}
        />
      )}

      {ws.error && (
        <div className="absolute bottom-4 left-4 bg-danger/20 text-danger px-3 py-2 rounded text-sm z-50">
          {ws.error}
        </div>
      )}
    </div>
  );
}
