// Feature: game-ui-redesign, Property 2: Drag cancellation resets state
// **Validates: Requirements 1.4**

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import fc from 'fast-check';
import { DragProvider, useDrag } from './DragContext';
import type { ReactNode } from 'react';

const wrapper = ({ children }: { children: ReactNode }) => (
  <DragProvider>{children}</DragProvider>
);

describe('Property 2: Drag cancellation resets state', () => {
  it('for any active drag state, calling endDrag resets dragState to null and hoveredTargetId to null', () => {
    fc.assert(
      fc.property(
        fc.record({
          templateId: fc.integer({ min: 1 }),
          name: fc.string({ minLength: 1 }),
          icon: fc.string(),
        }),
        fc.option(fc.integer({ min: 1 }), { nil: null }),
        (dragInput, hoveredTarget) => {
          const { result } = renderHook(() => useDrag(), { wrapper });

          // Start a drag with arbitrary state
          act(() => {
            result.current.startDrag(dragInput);
          });

          // Optionally set a hovered target
          if (hoveredTarget !== null) {
            act(() => {
              result.current.setHoveredTarget(hoveredTarget);
            });
          }

          // Verify drag is active
          expect(result.current.dragState).not.toBeNull();

          // Cancel the drag by calling endDrag
          act(() => {
            result.current.endDrag();
          });

          // Assert drag state is reset to null (idle)
          expect(result.current.dragState).toBeNull();
          // Assert hovered target is also reset
          expect(result.current.hoveredTargetId).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });
});
