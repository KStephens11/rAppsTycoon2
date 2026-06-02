// Feature: game-ui-redesign, Property 1: Drag initiation preserves template identity
// **Validates: Requirements 1.1**

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import fc from 'fast-check';
import { DragProvider, useDrag } from './DragContext';
import type { ReactNode } from 'react';

const wrapper = ({ children }: { children: ReactNode }) => (
  <DragProvider>{children}</DragProvider>
);

describe('Property 1: Drag initiation preserves template identity', () => {
  it('for any rApp template, calling startDrag produces a dragState whose templateId matches the input templateId exactly', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1 }),        // arbitrary positive integer templateId
        fc.string({ minLength: 1 }),   // arbitrary non-empty name
        fc.string(),                    // arbitrary icon string
        (templateId, name, icon) => {
          const { result } = renderHook(() => useDrag(), { wrapper });

          // Initiate drag with arbitrary template data
          act(() => {
            result.current.startDrag({ templateId, name, icon });
          });

          // Assert dragState.templateId equals the input templateId exactly
          expect(result.current.dragState).not.toBeNull();
          expect(result.current.dragState!.templateId).toBe(templateId);
          expect(result.current.dragState!.name).toBe(name);
          expect(result.current.dragState!.icon).toBe(icon);
        }
      ),
      { numRuns: 100 }
    );
  });
});
