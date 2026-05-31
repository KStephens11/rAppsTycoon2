import { useEffect, useState, useCallback, useMemo, useRef, lazy, Suspense, memo } from 'react';
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
import { ScoreSummary } from '../components/game/ScoreSummary';
import { SettingsToolbar } from '../components/ui/SettingsToolbar';
import { BottomSheet } from '../components/ui/BottomSheet';
import { ExpandableSection } from '../components/ui/ExpandableSection';
import { apiGet, apiPost, apiPut } from '../services/api';
import { BasestationsSkeleton, LeaderboardSkeleton } from '../components/ui/Skeleton';
import { DragProvider, useDrag } from '../context/DragContext';
import { DragPreview } from '../components/game/DragPreview';
import { BasestationPopover } from '../components/game/BasestationPopover';

// Lazy-load the heavy 3D map component
const IsometricMap = lazy(() => import('../components/game/IsometricMap'));

// Memoize EventPanel and Leaderboard to avoid unnecessary re-renders
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

  // Tune modal state
  const [tuneModalOpen, setTuneModalOpen] = useState(false);
  const [tuneTarget, setTuneTarget] = useState<{
    id: number;
    name: string;
    threshold?: number;
    aggressiveness?: string;
  } | null>(null);

  // Toast state (for non-event messages: deploy success, tune success, etc.)
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  // Event resolution tracking — detect when events disappear from basestations
  const [resolvedBasestationIds, setResolvedBasestationIds] = useState<Set<number>>(new Set());
  const previousEventIdsRef = useRef<Map<number, Set<number>>>(new Map());

  const addToast = useCallback((message: string, type: 'error' | 'success' | 'info' = 'success') => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, message, type }]);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // Event alert state (dedicated event notifications with severity colours and icons)
  const { alerts: eventAlerts, addEventAlert, dismissEventAlert } = useEventAlerts();

  // Event alert callback — triggers a dedicated EventAlert when a new event arrives via WebSocket
  const handleEventReceived = useCallback((event: GameEvent) => {
    addEventAlert(event);
    playEventAlert();
  }, [addEventAlert, playEventAlert]);

  // Set up subscriptions that route messages to game state
  const subscriptionCallbacks = useMemo(() => ({
    onEventReceived: handleEventReceived,
  }), [handleEventReceived]);

  useGameSubscriptions(ws, realTimeState, subscriptionCallbacks);

  // Connect WebSocket when game is active
  useEffect(() => {
    if (gameState === 'active') {
      ws.connect();
    }

    return () => {
      ws.disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gameState]);

  // Play game end fanfare when game completes
  useEffect(() => {
    if (gameState === 'completed') {
      playGameEnd();
    }
  }, [gameState, playGameEnd]);

  // Fetch basestations via REST API
  const fetchBasestations = useCallback(() => {
    if (!sessionCode || !token) return;

    apiGet<BasestationsResponse>(`/api/sessions/${sessionCode}/basestations`, token)
      .then((data) => {
        // Detect resolved events: compare previous event IDs with current
        const newResolvedBsIds: number[] = [];
        const currentEventMap = new Map<number, Set<number>>();

        for (const bs of data.basestations) {
          const currentEventIds = new Set(bs.activeEvents.map((e) => e.id));
          currentEventMap.set(bs.id, currentEventIds);

          const previousIds = previousEventIdsRef.current.get(bs.id);
          if (previousIds && previousIds.size > 0) {
            // Check if any previously active events are now gone (resolved)
            for (const prevId of previousIds) {
              if (!currentEventIds.has(prevId)) {
                newResolvedBsIds.push(bs.id);
                break; // One resolved event is enough to trigger the animation
              }
            }
          }
        }

        // Update the previous events ref for next comparison
        previousEventIdsRef.current = currentEventMap;

        // Trigger resolution animations
        if (newResolvedBsIds.length > 0) {
          setResolvedBasestationIds(new Set(newResolvedBsIds));
          // Clear after animation duration (2 seconds)
          setTimeout(() => {
            setResolvedBasestationIds(new Set());
          }, 2000);
        }

        setBasestations(data.basestations);
        setBasestationsLoading(false);
      })
      .catch((err) => {
        console.error('Failed to load basestations:', err);
        setBasestationsLoading(false);
      });
  }, [sessionCode, token]);

  useEffect(() => {
    fetchBasestations();
  }, [fetchBasestations]);

  // Fetch rApp catalogue for mobile CatalogueStrip
  const fetchCatalogue = useCallback(() => {
    if (!token) return;
    apiGet<CatalogueResponse>('/api/rapps/catalogue', token)
      .then((data) => {
        setRapps(data.rapps);
      })
      .catch((err) => {
        console.error('Failed to load catalogue:', err);
      });
  }, [token]);

  useEffect(() => {
    fetchCatalogue();
  }, [fetchCatalogue]);

  const handleSelectBasestation = useCallback((id: number | null) => {
    setSelectedBasestationId(id);
  }, []);

  // Track screen position of selected basestation for popover anchoring
  const handleScreenPositionUpdate = useCallback((position: { x: number; y: number } | null) => {
    setPopoverAnchor(position);
  }, []);

  // Get the full basestation data for the popover
  const selectedBasestation = useMemo(() => {
    if (!selectedBasestationId) return null;
    return basestations.find((bs) => bs.id === selectedBasestationId) ?? null;
  }, [selectedBasestationId, basestations]);

  // --- Deploy flow ---
  const handleConfirmDeploy = useCallback(async (templateId: number, basestationId: number) => {
    if (!sessionCode || !token) return;
    await apiPost(
      `/api/sessions/${sessionCode}/rapps/deploy`,
      { templateId, basestationId },
      token,
    );
    addToast('rApp deployed successfully!', 'success');
    playDeploy();
    // Refetch basestations to update deployed rApps list
    fetchBasestations();
  }, [sessionCode, token, addToast, fetchBasestations, playDeploy]);

  // --- Drag-and-drop deploy handler ---
  const handleDrop = useCallback(async (basestationId: number) => {
    if (!dragState || !sessionCode || !token || isDeploying) return;
    const { templateId } = dragState;
    setIsDeploying(true);
    try {
      await apiPost(
        `/api/sessions/${sessionCode}/rapps/deploy`,
        { templateId, basestationId },
        token,
      );
      addToast('rApp deployed successfully!', 'success');
      playDeploy();
      fetchBasestations();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to deploy rApp';
      addToast(message, 'error');
    } finally {
      endDrag();
      setIsDeploying(false);
    }
  }, [dragState, sessionCode, token, isDeploying, addToast, playDeploy, fetchBasestations, endDrag]);

  // --- Tune flow ---
  // Note: handleTune is wired to BasestationPopover
  const handleTune = useCallback((rappId: number, rappName: string, threshold?: number, aggressiveness?: string) => {
    setTuneTarget({ id: rappId, name: rappName, threshold, aggressiveness });
    setTuneModalOpen(true);
  }, []);

  const handleConfirmTune = useCallback(async (rappId: number, threshold: number, aggressiveness: string) => {
    if (!sessionCode || !token) return;
    await apiPut(
      `/api/sessions/${sessionCode}/rapps/${rappId}/tune`,
      { threshold, aggressiveness },
      token,
    );
    addToast('rApp tuned successfully!', 'success');
    fetchBasestations();
  }, [sessionCode, token, addToast, fetchBasestations]);

  // --- Disable flow ---
  const handleDisable = useCallback(async (rappId: number) => {
    if (!sessionCode || !token) return;
    try {
      await apiPut(
        `/api/sessions/${sessionCode}/rapps/${rappId}/disable`,
        undefined,
        token,
      );
      addToast('rApp disabled', 'info');
      fetchBasestations();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to disable rApp';
      addToast(message, 'error');
    }
  }, [sessionCode, token, addToast, fetchBasestations]);

  // --- Rollback flow ---
  const handleRollback = useCallback(async (rappId: number) => {
    if (!sessionCode || !token) return;
    try {
      await apiPut(
        `/api/sessions/${sessionCode}/rapps/${rappId}/rollback`,
        undefined,
        token,
      );
      addToast('rApp rolled back to previous version', 'success');
      fetchBasestations();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to rollback rApp';
      addToast(message, 'error');
    }
  }, [sessionCode, token, addToast, fetchBasestations]);

  // Merge REST data with real-time metrics
  const mergedBasestations = useMemo(() => basestations.map((bs) => {
    const rtState = realTimeState.basestations.find((rt) => rt.id === bs.id);
    const activeRapps = realTimeState.rappDeployments.filter(
      (r) => r.basestationId === bs.id && r.newStatus === 'ACTIVE',
    );
    const hasEvent = realTimeState.events.some((e) => e.basestationId === bs.id);

    // Compute highest severity from both REST and real-time events
    const severityOrder: Record<string, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
    const allSeverities = [
      ...bs.activeEvents.map((e) => e.severity),
      ...realTimeState.events
        .filter((e) => e.basestationId === bs.id)
        .map((e) => e.severity),
    ];
    const highestSeverity = allSeverities.length > 0
      ? allSeverities.reduce((highest, current) =>
          (severityOrder[current] || 0) > (severityOrder[highest] || 0) ? current : highest,
        ) as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
      : undefined;

    // Compute highest escalation level (0-3) from REST events on this basestation
    const highestEscalation = bs.activeEvents.length > 0
      ? Math.max(...bs.activeEvents.map((e) => e.escalationLevel))
      : 0;

    return {
      ...bs,
      metrics: rtState ? rtState.metrics : bs.metrics,
      activeRappsCount: activeRapps.length + bs.deployedRapps.filter((r) => r.status === 'ACTIVE').length,
      hasEvent: hasEvent || bs.activeEvents.length > 0,
      highestSeverity,
      highestEscalation,
      showResolution: resolvedBasestationIds.has(bs.id),
    };
  }), [basestations, realTimeState.basestations, realTimeState.rappDeployments, realTimeState.events, resolvedBasestationIds]);

  // Combine REST events (from basestations) with real-time WebSocket events for the EventPanel
  const combinedActiveEvents: ActiveEvent[] = useMemo(() => {
    // Collect all REST events from basestations, attaching basestation name
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

    // Collect real-time WebSocket events
    const rtEvents: ActiveEvent[] = realTimeState.events.map((e) => ({
      id: e.eventId,
      eventType: e.eventType,
      severity: e.severity,
      description: e.description,
      escalationLevel: 0,
      createdAt: '',
      basestationName: e.basestationName,
    }));

    // Merge: prefer REST data (has escalation + createdAt), add any RT-only events
    const restIds = new Set(restEvents.map((e) => e.id));
    return [...restEvents, ...rtEvents.filter((e) => !restIds.has(e.id))];
  }, [basestations, realTimeState.events]);

  return (
      <div className="flex flex-col h-full relative">
        {/* Generic Toasts (deploy success, tune success, etc.) */}
        <ToastContainer toasts={toasts} onDismiss={dismissToast} />

        {/* Event Alerts (dedicated event notifications with severity colours) */}
        <EventAlertContainer alerts={eventAlerts} onDismiss={dismissEventAlert} />

        {/* Drag Preview — follows cursor during drag */}
        <DragPreview />

        {/* Main content: Map + Right Panel in a row */}
        <div className="flex flex-1 min-h-0">
          {/* Map area — takes remaining space */}
          <div className="flex-1 relative min-w-0 min-h-0">
            {/* Settings toolbar (sound + theme toggles) */}
            <div className="absolute top-3 right-3 z-10">
              <SettingsToolbar />
            </div>
            <ScoreSummary
              entry={realTimeState.leaderboard.find((e) => e.playerId === playerId)}
            />

            {/* Floating Leaderboard — top-left, semi-transparent */}
            <div className="absolute top-14 left-3 z-10 w-56 bg-surface/80 backdrop-blur-sm border border-surface-lighter/50 rounded-lg shadow-lg overflow-hidden hidden md:block">
              <div className="p-3 max-h-64 overflow-y-auto">
                {realTimeState.leaderboard.length === 0
                  ? <LeaderboardSkeleton />
                  : <MemoizedLeaderboard entries={realTimeState.leaderboard} currentPlayerId={playerId} />
                }
              </div>
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

            {/* Basestation Popover — floats over the map near the selected basestation */}
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

          {/* Right panel — RappCatalogue only (always visible) */}
          <div className="hidden md:flex w-72 bg-surface border-l border-surface-lighter flex-col">
            {/* rApp Catalogue */}
            <div className="flex-1 overflow-y-auto p-4">
              <RappCatalogue
                basestations={basestations.map((bs) => ({ id: bs.id, name: bs.name }))}
                onConfirmDeploy={handleConfirmDeploy}
              />
            </div>
          </div>
        </div>

        {/* Bottom bar — Event Panel (h-36, horizontally scrollable) — hidden on mobile */}
        <div className="hidden md:block h-36 border-t border-surface-lighter bg-surface overflow-x-auto overflow-y-auto p-3">
          <MemoizedEventPanel events={combinedActiveEvents} />
        </div>

        {/* Mobile Bottom Sheet — visible only on mobile (< 768px) */}
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
            onClose={() => {
              setTuneModalOpen(false);
              setTuneTarget(null);
            }}
            rappName={tuneTarget.name}
            rappId={tuneTarget.id}
            currentThreshold={tuneTarget.threshold}
            currentAggressiveness={
              (tuneTarget.aggressiveness as 'LOW' | 'MODERATE' | 'HIGH') || undefined
            }
            onConfirm={handleConfirmTune}
          />
        )}

        {ws.error && (
          <div className="absolute bottom-4 left-4 bg-danger/20 text-danger px-3 py-2 rounded text-sm">
            {ws.error}
          </div>
        )}
      </div>
  );
}
