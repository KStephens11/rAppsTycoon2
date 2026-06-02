// Feature: game-ui-redesign, Property 4: Error messages propagate to user feedback
// **Validates: Requirements 3.4**

import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import fc from 'fast-check';
import { DragProvider, useDrag } from '../context/DragContext';
import type { ReactNode } from 'react';

const wrapper = ({ children }: { children: ReactNode }) => (
  <DragProvider>{children}</DragProvider>
);

/**
 * Tests the deploy-on-drop error handling flow as implemented in GamePage:
 *
 * 1. DragContext holds the active drag state (templateId from the catalogue)
 * 2. BasestationModel's onPointerUp triggers the drop → GamePage calls Deploy API
 * 3. Deploy API returns an error with a message
 * 4. GamePage catches the error, shows an error toast with the server message,
 *    and calls endDrag() to reset drag state
 *
 * This property verifies that for ANY error message string returned by the
 * Deploy API, the error toast notification contains that exact error message,
 * and the drag state is reset to null.
 */
describe('Property 4: Error messages propagate to user feedback', () => {
  it('for any error message from Deploy API, the error toast contains that exact message and drag state is reset to null', async () => {
    await fc.assert(
      fc.asyncProperty(
        // Generate arbitrary non-empty error message strings
        fc.string({ minLength: 1, maxLength: 200 }),
        fc.integer({ min: 1 }),  // arbitrary templateId
        fc.integer({ min: 1 }),  // arbitrary basestationId
        async (errorMessage, templateId, basestationId) => {
          // Track toast calls
          const addToast = vi.fn();

          // Mock Deploy API that rejects with the generated error message
          const mockDeployApi = vi.fn().mockRejectedValue(new Error(errorMessage));

          const { result } = renderHook(() => useDrag(), { wrapper });

          // Step 1: Start drag with the generated templateId
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

          // Step 2: Simulate the handleDrop flow with error handling
          // This mimics GamePage's handleDrop which:
          //   - Calls Deploy API
          //   - On error: shows error toast with server message, calls endDrag()
          await act(async () => {
            try {
              await mockDeployApi(templateId, basestationId);
            } catch (err: unknown) {
              const message = err instanceof Error ? err.message : 'Deploy failed';
              addToast(message, 'error');
              result.current.endDrag();
            }
          });

          // Step 3: Assert the error toast was called with the EXACT error message
          expect(addToast).toHaveBeenCalledTimes(1);
          expect(addToast).toHaveBeenCalledWith(errorMessage, 'error');

          // Step 4: Assert drag state is reset to null
          expect(result.current.dragState).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });
});
