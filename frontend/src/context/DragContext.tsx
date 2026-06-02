import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';

export interface DragState {
  templateId: number;
  name: string;
  icon: string;
}

export interface DragContextValue {
  dragState: DragState | null;
  hoveredTargetId: number | null;
  startDrag: (state: DragState) => void;
  setHoveredTarget: (id: number | null) => void;
  endDrag: () => void;
}

const DragContext = createContext<DragContextValue | null>(null);

export function DragProvider({ children }: { children: ReactNode }) {
  const [dragState, setDragState] = useState<DragState | null>(null);
  const [hoveredTargetId, setHoveredTargetId] = useState<number | null>(null);

  const startDrag = useCallback((state: DragState) => {
    setDragState(state);
  }, []);

  const setHoveredTarget = useCallback((id: number | null) => {
    setHoveredTargetId(id);
  }, []);

  const endDrag = useCallback(() => {
    setDragState(null);
    setHoveredTargetId(null);
  }, []);

  // Cleanup: reset drag state on unmount
  useEffect(() => {
    return () => {
      setDragState(null);
      setHoveredTargetId(null);
    };
  }, []);

  return (
    <DragContext.Provider value={{ dragState, hoveredTargetId, startDrag, setHoveredTarget, endDrag }}>
      {children}
    </DragContext.Provider>
  );
}

export function useDrag(): DragContextValue {
  const context = useContext(DragContext);
  if (!context) {
    throw new Error('useDrag must be used within a DragProvider');
  }
  return context;
}

/**
 * Optional variant of useDrag that returns null when no DragProvider is present.
 * Useful for components that need to support drag-and-drop but may be rendered
 * outside a DragProvider during transition periods.
 */
export function useDragOptional(): DragContextValue | null {
  return useContext(DragContext);
}
