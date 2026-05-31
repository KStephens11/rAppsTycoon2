# Implementation Plan: Game UI Redesign

## Overview

This plan transforms the Game Page from a tabbed sidebar layout into a multi-panel layout with drag-and-drop rApp deployment. Implementation is sliced into incremental steps: first the shared drag context, then the draggable catalogue, then drop detection on basestations, then the new layout, then the popover and keyboard accessibility, and finally mobile responsiveness.

## Tasks

- [x] 1. Create DragContext provider and DragPreview component
  - [x] 1.1 Create `DragContext.tsx` with DragProvider and useDrag hook
    - Create `frontend/src/context/DragContext.tsx`
    - Implement `DragState` interface (`templateId`, `name`, `icon`)
    - Implement `DragContextValue` interface (`dragState`, `hoveredTargetId`, `startDrag`, `setHoveredTarget`, `endDrag`)
    - Export `DragProvider` component wrapping children with context
    - Export `useDrag` hook for consuming the context
    - Include `useEffect` cleanup that resets drag state on unmount
    - _Requirements: 1.1, 1.4, 2.1_

  - [x] 1.2 Create `DragPreview.tsx` component
    - Create `frontend/src/components/game/DragPreview.tsx`
    - Render a `position: fixed` element with `pointer-events: none` that follows cursor position
    - Display rApp name and icon from drag state
    - Track cursor position via `mousemove` / `dragover` events on `document`
    - Only render when `dragState` is non-null
    - _Requirements: 1.2_

  - [x] 1.3 Write property test for drag initiation preserves template identity
    - **Property 1: Drag initiation preserves template identity**
    - Generate arbitrary `templateId` (positive integer), `name` (non-empty string), `icon` (string) → call `startDrag` → assert `dragState.templateId` equals input `templateId`
    - Use `fast-check` with `vitest`
    - **Validates: Requirements 1.1**

  - [x] 1.4 Write property test for drag cancellation resets state
    - **Property 2: Drag cancellation resets state**
    - Generate arbitrary drag states → call `startDrag` → call `endDrag` → assert `dragState` is `null` and no side effects occurred
    - Use `fast-check` with `vitest`
    - **Validates: Requirements 1.4**

- [x] 2. Modify RappCatalogue for drag-and-drop initiation
  - [x] 2.1 Add draggable behaviour to RappCatalogue cards
    - Modify `frontend/src/components/game/RappCatalogue.tsx`
    - Add `draggable` attribute to each rApp card element
    - Add `onDragStart` handler that calls `startDrag` from DragContext with `{ templateId: rapp.id, name: rapp.name, icon: rapp.name }`
    - Set `dataTransfer.setData('text/plain', String(rapp.id))` for native DnD compatibility
    - Set `dataTransfer.effectAllowed = 'move'`
    - Add `onDragEnd` handler that calls `endDrag` from DragContext
    - Apply `opacity-50` class to the card being dragged (track via local state or compare with `dragState.templateId`)
    - Remove the "Deploy" button from each card (deployment is now via drag or keyboard picker)
    - _Requirements: 1.1, 1.3_

  - [x] 2.2 Add keyboard activation for DeploymentPicker
    - Modify the `onKeyDown` handler on rApp cards in `RappCatalogue.tsx`
    - On Enter/Space, instead of calling `onDeploy`, open the `DeploymentPicker` component (to be created in task 5)
    - Pass `templateId`, `templateName`, available basestations, and callbacks
    - _Requirements: 6.1_

  - [x] 2.3 Write unit tests for RappCatalogue drag behaviour
    - Test that `onDragStart` fires with correct template data
    - Test that the source card dims (opacity change) during drag
    - Test that `onDragEnd` resets visual state
    - _Requirements: 1.1, 1.3_

- [x] 3. Checkpoint - Ensure drag context and catalogue compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Add drop detection to BasestationModel and IsometricMap
  - [x] 4.1 Modify `BasestationModel.tsx` to support drop target behaviour
    - Add `isDragActive` prop (boolean) — when true, show a subtle cyan glow/outline on the basestation indicating it's a valid drop target
    - Add `isHoveredTarget` prop (boolean) — when true, show a strong highlight (brighter glow, scale bump) as the drop indicator
    - Add `onPointerEnter` handler — when `isDragActive` is true, call `setHoveredTarget(basestationId)` from DragContext
    - Add `onPointerLeave` handler — when `isDragActive` is true, call `setHoveredTarget(null)`
    - Add `onPointerUp` handler — when `isDragActive` is true, call the `onDrop` callback prop with the basestation ID
    - Add `basestationId` prop to pass the ID for context updates
    - Add `onDrop` callback prop
    - _Requirements: 2.1, 2.2, 2.3, 3.1_

  - [x] 4.2 Modify `IsometricMap.tsx` to consume DragContext and wire drop props
    - Import and consume `useDrag` hook to get `dragState` and `hoveredTargetId`
    - Pass `isDragActive={!!dragState}` to each `BasestationModel`
    - Pass `isHoveredTarget={hoveredTargetId === bs.id}` to each `BasestationModel`
    - Pass `basestationId={bs.id}` and `onDrop` callback to each `BasestationModel`
    - Add `onDrop` prop to `IsometricMapProps` interface
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 4.3 Write property test for drop triggers deploy with correct parameters
    - **Property 3: Drop triggers deploy with correct parameters**
    - Generate arbitrary `(templateId, basestationId)` pairs → simulate drag active with that templateId → trigger drop on that basestationId → assert the deploy handler is called with exactly `{ templateId, basestationId }`
    - Use `fast-check` with `vitest`
    - **Validates: Requirements 3.1**

  - [x] 4.4 Write unit tests for BasestationModel drop detection
    - Test that `onPointerUp` during active drag triggers the drop handler
    - Test that hover indicator shows only when `isDragActive && isHoveredTarget`
    - Test that no drop indicator shows when drag is not active
    - _Requirements: 2.1, 2.2, 3.1_

- [x] 5. Create DeploymentPicker (keyboard accessibility)
  - [x] 5.1 Create `DeploymentPicker.tsx` component
    - Create `frontend/src/components/game/DeploymentPicker.tsx`
    - Implement the `DeploymentPickerProps` interface from the design
    - Render a lightweight dropdown/popover listing available basestations
    - Support arrow key navigation (up/down to move focus between items)
    - Confirm selection with Enter key → call `onSelect(basestationId)`
    - Dismiss with Escape key → call `onClose()`
    - Show "No basestations available" message when list is empty
    - Auto-close after 5s of no interaction
    - Use `role="listbox"` and `role="option"` for accessibility
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 5.2 Write unit tests for DeploymentPicker
    - Test arrow key navigation cycles through options
    - Test Enter confirms selection and calls onSelect
    - Test Escape dismisses and calls onClose
    - Test empty state message renders
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 6. Checkpoint - Ensure drag-and-drop flow and keyboard picker work end-to-end
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement new simultaneous panel layout in GamePage
  - [x] 7.1 Restructure `GamePage.tsx` layout to show all panels simultaneously
    - Remove `activeTab` state and all tab navigation buttons
    - Remove the tab bar UI from both desktop sidebar and mobile bottom sheet
    - Wrap the entire GamePage content in `<DragProvider>`
    - Restructure layout to: flex column with map + right panel in a row, bottom bar below
    - Right panel (`w-72`): stack RappCatalogue on top, Leaderboard below (both always visible)
    - Bottom bar (`h-36`): EventPanel displayed horizontally
    - Map area: `flex-1` taking remaining space
    - Remove `DeployModal` usage entirely (replaced by drag-and-drop + keyboard picker)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [x] 7.2 Wire deploy-on-drop handler in GamePage
    - Add `handleDrop(templateId, basestationId)` callback that calls the Deploy API
    - On success: show success toast, play deploy sound, refresh basestations, call `endDrag()`
    - On error: show error toast with server message, call `endDrag()`
    - Add `isDeploying` state to disable drop targets while a request is in-flight
    - Pass `handleDrop` down through IsometricMap to BasestationModel
    - Render `<DragPreview>` when drag is active
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 7.3 Write property test for error messages propagate to user feedback
    - **Property 4: Error messages propagate to user feedback**
    - Generate arbitrary error message strings → mock Deploy API to return that error → trigger drop → assert the error toast contains the exact error message and drag state is reset to null
    - Use `fast-check` with `vitest`
    - **Validates: Requirements 3.4**

  - [x] 7.4 Write unit tests for simultaneous panel layout
    - Test that RappCatalogue, EventPanel, and Leaderboard all render simultaneously (no tabs)
    - Test that no tab navigation buttons exist in the DOM
    - Test correct positioning classes are applied
    - _Requirements: 4.1, 4.2_

- [x] 8. Create BasestationPopover component
  - [x] 8.1 Create `BasestationPopover.tsx` floating detail panel
    - Create `frontend/src/components/game/BasestationPopover.tsx`
    - Implement `BasestationPopoverProps` interface from the design
    - Position using `position: absolute` within the map container, offset from anchor point
    - Constrain position to not overflow into the right panel area
    - Display basestation metrics, deployed rApps list, and active events
    - Include Tune, Disable, and Rollback controls for each deployed rApp
    - Close on outside click (use `useEffect` with click-outside detection)
    - Close on Escape key press
    - Recalculate anchor position when camera moves (throttled to 10fps)
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 8.2 Integrate BasestationPopover into GamePage
    - Replace the sidebar `BasestationDetail` view with the floating `BasestationPopover`
    - Compute screen coordinates from 3D basestation position using Three.js `Vector3.project()`
    - Pass `onTune`, `onDisable`, `onRollback` callbacks
    - Ensure popover does not block the RappCatalogue panel for drag-and-drop
    - _Requirements: 7.1, 7.4_

  - [x] 8.3 Write unit tests for BasestationPopover
    - Test renders with correct basestation data
    - Test closes on Escape key
    - Test closes on outside click
    - Test does not obscure catalogue panel (check positioning constraints)
    - _Requirements: 7.1, 7.3, 7.4_

- [x] 9. Checkpoint - Ensure full desktop layout with popover works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement mobile responsive layout
  - [x] 10.1 Create `CatalogueStrip.tsx` for mobile bottom sheet
    - Create `frontend/src/components/game/CatalogueStrip.tsx`
    - Implement `CatalogueStripProps` interface from the design
    - Render rApp cards as a horizontally scrollable compact strip
    - Each card is draggable (same drag behaviour as desktop catalogue)
    - Support tap-to-deploy via DeploymentPicker on mobile
    - _Requirements: 5.2_

  - [x] 10.2 Update GamePage and BottomSheet for mobile layout
    - At viewport < 768px: show `CatalogueStrip` in the bottom sheet
    - Add expandable section in bottom sheet for Events and Leaderboard (compact overlay indicators that expand on tap)
    - Map takes full viewport on mobile
    - Popover floats over map on mobile as well
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 10.3 Write unit tests for mobile responsive behaviour
    - Test that bottom sheet renders CatalogueStrip at < 768px viewport
    - Test that Events and Leaderboard are accessible via expandable indicators
    - Test horizontal scroll on CatalogueStrip
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The DragContext bridges HTML5 DnD (DOM) with React Three Fiber (canvas) via shared state
- The existing `DeployModal` is removed entirely in favour of drag-and-drop + keyboard picker

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4"] },
    { "id": 2, "tasks": ["2.1", "5.1"] },
    { "id": 3, "tasks": ["2.2", "2.3", "5.2"] },
    { "id": 4, "tasks": ["4.1"] },
    { "id": 5, "tasks": ["4.2", "4.3", "4.4"] },
    { "id": 6, "tasks": ["7.1"] },
    { "id": 7, "tasks": ["7.2", "7.3", "7.4"] },
    { "id": 8, "tasks": ["8.1"] },
    { "id": 9, "tasks": ["8.2", "8.3"] },
    { "id": 10, "tasks": ["10.1"] },
    { "id": 11, "tasks": ["10.2", "10.3"] }
  ]
}
```
