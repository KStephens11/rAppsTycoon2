# Implementation Plan: rApp Tycoon Frontend UI

## Overview

This implementation plan breaks down the rApp Tycoon Frontend UI into atomic, <10 minute tasks organized by category: Setup & Boilerplate, Core Infrastructure, Pages & Routing, UI Components, and Refactoring. All tasks focus on implementation only—testing will be addressed in a later phase.

## Phase 1: Setup & Boilerplate

- [x] 1.1 Initialize React project with Vite
  - Create new Vite project with React template
  - _Requirements: 1.0_

- [x] 1.2 Install core dependencies
  - Install React, React Router, TypeScript, Axios, STOMP client
  - _Requirements: 1.0_

- [ ] 1.3 Install UI and visualization libraries
  - Install styled-components, Three.js, Recharts
  - _Requirements: 4.0, 6.0_

- [ ] 1.4 Configure TypeScript and ESLint
  - Set up tsconfig.json, ESLint rules, Prettier formatting
  - _Requirements: 1.0_

- [ ] 1.5 Set up environment variables
  - Create .env files for dev/prod, configure API base URL and WebSocket URL
  - _Requirements: 1.0_

- [ ] 1.6 Create project folder structure
  - Create directories: src/pages, src/components, src/contexts, src/hooks, src/utils, src/types, src/styles
  - _Requirements: 1.0_

- [ ] 1.7 Initialize Git repository
  - Create .gitignore, make initial commit
  - _Requirements: 1.0_

## Phase 2: Core Infrastructure

- [ ] 2.1 Create TypeScript type definitions
  - Define all interfaces from design document (GameSession, Player, Basestation, Metrics, RAppTemplate, etc.)
  - _Requirements: 1.0, 2.0, 3.0_

- [ ] 2.2 Create GameStateContext and reducer
  - Implement context for session, players, basestations, metrics, leaderboard, events
  - _Requirements: 3.0_

- [ ] 2.3 Create UIStateContext and reducer
  - Implement context for selected region, basestation, dialogs, loading, errors
  - _Requirements: 3.0_

- [ ] 2.4 Create WebSocketContext and provider
  - Implement context for connection status, subscriptions, reconnection logic
  - _Requirements: 11.0_

- [ ] 2.5 Create Axios HTTP client with interceptors
  - Set up base URL, request/response interceptors, token injection, error handling
  - _Requirements: 1.0, 12.0_

- [ ] 2.6 Implement useWebSocket custom hook
  - Handle connection, subscription, message parsing, reconnection with exponential backoff
  - _Requirements: 11.0_

- [ ] 2.7 Implement useAPI custom hook
  - Wrapper for Axios calls with error handling and loading states
  - _Requirements: 1.0, 12.0_

- [ ] 2.8 Implement useResponsiveLayout custom hook
  - Detect viewport size, return layout type (desktop/tablet/mobile)
  - _Requirements: 14.0_

- [ ] 2.9 Create utility functions
  - Implement validation (displayName, sessionCode), color coding (health status), metric calculations
  - _Requirements: 1.0, 4.0, 6.0_

- [ ] 2.10 Create ErrorBoundary component
  - Catch React errors, display fallback UI, log errors
  - _Requirements: 12.0_

## Phase 3: Pages & Routing

- [ ] 3.1 Set up React Router with route definitions
  - Create router configuration with LobbyPage, LobbyWaitingPage, GameBoardPage, GameResultsPage
  - _Requirements: 1.0, 2.0, 3.0_

- [ ] 3.2 Create LobbyPage layout
  - Implement two-column layout with CreateSessionForm and JoinSessionForm
  - _Requirements: 1.0_

- [ ] 3.3 Create LobbyWaitingPage layout
  - Implement session code display, player list, start button, leave button
  - _Requirements: 2.0_

- [ ] 3.4 Create GameBoardPage layout
  - Implement multi-panel grid layout (left sidebar, center map, right panels, bottom panels)
  - _Requirements: 3.0_

- [ ] 3.5 Create GameResultsPage layout
  - Implement final leaderboard, winner display, share button, return to lobby button
  - _Requirements: 13.0_

- [ ] 3.6 Implement page transitions and navigation
  - Add route guards, loading states, error handling for page navigation
  - _Requirements: 1.0, 2.0, 3.0_

## Phase 4: Lobby Features

- [ ] 4.1 Implement CreateSessionForm component
  - Form with display name input, submit button, validation, API call
  - _Requirements: 1.0_

- [ ] 4.2 Implement JoinSessionForm component
  - Form with session code and display name inputs, submit button, validation, API call
  - _Requirements: 1.0_

- [ ] 4.3 Implement form validation logic
  - Client-side validation for display name (1-50 chars), session code (8 chars)
  - _Requirements: 1.0_

- [ ] 4.4 Implement session code display and copy-to-clipboard
  - Display session code prominently, add copy button
  - _Requirements: 2.0_

- [ ] 4.5 Implement PlayerList component
  - Display all players in lobby with names, host indicator
  - _Requirements: 2.0_

- [ ] 4.6 Implement real-time player list updates
  - Subscribe to WebSocket updates, add/remove players from list
  - _Requirements: 2.0, 11.0_

- [ ] 4.7 Implement StartGameButton component
  - Button enabled only when ≥2 players, disabled for non-host, API call on click
  - _Requirements: 2.0_

- [ ] 4.8 Implement LeaveSessionButton component
  - Button to return to lobby, clear session data
  - _Requirements: 2.0_

## Phase 5: Game Board Layout

- [ ] 5.1 Implement responsive grid layout system
  - Create CSS Grid layout for desktop (1920px+), tablet (768-1024px)
  - _Requirements: 3.0, 14.0_

- [ ] 5.2 Implement GameHeader component
  - Display player name, money, game timer in top-left corner
  - _Requirements: 3.0_

- [ ] 5.3 Implement game timer display
  - Show elapsed time and remaining time, update every second
  - _Requirements: 3.0_

- [ ] 5.4 Implement RegionSelector component
  - Buttons for regions A, B, C, D with visual indicators, click handler
  - _Requirements: 10.0_

- [ ] 5.5 Implement ActionButtons component
  - Deploy, Tune, Disable, Rollback buttons with enable/disable logic
  - _Requirements: 9.0_

- [ ] 5.6 Implement panel containers and spacing
  - Create styled containers for sidebar, center, right panels with proper spacing
  - _Requirements: 3.0_

## Phase 6: 3D Map Visualization

- [ ] 6.1 Set up Three.js scene and camera
  - Initialize scene, isometric camera, lighting, renderer
  - _Requirements: 4.0_

- [ ] 6.2 Implement isometric camera and controls
  - Set up camera position for isometric view, implement zoom and pan controls
  - _Requirements: 4.0_

- [ ] 6.3 Create Basestation 3D model/icon
  - Design or import 3D tower model, create material and geometry
  - _Requirements: 4.0_

- [ ] 6.4 Implement basestation rendering
  - Render all basestations on map with position data
  - _Requirements: 4.0_

- [ ] 6.5 Implement health color coding for basestations
  - Apply color based on health metric (green ≥80, yellow 50-79, red <50)
  - _Requirements: 4.0_

- [ ] 6.6 Implement basestation selection and highlighting
  - Click handler to select basestation, visual highlight on selection
  - _Requirements: 4.0_

- [ ] 6.7 Implement map zoom and pan controls
  - Mouse wheel zoom, drag to pan, keyboard shortcuts
  - _Requirements: 4.0_

- [ ] 6.8 Implement event indicator animations
  - Animated alert icon or pulsing border for basestations with active events
  - _Requirements: 4.0_

- [ ] 6.9 Implement map legend
  - Display color coding explanation and visual indicators
  - _Requirements: 4.0_

- [ ] 6.10 Implement region filtering on map
  - Show only basestations in selected region, smooth transition
  - _Requirements: 10.0_

## Phase 7: rApp Catalog

- [ ] 7.1 Implement rAppCatalog component
  - Container for rApp list, filtering controls, detail view
  - _Requirements: 5.0_

- [ ] 7.2 Implement rApp list display
  - Display all rApps with name, purpose, cost, benefit, risk, confidence
  - _Requirements: 5.0_

- [ ] 7.3 Implement rApp filtering by cost
  - Add cost range slider, filter list dynamically
  - _Requirements: 5.0_

- [ ] 7.4 Implement rApp filtering by risk
  - Add risk level filter, update list on selection
  - _Requirements: 5.0_

- [ ] 7.5 Implement rApp detail view
  - Display full Impact object with all metric effects
  - _Requirements: 5.0_

- [ ] 7.6 Implement deploy button integration
  - Deploy button on rApp, opens confirmation dialog
  - _Requirements: 5.0_

- [ ] 7.7 Implement rApp status indicators
  - Display status badges (Active, Deployed, Unlocked, Locked)
  - _Requirements: 5.0_

- [ ] 7.8 Implement loading state for rApp catalog
  - Show skeleton loaders while fetching from API
  - _Requirements: 5.0_

## Phase 8: Metrics Dashboard

- [ ] 8.1 Implement NetworkMetricsDashboard component
  - Container for six metric sparklines with layout
  - _Requirements: 6.0_

- [ ] 8.2 Implement MetricSparkline component
  - Render single sparkline chart with Recharts
  - _Requirements: 6.0_

- [ ] 8.3 Implement metric value display
  - Show current value prominently (e.g., "85.5%")
  - _Requirements: 6.0_

- [ ] 8.4 Implement metric trend indicators
  - Display up/down arrows based on trend
  - _Requirements: 6.0_

- [ ] 8.5 Implement metric color coding
  - Apply colors based on metric value (green ≥80, yellow 50-79, red <50)
  - _Requirements: 6.0_

- [ ] 8.6 Implement metric tooltips
  - Show metric value at specific point on hover
  - _Requirements: 6.0_

- [ ] 8.7 Implement metrics history tracking
  - Store last 60 seconds / 12 ticks of metric data
  - _Requirements: 6.0_

- [ ] 8.8 Implement average metrics calculation
  - Calculate and display average metrics across all player basestations
  - _Requirements: 6.0_

- [ ] 8.9 Implement smooth sparkline updates
  - Update charts without flickering or jarring transitions
  - _Requirements: 6.0_

- [ ] 8.10 Implement basestation name display
  - Show selected basestation name and position at top of panel
  - _Requirements: 6.0_

## Phase 9: Leaderboard

- [ ] 9.1 Implement SessionScoreboard component
  - Container for leaderboard entries with scrollable area
  - _Requirements: 7.0_

- [ ] 9.2 Implement leaderboard entry display
  - Show rank, player name, composite score, score components
  - _Requirements: 7.0_

- [ ] 9.3 Implement rank change animations
  - Animate rank changes with visual indicators (arrows, color changes)
  - _Requirements: 7.0_

- [ ] 9.4 Implement score component breakdown
  - Display money, customer satisfaction, network stability separately
  - _Requirements: 7.0_

- [ ] 9.5 Implement real-time leaderboard updates
  - Subscribe to LEADERBOARD_UPDATED messages, update display
  - _Requirements: 7.0, 11.0_

- [ ] 9.6 Implement scrollable leaderboard container
  - Add scrollbar for long player lists, maintain consistent layout
  - _Requirements: 7.0_

- [ ] 9.7 Implement current player highlighting
  - Highlight current player's entry in leaderboard
  - _Requirements: 7.0_

## Phase 10: Event Feed

- [ ] 10.1 Implement LiveIncidentFeed component
  - Container for event list with filtering and scrolling
  - _Requirements: 8.0_

- [ ] 10.2 Implement EventNotification component
  - Display single event with type, severity, basestation, description, timestamp
  - _Requirements: 8.0_

- [ ] 10.3 Implement severity color coding
  - Apply colors (green LOW, yellow MEDIUM, orange HIGH, red CRITICAL)
  - _Requirements: 8.0_

- [ ] 10.4 Implement event filtering by severity
  - Add filter buttons, update event list dynamically
  - _Requirements: 8.0_

- [ ] 10.5 Implement event detail display
  - Show full event description on click
  - _Requirements: 8.0_

- [ ] 10.6 Implement event timestamp formatting
  - Display relative time (e.g., "2 minutes ago")
  - _Requirements: 8.0_

- [ ] 10.7 Implement event count display
  - Show count of unresolved events in feed header
  - _Requirements: 8.0_

- [ ] 10.8 Implement reverse chronological ordering
  - Display newest events first
  - _Requirements: 8.0_

- [ ] 10.9 Implement event history limit
  - Show last 20 events, allow scrolling for older events
  - _Requirements: 8.0_

- [ ] 10.10 Implement basestation highlighting on event click
  - Highlight affected basestation on map when event is clicked
  - _Requirements: 8.0_

## Phase 11: Player Actions

- [ ] 11.1 Implement DeploymentConfirmationDialog component
  - Display cost, expected impacts, confirm/cancel buttons
  - _Requirements: 9.0_

- [ ] 11.2 Implement deploy action handler
  - Call API, handle success/error, update UI
  - _Requirements: 9.0_

- [ ] 11.3 Implement TuneConfigurationDialog component
  - Threshold slider (1-100), aggressiveness dropdown, confirm/cancel buttons
  - _Requirements: 9.0_

- [ ] 11.4 Implement tune action handler
  - Call API with configuration, handle success/error, update UI
  - _Requirements: 9.0_

- [ ] 11.5 Implement disable action handler
  - Show confirmation, call API, update rApp status
  - _Requirements: 9.0_

- [ ] 11.6 Implement rollback action handler
  - Show previous version info, call API, update rApp status
  - _Requirements: 9.0_

- [ ] 11.7 Implement action button enable/disable logic
  - Disable buttons during transitional states (DEPLOYING, ROLLING_BACK)
  - _Requirements: 9.0_

- [ ] 11.8 Implement deployed rApp list display
  - Show all rApps deployed on selected basestation with status
  - _Requirements: 9.0_

## Phase 12: WebSocket Integration

- [ ] 12.1 Implement WebSocket connection establishment
  - Connect to ws://localhost:8080/ws/game with token in headers
  - _Requirements: 11.0_

- [ ] 12.2 Implement STOMP subscription logic
  - Subscribe to all required destinations (/topic/session/{code}/game, etc.)
  - _Requirements: 11.0_

- [ ] 12.3 Implement GAME_STARTED message handler
  - Display notification, update game state
  - _Requirements: 11.0_

- [ ] 12.4 Implement GAME_ENDED message handler
  - Navigate to GameResultsPage, display final leaderboard
  - _Requirements: 11.0_

- [ ] 12.5 Implement EVENT_OCCURRED message handler
  - Add event to feed, display notification, update basestation indicators
  - _Requirements: 11.0_

- [ ] 12.6 Implement METRICS_UPDATED message handler
  - Update metrics in state, refresh sparklines and map indicators
  - _Requirements: 11.0_

- [ ] 12.7 Implement LEADERBOARD_UPDATED message handler
  - Update leaderboard in state, refresh display
  - _Requirements: 11.0_

- [ ] 12.8 Implement RAPP_STATUS_CHANGED message handler
  - Update rApp status in state, refresh deployed rApp list
  - _Requirements: 11.0_

- [ ] 12.9 Implement reconnection logic with exponential backoff
  - Detect disconnection, attempt reconnection (1s, 2s, 4s, 8s, max 30s)
  - _Requirements: 11.0_

- [ ] 12.10 Implement message validation
  - Validate incoming messages against schema, discard malformed messages
  - _Requirements: 11.0_

- [ ] 12.11 Implement connection status banner
  - Display "Connection Lost" or "Reconnecting" message
  - _Requirements: 11.0_

## Phase 13: Error Handling & User Feedback

- [ ] 13.1 Implement Toast/Notification system
  - Create reusable notification component with success/error/warning/info types
  - _Requirements: 12.0_

- [ ] 13.2 Implement error message display
  - Display user-friendly error messages from API responses
  - _Requirements: 12.0_

- [ ] 13.3 Implement loading spinners
  - Create reusable spinner component for async operations
  - _Requirements: 12.0_

- [ ] 13.4 Implement skeleton loaders
  - Create skeleton components for async content (catalog, leaderboard, metrics)
  - _Requirements: 12.0_

- [ ] 13.5 Implement retry logic for failed operations
  - Add retry button to error messages, implement exponential backoff
  - _Requirements: 12.0_

- [ ] 13.6 Implement validation error display
  - Show field-level validation errors next to form inputs
  - _Requirements: 1.0, 12.0_

- [ ] 13.7 Implement session error handling
  - Handle SESSION_NOT_FOUND, SESSION_FULL, INVALID_STATE errors
  - _Requirements: 1.0, 2.0, 12.0_

- [ ] 13.8 Implement authorization error handling
  - Handle UNAUTHORIZED errors, clear token, redirect to lobby
  - _Requirements: 12.0_

- [ ] 13.9 Implement timeout error handling
  - Handle 30s timeout, display message, offer retry
  - _Requirements: 12.0_

- [ ] 13.10 Implement server error handling
  - Handle 500 errors, display generic message, log error
  - _Requirements: 12.0_

## Phase 14: Session Persistence

- [ ] 14.1 Implement session token storage
  - Store token in sessionStorage on login
  - _Requirements: 17.0_

- [ ] 14.2 Implement token validation on app load
  - Check token validity by calling GET /api/sessions/{code}
  - _Requirements: 17.0_

- [ ] 14.3 Implement session restoration
  - Restore session from stored token/code on page reload
  - _Requirements: 17.0_

- [ ] 14.4 Implement token clearing on logout
  - Clear stored token and session code
  - _Requirements: 17.0_

- [ ] 14.5 Implement session expiration handling
  - Detect expired sessions, display message, redirect to lobby
  - _Requirements: 17.0_

## Phase 15: Styling & Theme

- [ ] 15.1 Set up styled-components
  - Configure styled-components, create global styles
  - _Requirements: 18.0_

- [ ] 15.2 Create global styles and theme
  - Define color palette, typography, spacing system
  - _Requirements: 18.0_

- [ ] 15.3 Implement 5G network color scheme
  - Use blues, purples, teals for network theme
  - _Requirements: 18.0_

- [ ] 15.4 Implement typography system
  - Define font sizes, weights, line heights
  - _Requirements: 18.0_

- [ ] 15.5 Implement component styling
  - Style all components with consistent design system
  - _Requirements: 18.0_

- [ ] 15.6 Implement responsive breakpoints
  - Define breakpoints for desktop, tablet, mobile
  - _Requirements: 14.0, 18.0_

- [ ] 15.7 Implement dark mode support (optional)
  - Create dark theme variant, add toggle
  - _Requirements: 18.0_

## Phase 16: Accessibility

- [ ] 16.1 Implement keyboard navigation
  - Add Tab, Enter, Space, Arrow key support to all interactive elements
  - _Requirements: 15.0_

- [ ] 16.2 Add ARIA labels and roles
  - Add aria-label, aria-role, aria-live to components
  - _Requirements: 15.0_

- [ ] 16.3 Implement focus indicators
  - Add visible focus outlines to all interactive elements
  - _Requirements: 15.0_

- [ ] 16.4 Add alt text to images and icons
  - Add alt attributes to all images, aria-labels to icons
  - _Requirements: 15.0_

- [ ] 16.5 Implement semantic HTML
  - Use button, link, heading, list elements instead of divs
  - _Requirements: 15.0_

- [ ] 16.6 Implement screen reader support
  - Add aria-live regions for real-time updates, announce state changes
  - _Requirements: 15.0_

- [ ] 16.7 Ensure color is not only means of conveying information
  - Add icons, text, or patterns in addition to color
  - _Requirements: 15.0_

## Phase 17: Performance Optimization

- [ ] 17.1 Implement route-based code splitting
  - Use React.lazy() for page components
  - _Requirements: 16.0_

- [ ] 17.2 Implement React.memo for expensive components
  - Memoize RegionMap, NetworkMetricsDashboard, SessionScoreboard, LiveIncidentFeed
  - _Requirements: 16.0_

- [ ] 17.3 Implement useMemo for expensive computations
  - Memoize filtered basestation lists, leaderboard sorting, metric calculations
  - _Requirements: 16.0_

- [ ] 17.4 Implement useCallback for event handlers
  - Memoize deployment, region selection, dialog handlers
  - _Requirements: 16.0_

- [ ] 17.5 Implement debouncing for resize events
  - Debounce window resize handler for layout recalculation
  - _Requirements: 16.0_

- [ ] 17.6 Implement throttling for WebSocket updates
  - Throttle metrics updates to max 1 per 100ms
  - _Requirements: 16.0_

- [ ] 17.7 Implement lazy loading for heavy libraries
  - Lazy load Three.js, Recharts only when needed
  - _Requirements: 16.0_

- [ ] 17.8 Implement bundle analysis
  - Run webpack-bundle-analyzer, identify large dependencies
  - _Requirements: 16.0_

- [ ] 17.9 Implement production build optimization
  - Enable minification, tree-shaking, gzip compression
  - _Requirements: 16.0_

## Phase 18: Responsive Design

- [ ] 18.1 Implement responsive layout for tablet (768-1024px)
  - Stack panels vertically or use tabbed interface
  - _Requirements: 14.0_

- [ ] 18.2 Implement responsive font sizes
  - Scale font sizes based on viewport width
  - _Requirements: 14.0_

- [ ] 18.3 Implement touch-friendly button sizes
  - Ensure buttons are at least 44×44 pixels
  - _Requirements: 14.0_

- [ ] 18.4 Implement orientation support
  - Support landscape and portrait orientations
  - _Requirements: 14.0_

- [ ] 18.5 Test responsive design on multiple devices
  - Verify layout on iPad, iPad Pro, Android tablets
  - _Requirements: 14.0_

## Phase 19: Game Results

- [ ] 19.1 Implement GameResultsPage layout
  - Create layout for final leaderboard, winner display, buttons
  - _Requirements: 13.0_

- [ ] 19.2 Implement final leaderboard display
  - Display all players ranked by composite score
  - _Requirements: 13.0_

- [ ] 19.3 Implement winner display
  - Show winner's name and final score prominently
  - _Requirements: 13.0_

- [ ] 19.4 Implement game statistics summary
  - Display total events, total rApps deployed, average metrics
  - _Requirements: 13.0_

- [ ] 19.5 Implement share results button
  - Add button to copy results to clipboard or share
  - _Requirements: 13.0_

- [ ] 19.6 Implement return to lobby button
  - Button to navigate back to LobbyPage
  - _Requirements: 13.0_

## Phase 20: Refactoring & Cleanup

- [ ] 20.1 Remove unused dependencies
  - Audit package.json, remove unused packages
  - _Requirements: 16.0_

- [ ] 20.2 Refactor large components into smaller pieces
  - Break down GameBoardPage, rAppCatalog, NetworkMetricsDashboard
  - _Requirements: 16.0_

- [ ] 20.3 Extract reusable logic into custom hooks
  - Create hooks for common patterns (useMetrics, useRApps, useEvents)
  - _Requirements: 16.0_

- [ ] 20.4 Optimize bundle size
  - Remove dead code, optimize imports, analyze bundle
  - _Requirements: 16.0_

- [ ] 20.5 Clean up console logs and debug code
  - Remove all console.log, debug statements
  - _Requirements: 16.0_

- [ ] 20.6 Add code comments and documentation
  - Document complex logic, add JSDoc comments
  - _Requirements: 16.0_

- [ ] 20.7 Run linting and fix issues
  - Run ESLint, fix all warnings and errors
  - _Requirements: 16.0_

- [ ] 20.8 Final code review and cleanup
  - Review all code, ensure consistency, fix any remaining issues
  - _Requirements: 16.0_

## Notes

- All tasks focus on implementation only—testing will be addressed in a later phase
- Each task is designed to be completable in <10 minutes
- Tasks are organized by category and can be executed in parallel within each phase
- Core infrastructure tasks (Phase 2) should be completed before feature tasks
- Styling and accessibility can be integrated throughout implementation
- Performance optimization should be done after core functionality is complete
- Responsive design should be tested continuously during development



## Task Dependency Graph

```json
{
  "waves": [
    {
      "id": 0,
      "tasks": ["1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7"]
    },
    {
      "id": 1,
      "tasks": ["2.1", "2.2", "2.3", "2.4", "2.5", "2.6", "2.7", "2.8", "2.9", "2.10"]
    },
    {
      "id": 2,
      "tasks": ["3.1", "3.2", "3.3", "3.4", "3.5"]
    },
    {
      "id": 3,
      "tasks": ["4.1", "4.2", "4.3", "4.4", "4.5", "4.6", "4.7", "4.8"]
    },
    {
      "id": 4,
      "tasks": ["5.1", "5.2", "5.3", "5.4", "5.5", "5.6"]
    },
    {
      "id": 5,
      "tasks": ["6.1", "6.2", "6.3", "6.4", "6.5", "6.6", "6.7", "6.8", "6.9", "6.10"]
    },
    {
      "id": 6,
      "tasks": ["7.1", "7.2", "7.3", "7.4", "7.5", "7.6", "7.7", "7.8"]
    },
    {
      "id": 7,
      "tasks": ["8.1", "8.2", "8.3", "8.4", "8.5", "8.6", "8.7", "8.8", "8.9", "8.10"]
    },
    {
      "id": 8,
      "tasks": ["9.1", "9.2", "9.3", "9.4", "9.5", "9.6", "9.7"]
    },
    {
      "id": 9,
      "tasks": ["10.1", "10.2", "10.3", "10.4", "10.5", "10.6", "10.7", "10.8", "10.9", "10.10"]
    },
    {
      "id": 10,
      "tasks": ["11.1", "11.2", "11.3", "11.4", "11.5", "11.6", "11.7", "11.8"]
    },
    {
      "id": 11,
      "tasks": ["12.1", "12.2", "12.3", "12.4", "12.5", "12.6", "12.7", "12.8", "12.9", "12.10", "12.11"]
    },
    {
      "id": 12,
      "tasks": ["13.1", "13.2", "13.3", "13.4", "13.5", "13.6", "13.7", "13.8", "13.9", "13.10"]
    },
    {
      "id": 13,
      "tasks": ["14.1", "14.2", "14.3", "14.4", "14.5"]
    },
    {
      "id": 14,
      "tasks": ["15.1", "15.2", "15.3", "15.4", "15.5", "15.6", "15.7"]
    },
    {
      "id": 15,
      "tasks": ["16.1", "16.2", "16.3", "16.4", "16.5", "16.6", "16.7"]
    },
    {
      "id": 16,
      "tasks": ["17.1", "17.2", "17.3", "17.4", "17.5", "17.6", "17.7", "17.8", "17.9"]
    },
    {
      "id": 17,
      "tasks": ["18.1", "18.2", "18.3", "18.4", "18.5"]
    },
    {
      "id": 18,
      "tasks": ["19.1", "19.2", "19.3", "19.4", "19.5", "19.6"]
    },
    {
      "id": 19,
      "tasks": ["20.1", "20.2", "20.3", "20.4", "20.5", "20.6", "20.7", "20.8"]
    }
  ]
}
```

## Execution Strategy

**Wave Execution Order:**
- Waves 0-1: Setup and infrastructure (required before any feature work)
- Waves 2-5: Core pages and layout (foundation for all UI)
- Waves 6-10: Feature components (can be parallelized within wave)
- Wave 11: WebSocket integration (enables real-time updates)
- Waves 12-14: Error handling, persistence, styling (cross-cutting concerns)
- Waves 15-19: Accessibility, performance, responsive design, results, refactoring

**Parallelization:**
- Within each wave, tasks can be executed in parallel by different developers
- Tasks in different waves have dependencies and must be executed sequentially
- Example: Wave 6 (rApp Catalog) can start once Wave 5 (3D Map) is complete

**Estimated Timeline:**
- Setup & Infrastructure: 1-2 hours
- Core Pages & Layout: 2-3 hours
- Feature Components: 4-6 hours
- WebSocket Integration: 1-2 hours
- Error Handling & Styling: 2-3 hours
- Accessibility & Performance: 2-3 hours
- Responsive Design & Results: 1-2 hours
- Refactoring & Cleanup: 1-2 hours
- **Total: 14-23 hours** (depending on team size and parallelization)

