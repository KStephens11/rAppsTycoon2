import { useEffect, useState, useCallback, useMemo, useRef, lazy, Suspense, memo } from 'react';
import { Radio } from 'lucide-react';
import { useGame } from '../context/GameContext';
import { useWebSocket } from '../hooks';
import { useGameState, type GameEvent } from '../hooks';
import { useGameSubscriptions } from '../hooks';
import { useSoundEffects } from '../hooks';
import { RappCatalogue, type RappTemplate } from '../components/game/RappCatalogue';
import { CatalogueStrip } from '../components/game/CatalogueStrip';
import { TuneModal } from '../components/game/TuneModal';
import { ToastContainer, type ToastMessage } from '../components/ui';
import { EventAlertContainer, useEventAlerts } from '../components/game/EventAlert';
import { EventPanel, type ActiveEvent } from '../components/game/EventPanel';
import { Leaderboard } from '../components/game/Leaderboard';
import { GameTimer } from '../components/game/GameTimer';
import { SettingsToolbar } from '../components/ui/SettingsToolbar';
import { BottomSheet } from '../components/ui';
import { ExpandableSection } from '../components/ui/ExpandableSection';
import { apiGet, apiPost, apiPut } from '../services/api';
import { BasestationsSkeleton, LeaderboardSkeleton } from '../components/ui';
import { DragProvider, useDrag } from '../context/DragContext';
import { DragPreview } from '../components/game/DragPreview';
import { BasestationPopover } from '../components/game/BasestationPopover';
import { Confetti } from '../components/Confetti';

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

// ---

export function GamePage() {
  return (
    <DragProvider>
      <GamePageInner />
    </DragProvider>
  );
}

function GamePageInner() {
  const { gameState, sessionCode, token, playerId } = useGame();
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
  const [currentTick, setCurrentTick] = useState(0);
  const [totalTicks, setTotalTicks] = useState(60);

  const [tuneModalOpen, setTuneModalOpen] = useState(false);
  const [tuneTarget, setTuneTarget] = useState<{
    id: number;
    name: string;
    threshold?: number;
    aggressiveness?: string;
  } | null>(null);

  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const [resolvedBasestationIds, setResolvedBasestationIds] = useState<Set<number>>(new Set());
  const [showResolutionCelebration, setShowResolutionCelebration] = useState(false);
  const postActionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
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
    return () => {
      ws.disconnect();
      if (postActionTimerRef.current) clearTimeout(postActionTimerRef.current);
    };
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
          // Trigger celebration burst for any event resolution
          setShowResolutionCelebration(true);
          setTimeout(() => setShowResolutionCelebration(false), 2200);
        }
        setBasestations(data.basestations);
        setBasestationsLoading(false);
      })
      .catch((err) => {
        console.error('Failed to load basestations:', err);
        setBasestationsLoading(false);
      });
  }, [sessionCode, token]);

  // After a player action, fetch immediately then again after 6 s (≥ one tick interval)
  // so the resolution detection fires as soon as the tick engine processes the change.
  const fetchBasestationsAfterAction = useCallback(() => {
    fetchBasestations();
    if (postActionTimerRef.current) clearTimeout(postActionTimerRef.current);
    postActionTimerRef.current = setTimeout(fetchBasestations, 6000);
  }, [fetchBasestations]);

  useEffect(() => { fetchBasestations(); }, [fetchBasestations]);

  // On mount (including page refresh) fetch the session to get startedAt so
  // the timer counts down from the real game-start time, not from zero.
  useEffect(() => {
    if (!sessionCode || !token || gameState !== 'active') return;
    apiGet<{ startedAt: string | null; totalTicks?: number; currentTick?: number }>(
      `/api/sessions/${sessionCode}`,
      token,
    )
      .then((data) => {
        if (data.currentTick != null) {
          setCurrentTick(data.currentTick);
        }
        if (data.totalTicks) {
          setTotalTicks(data.totalTicks);
        }
      })
      .catch((err) => {
        console.error('Failed to fetch session:', err);
      });
  }, [sessionCode, token, gameState]);

  // Refetch when WebSocket connects/reconnects to catch missed events
  useEffect(() => {
    if (wsConnected) {
      fetchBasestations();
    }
  }, [wsConnected, fetchBasestations]);

  // Periodic basestations poll — safety net for missed WebSocket events
  // Also refreshes the game tick for the timer
  useEffect(() => {
    if (gameState !== 'active' || !sessionCode || !token) return;
    const interval = setInterval(() => {
      fetchBasestations();
      // Refresh tick from session endpoint
      apiGet<{ currentTick?: number; totalTicks?: number }>(
        `/api/sessions/${sessionCode}`,
        token,
      ).then((data) => {
        if (data.currentTick != null) setCurrentTick(data.currentTick);
        if (data.totalTicks) setTotalTicks(data.totalTicks);
      }).catch(() => {});
    }, 5000); // every 5 seconds to match tick interval
    return () => clearInterval(interval);
  }, [gameState, sessionCode, token, fetchBasestations]);

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
    fetchBasestationsAfterAction();
  }, [sessionCode, token, addToast, fetchBasestationsAfterAction, playDeploy]);

  const handleDrop = useCallback(async (basestationId: number) => {
    if (!dragState || !sessionCode || !token || isDeploying) return;
    const { templateId } = dragState;
    setIsDeploying(true);
    try {
      await apiPost(`/api/sessions/${sessionCode}/rapps/deploy`, { templateId, basestationId }, token);
      addToast('rApp deployed successfully!', 'success');
      playDeploy();
      fetchBasestationsAfterAction();
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
    fetchBasestationsAfterAction();
  }, [sessionCode, token, addToast, fetchBasestationsAfterAction]);

  const handleDisable = useCallback(async (rappId: number) => {
    if (!sessionCode || !token) return;
    try {
      await apiPut(`/api/sessions/${sessionCode}/rapps/${rappId}/disable`, undefined, token);
      addToast('rApp disabled', 'info');
      fetchBasestationsAfterAction();
    } catch (err: unknown) {
      addToast(err instanceof Error ? err.message : 'Failed to disable rApp', 'error');
    }
  }, [sessionCode, token, addToast, fetchBasestationsAfterAction]);

  const handleRollback = useCallback(async (rappId: number) => {
    if (!sessionCode || !token) return;
    try {
      await apiPut(`/api/sessions/${sessionCode}/rapps/${rappId}/rollback`, undefined, token);
      addToast('rApp rolled back to previous version', 'success');
      fetchBasestationsAfterAction();
    } catch (err: unknown) {
      addToast(err instanceof Error ? err.message : 'Failed to rollback rApp', 'error');
    }
  }, [sessionCode, token, addToast, fetchBasestationsAfterAction]);

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

        {/* Game timer */}
        {gameState === 'active' && (
          <GameTimer
            currentTick={currentTick}
            totalTicks={totalTicks}
          />
        )}

        {/* Settings */}
        <SettingsToolbar />
      </header>

      {/* ── 3-column main area ── */}
      <div className="flex flex-1 min-h-0">

        {/* Center — Map (full width, overlays float on top) */}
        <div className="flex-1 relative min-w-0 min-h-0">
          {/* Floating Leaderboard — top left */}
          <div className="absolute top-3 left-3 z-10 w-52 hidden md:block bg-surface/80 backdrop-blur-sm border border-surface-lighter/50 rounded-lg shadow-lg overflow-hidden">
            <div className="px-3 py-2 border-b border-surface-lighter/50">
              <h2 className="text-[10px] font-bold uppercase tracking-widest text-text-muted">
                Leaderboard
              </h2>
            </div>
            <div className="p-2 max-h-52 overflow-y-auto">
              {realTimeState.leaderboard.length === 0
                ? <LeaderboardSkeleton />
                : <MemoizedLeaderboard entries={realTimeState.leaderboard} currentPlayerId={playerId} />
              }
            </div>
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

        {/* Right panel — Active Events */}
        <div className="hidden md:flex w-64 bg-surface border-l border-surface-lighter flex-col shrink-0">
          <div className="flex-1 overflow-y-auto p-3">
            <div className="relative">
              {showResolutionCelebration && (
                <Confetti duration={2000} particleCount={80} contained />
              )}
              <MemoizedEventPanel events={combinedActiveEvents} />
            </div>
          </div>
        </div>
      </div>

      {/* ── Bottom bar — rApp Catalogue (full width) ── */}
      <div className="hidden md:flex h-28 border-t border-surface-lighter shrink-0 bg-surface">
        <div className="flex flex-col w-full">
          <div className="px-4 py-1 shrink-0">
            <h3 className="text-[10px] font-bold uppercase tracking-widest text-text-muted">
              rApp Catalogue
            </h3>
          </div>
          <div className="flex-1 px-4 py-1">
            <RappCatalogue
              basestations={basestations.map((bs) => ({ id: bs.id, name: bs.name }))}
              onConfirmDeploy={handleConfirmDeploy}
            />
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
