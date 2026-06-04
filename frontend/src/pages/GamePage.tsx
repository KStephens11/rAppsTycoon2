import { useEffect, useState, useCallback, useMemo, useRef, memo } from 'react';
import { Radio } from 'lucide-react';
import { useGame } from '../context/GameContext';
import { useWebSocket } from '../hooks';
import { useGameState, type GameEvent } from '../hooks';
import { useGameSubscriptions } from '../hooks';
import { useSoundEffects } from '../hooks';
import { RappCatalogue, type RappTemplate } from '../components/game/RappCatalogue';
import { CatalogueStrip } from '../components/game/CatalogueStrip';
import { TuneModal } from '../components/game/TuneModal';
import { GameMap } from '../components/game/GameMap';
import { BasestationPopover } from '../components/game/BasestationPopover';
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
import { Confetti } from '../components/Confetti';

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
  const [basestations, setBasestations] = useState<BasestationApiData[]>([]);
  const [basestationsLoading, setBasestationsLoading] = useState(true);
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

  const [, setResolvedBasestationIds] = useState<Set<number>>(new Set());
  const [showResolutionCelebration, setShowResolutionCelebration] = useState(false);
  const [selectedBasestationData, setSelectedBasestationData] = useState<{
    basestation: BasestationApiData;
    anchor: { x: number; y: number };
  } | null>(null);
  const postActionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const previousEventIdsRef = useRef<Map<number, Set<number>>>(new Map());
  const shouldCelebrateResolutionRef = useRef(false); // Track if we should celebrate event resolution

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
          // Only trigger celebration if this fetch was triggered by a player action
          if (shouldCelebrateResolutionRef.current) {
            setShowResolutionCelebration(true);
            setTimeout(() => setShowResolutionCelebration(false), 2200);
            shouldCelebrateResolutionRef.current = false; // Reset flag
          }
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
    shouldCelebrateResolutionRef.current = true; // Set flag to celebrate resolution
    fetchBasestations();
    if (postActionTimerRef.current) clearTimeout(postActionTimerRef.current);
    postActionTimerRef.current = setTimeout(() => {
      shouldCelebrateResolutionRef.current = true; // Also celebrate on delayed fetch
      fetchBasestations();
    }, 6000);
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

  const handleConfirmDeploy = useCallback(async (templateId: number, basestationId: number) => {
    if (!sessionCode || !token) return;
    await apiPost(`/api/sessions/${sessionCode}/rapps/deploy`, { templateId, basestationId }, token);
    addToast('rApp deployed successfully!', 'success');
    playDeploy();
    fetchBasestationsAfterAction();
    endDrag(); // Clear drag state after successful deployment
  }, [sessionCode, token, addToast, fetchBasestationsAfterAction, playDeploy, endDrag]);

  const handleConfirmTune = useCallback(async (rappId: number, threshold: number, aggressiveness: string) => {
    if (!sessionCode || !token) return;
    await apiPut(`/api/sessions/${sessionCode}/rapps/${rappId}/tune`, { threshold, aggressiveness }, token);
    addToast('rApp tuned successfully!', 'success');
    fetchBasestationsAfterAction();
  }, [sessionCode, token, addToast, fetchBasestationsAfterAction]);

  const handleEventClick = useCallback((basestationName: string) => {
    const bs = basestations.find(b => b.name === basestationName);
    if (!bs) return;
    
    // Trigger the same flow as clicking the basestation on the map
    // by calling onSelectBasestation with the basestation ID
    // The GameMap will calculate the proper screen position
    // For now, we'll use a rough approximation based on world position
    
    // Calculate basestation position using isometric projection
    const COLS = 60;
    const ROWS = 60;
    const WORLD_SIZE = 600;
    const CELL = WORLD_SIZE / COLS;
    const TW = 18;
    const TH = 9;
    
    const gc = Math.min(COLS - 1, Math.max(0, Math.floor(bs.positionX / CELL)));
    const gr = Math.min(ROWS - 1, Math.max(0, Math.floor(bs.positionY / CELL)));
    
    // Get map container dimensions
    const mapElements = document.querySelectorAll('canvas');
    const canvas = Array.from(mapElements).find(c => c.width > 100); // Find the game map canvas
    if (!canvas) return;
    
    const rect = canvas.getBoundingClientRect();
    
    // Calculate isometric position with default camera
    const ox = 0;
    const oy = -(ROWS * TH) / 4;
    const isoX = ox + (gc - gr) * (TW / 2);
    const isoY = oy + (gc + gr) * (TH / 2);
    
    // Apply default zoom and center transform
    const zoom = 2.0;
    const screenX = rect.left + rect.width / 2 + isoX * zoom;
    const screenY = rect.top + rect.height / 2 + isoY * zoom - 62 * zoom; // Offset for tower top
    
    setSelectedBasestationData({
      basestation: bs,
      anchor: { x: screenX, y: screenY }
    });
  }, [basestations]);

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

        {/* Money display */}
        {gameState === 'active' && playerId && (
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border bg-surface-light border-surface-lighter">
            <span className="text-xs font-bold text-primary">€</span>
            <span className="text-xs font-bold tabular-nums text-primary">
              {realTimeState.leaderboard
                .find((entry) => entry.playerId === playerId)
                ?.scores.money.toLocaleString() ?? '0'}
            </span>
          </div>
        )}

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
          {/* Toast notifications — inside map area */}
          <div className="absolute bottom-3 right-3 z-20">
            <ToastContainer toasts={toasts} onDismiss={dismissToast} />
          </div>

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

          {/* City map */}
          <div className="absolute inset-0">
            <GameMap
              basestations={basestations}
              dragState={dragState}
              onDropDeploy={(templateId, basestationId) => {
                handleConfirmDeploy(templateId, basestationId);
              }}
              onSelectBasestation={(id, sx, sy) => {
                const bs = basestations.find(b => b.id === id);
                if (bs) {
                  // Single atomic state update to prevent flash
                  setSelectedBasestationData({
                    basestation: bs,
                    anchor: { x: sx, y: sy }
                  });
                }
              }}
            />
          </div>

          {/* Basestation popover */}
          {selectedBasestationData && (
            <BasestationPopover
              basestation={selectedBasestationData.basestation}
              anchorPosition={selectedBasestationData.anchor}
              onClose={() => { setSelectedBasestationData(null); }}
              onTune={(rappId, rappName, threshold, aggressiveness) => {
                setTuneTarget({ id: rappId, name: rappName, threshold, aggressiveness });
                setTuneModalOpen(true);
              }}
              onDisable={async (rappId) => {
                try {
                  await apiPut(`/api/sessions/${sessionCode}/rapps/${rappId}/disable`, {}, token ?? undefined);
                  addToast('rApp disabled', 'success');
                } catch {
                  addToast('Failed to disable rApp', 'error');
                }
              }}
              onRollback={async (rappId) => {
                try {
                  await apiPut(`/api/sessions/${sessionCode}/rapps/${rappId}/rollback`, {}, token ?? undefined);
                  addToast('rApp rolled back', 'success');
                } catch {
                  addToast('Failed to rollback rApp', 'error');
                }
              }}
            />
          )}
          {basestationsLoading && <BasestationsSkeleton />}
        </div>

        {/* Right panel — Active Events */}
        <div className="hidden md:flex w-80 bg-surface border-l border-surface-lighter flex-col shrink-0">
          <div className="flex-1 overflow-y-auto p-3">
            <div className="relative">
              {showResolutionCelebration && (
                <Confetti duration={2000} particleCount={80} contained />
              )}
              <MemoizedEventPanel events={combinedActiveEvents} onEventClick={handleEventClick} />
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
          <MemoizedEventPanel events={combinedActiveEvents} onEventClick={handleEventClick} />
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
