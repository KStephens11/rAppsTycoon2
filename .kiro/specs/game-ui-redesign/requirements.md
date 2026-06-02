# Requirements Document

## Introduction

This feature redesigns the Game Page UI for rApp Tycoon to improve usability and immersion. The two core changes are:

1. **Drag-and-drop deployment** — Players deploy rApps by dragging them from the catalogue directly onto a basestation on the 3D isometric map, replacing the current modal-based workflow.
2. **Always-visible panels** — The rApp Catalogue, Events panel, and Leaderboard are displayed simultaneously in the sidebar, eliminating the tab-based navigation that hides content behind buttons.

## Glossary

- **Game_Page**: The main gameplay view containing the 3D map and surrounding panels.
- **Drag_Source**: A draggable rApp card element within the rApp Catalogue that initiates a drag-and-drop deployment.
- **Drop_Target**: A basestation element on the 3D isometric map that accepts a dragged rApp for deployment.
- **Drag_Preview**: A visual representation of the rApp being dragged, shown attached to the cursor during a drag operation.
- **Drop_Indicator**: A visual highlight applied to a basestation when a dragged rApp hovers over it, signalling it can accept the drop.
- **RApp_Catalogue**: The panel listing available rApp templates that players can deploy, positioned alongside the map.
- **Event_Panel**: The panel displaying currently active game events with severity and escalation information.
- **Leaderboard_Panel**: The panel showing player rankings and scores in real time.
- **Isometric_Map**: The React Three Fiber 3D canvas displaying basestations and their status.
- **Basestation_Popover**: A floating detail panel that appears near a selected basestation showing its metrics, deployed rApps, and active events.
- **Deploy_API**: The backend endpoint POST /api/sessions/{code}/rapps/deploy accepting { templateId, basestationId }.

## Requirements

### Requirement 1: Drag-and-Drop Initiation

**User Story:** As a player, I want to drag an rApp from the catalogue, so that I can deploy it to a basestation without navigating through modal dialogs.

#### Acceptance Criteria

1. WHEN a player initiates a drag gesture on a Drag_Source in the RApp_Catalogue, THE Game_Page SHALL begin a drag operation carrying the rApp template identifier.
2. WHILE a drag operation is in progress, THE Game_Page SHALL display a Drag_Preview attached to the cursor showing the rApp name and icon.
3. WHILE a drag operation is in progress, THE RApp_Catalogue SHALL visually dim the originating Drag_Source to indicate it is being dragged.
4. WHEN a player releases the drag outside a valid Drop_Target, THE Game_Page SHALL cancel the drag operation and restore the Drag_Source to its original visual state.

### Requirement 2: Drop Target Feedback on Basestations

**User Story:** As a player, I want basestations to visually indicate when I can drop an rApp on them, so that I know where deployment is possible.

#### Acceptance Criteria

1. WHILE a drag operation is in progress and the cursor hovers over a Drop_Target on the Isometric_Map, THE Drop_Target SHALL display a Drop_Indicator highlighting the basestation.
2. WHILE a drag operation is in progress and the cursor is not over any Drop_Target, THE Isometric_Map SHALL display no Drop_Indicator on any basestation.
3. WHEN a drag operation begins, THE Isometric_Map SHALL indicate all valid Drop_Targets with a subtle visual cue distinguishing them from non-interactive elements.

### Requirement 3: Deploy on Drop

**User Story:** As a player, I want dropping an rApp on a basestation to trigger deployment, so that the interaction is fast and intuitive.

#### Acceptance Criteria

1. WHEN a player drops a Drag_Source onto a valid Drop_Target, THE Game_Page SHALL call the Deploy_API with the corresponding templateId and basestationId.
2. WHEN the Deploy_API returns a success response, THE Game_Page SHALL display a success toast notification and play the deploy sound effect.
3. WHEN the Deploy_API returns a success response, THE Game_Page SHALL refresh the basestation data to reflect the new deployment.
4. IF the Deploy_API returns an error response, THEN THE Game_Page SHALL display an error toast notification with the error message and restore the Drag_Source to its original state.

### Requirement 4: Simultaneous Panel Layout

**User Story:** As a player, I want to see the rApp Catalogue, Events, and Leaderboard all at once, so that I have full situational awareness without switching tabs.

#### Acceptance Criteria

1. THE Game_Page SHALL display the RApp_Catalogue, Event_Panel, and Leaderboard_Panel simultaneously without tab navigation.
2. THE Game_Page SHALL remove all tab navigation buttons previously used to switch between panels.
3. THE Game_Page SHALL arrange the panels around the Isometric_Map so that each panel is visible without requiring scrolling under normal content volumes (up to 7 catalogue items, up to 5 events, up to 8 leaderboard entries).
4. THE RApp_Catalogue SHALL be positioned to the right of the Isometric_Map as a compact vertical list optimised for drag initiation.
5. THE Event_Panel SHALL be positioned below the Isometric_Map or in a bottom bar area.
6. THE Leaderboard_Panel SHALL be positioned in a corner or edge area that does not overlap the primary map interaction zone.

### Requirement 5: Responsive Panel Behaviour

**User Story:** As a player on a smaller screen, I want the panels to remain usable, so that I can still access all information without excessive scrolling.

#### Acceptance Criteria

1. WHILE the viewport width is below the medium breakpoint (768px), THE Game_Page SHALL reposition the panels into a mobile-friendly layout using the bottom sheet and overlay areas.
2. WHILE the viewport width is below the medium breakpoint, THE Game_Page SHALL display the RApp_Catalogue in the bottom sheet as a horizontally scrollable strip for drag initiation.
3. THE Event_Panel and Leaderboard_Panel SHALL remain accessible on mobile via compact overlay indicators that expand on tap.

### Requirement 6: Keyboard Accessibility for Drag-and-Drop

**User Story:** As a player using keyboard navigation, I want an alternative way to deploy rApps, so that the feature is accessible without a pointing device.

#### Acceptance Criteria

1. WHEN a player activates a Drag_Source using the Enter or Space key, THE Game_Page SHALL open a lightweight deployment picker listing available basestations.
2. WHEN a player selects a basestation from the deployment picker and confirms, THE Game_Page SHALL call the Deploy_API with the corresponding templateId and basestationId.
3. THE deployment picker SHALL be navigable using arrow keys and dismissible using the Escape key.

### Requirement 7: Basestation Detail Popover

**User Story:** As a player, I want to see basestation details as a popover on the map when I click a basestation, so that I can manage deployed rApps without losing sight of the other panels.

#### Acceptance Criteria

1. WHEN a player clicks a basestation on the Isometric_Map, THE Game_Page SHALL display a floating popover panel near the selected basestation showing its detail view (metrics, deployed rApps, active events).
2. THE popover SHALL include controls to tune, disable, and rollback deployed rApps.
3. WHEN a player clicks outside the popover or presses Escape, THE Game_Page SHALL close the popover.
4. THE popover SHALL not obscure the RApp_Catalogue panel, so that drag-and-drop deployment remains possible while the popover is open.
