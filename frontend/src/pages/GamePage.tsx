import { useEffect, useState, useCallback, useMemo, useRef, lazy, Suspense, memo } from 'react';
import { Layers, AlertTriangle, BarChart3 } from 'lucide-react';
import { useGame } from '../context/GameContext';
import { useWebSocket } from '../hooks/useWebSocket';
import { useGameState, type GameEvent } from '../hooks/useGameState';
import { useGameSubscriptions } from '../hooks/useGameSubscriptions';
import { useSoundEffects } from '../hooks/useSoundEffects';
import { BasestationDetail } from '../components/game/BasestationDetail';
import { RappCatalogue, type RappTemplate } from '../components/game/RappCatalogue';
import { DeployModal } from '../components/game/DeployModal';
import { TuneModal } from '../components/game/TuneModal';
import { ToastContainer, type ToastMessage } from '../components/ui/Toast';
import { EventAlertContainer, useEventAlerts } from '../components/game/EventAlert';
import { EventPanel, type ActiveEvent } from '../components/game/EventPanel';
import { Leaderboard } from '../components/game/Leaderboard';
import { ScoreSummary } from '../components/game/ScoreSummary';
import { SettingsToolbar } from '../components/ui/SettingsToolbar';
import { apiGet, apiPost, apiPut } from '../services/api';
import { BasestationsSkeleton, LeaderboardSkeleton } from '../components/ui/Skeleton';
import { BottomSheet } from '../components/ui/BottomSheet';

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

type SidebarTab = 'rapps' | 'events' | 'leaderboard';

export function GamePage() {
  const { gameState, sessionCode, token, playerId } = useGame();
  const ws = useWebSocket();
  const realTimeState = useGameState();
  const { playDeploy, playEventAlert, playGameEnd } = useSoundEffects();
  const [activeTab, setActiveTab] = useState<SidebarTab>('rapps');
  const [selectedBasestationId, setSelectedBasestationId] = useState<number | null>(null);
  const [basestations, setBasestations] = useState<BasestationApiData[]>([]);
  const [basestationsLoading, setBasestationsLoading] = useState(true);

  // Deploy modal state
  const [deployModalOpen, setDeployModalOpen] = useState(false);
  const [selectedRapp, setSelectedRapp] = useState<RappTemplate | null>(null);
  const [preSelectedBsId, setPreSelectedBsId] = useState<number | null>(null);

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

  const handleSelectBasestation = useCallback((id: number | null) => {
    setSelectedBasestationId(id);
  }, []);

  // --- Deploy flow ---
  const handleOpenDeploy = useCallback((rapp: RappTemplate) => {
    setSelectedRapp(rapp);
    setPreSelectedBsId(selectedBasestationId);
    setDeployModalOpen(true);
  }, [selectedBasestationId]);

  const handleOpenDeployFromDetail = useCallback(() => {
    // Switch to catalogue view with basestation pre-selected
    setPreSelectedBsId(selectedBasestationId);
    setSelectedRapp(null);
    setDeployModalOpen(false);
    setSelectedBasestationId(null); // Show catalogue
  }, [selectedBasestationId]);

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

  // --- Tune flow ---
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

  const tabs: { id: SidebarTab; label: string; icon: typeof Layers }[] = [
    { id: 'rapps', label: 'rApps', icon: Layers },
    { id: 'events', label: 'Events', icon: AlertTriangle },
    { id: 'leaderboard', label: 'Leaderboard', icon: BarChart3 },
  ];

  /** Shared tab content renderer used by both desktop sidebar and mobile bottom sheet */
  const renderTabContent = () => (
    <>
      {activeTab === 'rapps' && (
        <div>
          {selectedBasestationId ? (
            (() => {
              const selected = mergedBasestations.find(
                (bs) => bs.id === selectedBasestationId,
              );
              if (!selected) return null;

              // Merge real-time rApp deployments with REST data
              const rtRapps = realTimeState.rappDeployments
                .filter((r) => r.basestationId === selected.id)
                .map((r) => ({
                  id: r.deploymentId,
                  templateId: 0,
                  name: r.name,
                  status: r.newStatus,
                  version: r.version,
                  deployedAt: '',
                }));

              // Combine REST rApps with real-time updates (prefer real-time)
              const rtIds = new Set(rtRapps.map((r) => r.id));
              const combinedRapps = [
                ...rtRapps,
                ...selected.deployedRapps.filter((r) => !rtIds.has(r.id)),
              ];

              // Merge real-time events with REST data
              const rtEvents = realTimeState.events
                .filter((e) => e.basestationId === selected.id)
                .map((e) => ({
                  id: e.eventId,
                  eventType: e.eventType,
                  severity: e.severity,
                  description: e.description,
                  escalationLevel: 0,
                  createdAt: '',
                }));

              const rtEventIds = new Set(rtEvents.map((e) => e.id));
              const combinedEvents = [
                ...rtEvents,
                ...selected.activeEvents.filter((e) => !rtEventIds.has(e.id)),
              ];

              return (
                <BasestationDetail
                  basestation={{
                    id: selected.id,
                    name: selected.name,
                    metrics: selected.metrics,
                    deployedRapps: combinedRapps,
                    activeEvents: combinedEvents,
                  }}
                  onTune={handleTune}
                  onDisable={handleDisable}
                  onRollback={handleRollback}
                  onDeployRapp={handleOpenDeployFromDetail}
                />
              );
            })()
          ) : (
            <RappCatalogue onDeploy={handleOpenDeploy} />
          )}
        </div>
      )}
      {activeTab === 'events' && (
        <MemoizedEventPanel events={combinedActiveEvents} />
      )}
      {activeTab === 'leaderboard' && (
        realTimeState.leaderboard.length === 0
          ? <LeaderboardSkeleton />
          : <MemoizedLeaderboard entries={realTimeState.leaderboard} currentPlayerId={playerId} />
      )}
    </>
  );

  return (
    <div className="flex flex-col md:flex-row h-full relative">
      {/* Generic Toasts (deploy success, tune success, etc.) */}
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      {/* Event Alerts (dedicated event notifications with severity colours) */}
      <EventAlertContainer alerts={eventAlerts} onDismiss={dismissEventAlert} />

      {/* Left panel — 3D Map (full width on mobile, 70% on desktop) */}
      <div className="flex-1 md:flex-[7] relative min-w-0 min-h-0">
        {/* Settings toolbar (sound + theme toggles) */}
        <div className="absolute top-3 right-3 z-10">
          <SettingsToolbar />
        </div>
        <ScoreSummary
          entry={realTimeState.leaderboard.find((e) => e.playerId === playerId)}
        />
        <Suspense fallback={<BasestationsSkeleton />}>
          <IsometricMap
            basestations={mergedBasestations}
            selectedBasestationId={selectedBasestationId}
            onSelectBasestation={handleSelectBasestation}
          />
        </Suspense>
        {basestationsLoading && <BasestationsSkeleton />}
      </div>

      {/* Right panel — Sidebar (hidden on mobile, shown on md+) */}
      <div className="hidden md:flex md:flex-[3] md:min-w-[280px] md:max-w-[400px] bg-surface border-l border-surface-lighter flex-col">
        {/* Tab bar */}
        <div className="flex border-b border-surface-lighter">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-3 text-sm font-medium transition-colors ${
                  activeTab === tab.id
                    ? 'text-primary border-b-2 border-primary'
                    : 'text-text-muted hover:text-text'
                }`}
              >
                <Icon size={16} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {/* Tab content */}
        <div className="flex-1 overflow-y-auto p-4">
          {renderTabContent()}
        </div>
      </div>

      {/* Mobile bottom sheet (visible only on mobile <md) */}
      <BottomSheet>
        {/* Tab bar inside bottom sheet */}
        <div className="flex border-b border-surface-lighter px-2">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium transition-colors ${
                  activeTab === tab.id
                    ? 'text-primary border-b-2 border-primary'
                    : 'text-text-muted hover:text-text'
                }`}
              >
                <Icon size={14} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
        <div className="p-4 overflow-y-auto">
          {renderTabContent()}
        </div>
      </BottomSheet>

      {/* Deploy Modal */}
      <DeployModal
        isOpen={deployModalOpen}
        onClose={() => setDeployModalOpen(false)}
        rapp={selectedRapp}
        basestations={basestations.map((bs) => ({ id: bs.id, name: bs.name }))}
        preSelectedBasestationId={preSelectedBsId}
        onConfirm={handleConfirmDeploy}
      />

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
