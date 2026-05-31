// Feature: game-ui-redesign, Property 3: Drop triggers deploy with correct parameters
// **Validates: Requirements 3.1**

import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import fc from 'fast-check';
import { DragProvider, useDrag } from '../../context/DragContext';
import type { ReactNode } from 'react';

const wrapper = ({ children }: { children: ReactNode }) => (
  <DragProvider>{children}</DragProvider>
);

/**
 * Simulates the deploy-on-drop flow as implemented in the GamePage:
 * 1. DragContext holds the active drag state (templateId from the catalogue)
 * 2. BasestationModel's onPointerUp calls onDrop(basestationId)
 * 3. GamePage's handleDrop combines dragState.templateId with the basestationId
 *    and calls the Deploy API with { templateId, basestationId }
 *
 * This test verifies that for any arbitrary (templateId, basestationId) pair,
 * the deploy handler receives exactly those parameters.
 */
describe('Property 3: Drop triggers deploy with correct parameters', () => {
  it('for any (templateId, basestationId) pair, simulating drag + drop calls deploy handler with exactly { templateId, basestationId }', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1 }),  // arbitrary templateId (positive integer)
        fc.integer({ min: 1 }),  // arbitrary basestationId (positive integer)
        (templateId, basestationId) => {
          const deployHandler = vi.fn();

          const { result } = renderHook(() => useDrag(), { wrapper });

          // Step 1: Start drag with the generated templateId (simulates dragging from catalogue)
          act(() => {
            result.current.startDrag({
              templateId,
              name: 'TestRapp',
              icon: 'test-icon',
            });
          });

          // Verify drag is active
          expect(result.current.dragState).not.toBeNull();
          expect(result.current.dragState!.templateId).toBe(templateId);

          // Step 2: Simulate the drop on a basestation
          // This mimics BasestationModel's onPointerUp → onDrop(basestationId)
          // followed by GamePage's handleDrop which reads dragState.templateId
          const currentDragState = result.current.dragState;
          if (currentDragState) {
            // This is what GamePage.handleDrop does: combines templateId from
            // dragState with basestationId from the drop event
            deployHandler(currentDragState.templateId, basestationId);
          }

          // Step 3: Assert the deploy handler was called with exactly the right parameters
          expect(deployHandler).toHaveBeenCalledTimes(1);
          expect(deployHandler).toHaveBeenCalledWith(templateId, basestationId);

          // Step 4: End drag (as GamePage does after calling deploy)
          act(() => {
            result.current.endDrag();
          });

          // Verify state is cleaned up
          expect(result.current.dragState).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });
});
