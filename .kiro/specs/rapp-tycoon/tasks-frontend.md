# Tasks — Frontend (React)

> Each slice is a working, testable increment. Tech stack: React 18+, Vite, TailwindCSS, Framer Motion, React Three Fiber (for 3D isometric map), Lucide React (icons), STOMP.js (WebSocket).

---

## Slice 1: Project Setup & Design System

**Goal:** Runnable React app with design tokens, layout shell, and routing.

- [x] 1.1 Initialise Vite + React + TypeScript project in `frontend/`
- [x] 1.2 Install dependencies: tailwindcss, framer-motion, lucide-react, react-router-dom, @stomp/stompjs, sockjs-client, @react-three/fiber, @react-three/drei
- [x] 1.3 Configure Tailwind with custom colour palette (dark theme: slate-900 base, cyan/emerald/amber accents — no boring greys)
- [x] 1.4 Create design tokens: colours, spacing, typography (Inter font), border-radius, shadows
- [x] 1.5 Create `AppLayout` component with sidebar navigation shell (icons from Lucide: Home, Map, Layers, BarChart3, Settings)
- [x] 1.6 Set up React Router with routes: `/`, `/lobby`, `/game`, `/results`
- [x] 1.7 Create reusable UI components: Button, Card, Badge, ProgressBar, Modal, Tooltip (all with Framer Motion enter/exit animations)
- [x] 1.8 Create `api.ts` service module with base URL config and typed fetch helpers
- [x] 1.9 Create Dockerfile for frontend (Nginx serving built assets)
- [x] 1.10 Verify: app runs locally with `npm run dev`, shows layout shell with navigation

---

## Slice 2: Lobby — Create & Join Sessions

**Goal:** Players can create a session, share the code, and join. Fully functional against the backend.

- [x] 2.1 Create `LobbyPage` with two panels: "Create Game" and "Join Game"
- [x] 2.2 Implement "Create Game" form: display name input, submit calls `POST /api/sessions`, stores token in context
- [x] 2.3 Implement "Join Game" form: session code input (8-char, uppercase), display name, calls `POST /api/sessions/{code}/join`
- [x] 2.4 Create `WaitingRoom` component: shows session code (large, copyable), player list with host badge, player count (e.g., "3/6")
- [x] 2.5 Add "Start Game" button (visible only to host), calls `POST /api/sessions/{code}/start`
- [x] 2.6 Implement `GameContext` provider: stores sessionCode, token, playerId, isHost, gameState
- [x] 2.7 Add polling for session state (every 2s while in lobby) to detect new players joining and game start
- [x] 2.8 Add animated transitions between lobby states (Framer Motion: fade/slide)
- [x] 2.9 Handle errors: session not found, session full, game already started (toast notifications)
- [x] 2.10 Verify: two browser tabs can create/join a session, see each other in waiting room, host can start

---

## Slice 3: WebSocket Connection & Real-Time State

**Goal:** Frontend connects via WebSocket on game start and receives live updates.

- [x] 3.1 Create `useWebSocket` hook: connects to `/ws/game/websocket` with STOMP, passes X-Session-Token header
- [x] 3.2 Implement automatic reconnection with exponential backoff (1s, 2s, 4s, 8s, max 30s)
- [x] 3.3 Subscribe to session topics on connect: `/topic/session/{code}/game`, `/topic/session/{code}/leaderboard`, `/topic/session/{code}/player/{id}/metrics`, `/topic/session/{code}/player/{id}/events`
- [x] 3.4 Create `useGameState` hook: manages game state from WebSocket messages (metrics, leaderboard, events, rApp status changes)
- [x] 3.5 Handle GAME_STARTED message: transition from lobby to game view
- [x] 3.6 Handle GAME_ENDED message: transition to results screen
- [x] 3.7 Add connection status indicator in UI (green dot = connected, yellow = reconnecting, red = disconnected)
- [x] 3.8 Verify: start game in one tab, second tab receives GAME_STARTED and transitions to game view

---

## Slice 4: Game Board — 3D Isometric Map

**Goal:** The main game view with a stunning 3D isometric map showing basestations as actual tower structures.

- [x] 4.1 Create `GamePage` layout: left panel (map, 70% width), right panel (sidebar with tabs: rApps, Events, Leaderboard)
- [x] 4.2 Create `IsometricMap` component using React Three Fiber with isometric camera (OrthographicCamera at 45° angle)
- [x] 4.3 Design and render ground plane: stylised terrain with subtle grid lines, gradient sky background (dark blue to purple), ambient particle effects (floating data points)
- [x] 4.4 Create `BasestationModel` 3D component: a stylised cell tower with antenna dish, blinking lights, and a glowing base ring (colour indicates health: green → yellow → red)
- [x] 4.5 Position basestations on the map using their positionX/positionY coordinates, with slight elevation differences
- [x] 4.6 Add hover interaction on basestations: glow effect intensifies, tooltip shows name + quick metrics summary
- [x] 4.7 Add click interaction: selects basestation, highlights it, opens detail panel in sidebar
- [x] 4.8 Add ambient animations: rotating antenna dishes, pulsing connection lines between towers, floating holographic data rings
- [x] 4.9 Add event visual indicators: when an event hits a basestation, show a warning pulse animation (expanding red ring) and a hazard icon floating above the tower
- [x] 4.10 Add deployed rApp visual indicators: small orbiting icons around the tower for each active rApp (colour-coded by type)
- [x] 4.11 Verify: game starts, map renders with 3 basestations as 3D towers, can hover and click them

---

## Slice 5: Basestation Detail & Metrics Display

**Goal:** Clicking a basestation shows its full status with animated metric bars.

- [x] 5.1 Create `BasestationDetail` sidebar panel: shows selected basestation name, position, and all 6 metrics
- [x] 5.2 Create `MetricBar` component: animated horizontal bar (Framer Motion spring animation on value change), colour gradient (green at 100 → red at 0), with numeric value and icon (Lucide: Heart for health, Users for custExp, Zap for energy, Shield for SLA, Cpu for autoRel, DollarSign for cost)
- [x] 5.3 Display deployed rApps list on selected basestation: name, status badge (DEPLOYING/ACTIVE/DISABLED), version number, aggressiveness level
- [x] 5.4 Display active events on selected basestation: event type icon, severity badge (colour-coded: LOW=blue, MEDIUM=amber, HIGH=orange, CRITICAL=red), escalation level indicator, description
- [x] 5.5 Add real-time metric updates: when METRICS_UPDATED WebSocket message arrives, animate the bars smoothly to new values
- [x] 5.6 Add flash animation on metric change (brief highlight on the bar that changed)
- [x] 5.7 Verify: select a basestation, see metrics at 100, push an event via internal API, see metrics decrease with animation

---

## Slice 6: rApp Catalogue & Deployment

**Goal:** Players can browse rApps and deploy them to basestations.

- [x] 6.1 Create `RappCatalogue` tab in sidebar: fetches from `GET /api/rapps/catalogue`, displays as card grid
- [x] 6.2 Design rApp card: icon (unique per rApp type), name, cost (€ badge), risk/confidence meters, one-line benefit description
- [x] 6.3 Add "Deploy" button on each rApp card: opens deployment modal
- [x] 6.4 Create `DeployModal`: select target basestation (dropdown or click on map), confirm deployment, shows cost deduction preview
- [x] 6.5 Call `POST /api/sessions/{code}/rapps/deploy` on confirm, show success animation (rApp icon flies to basestation on map)
- [x] 6.6 Show deployment status on map: DEPLOYING state shows a loading spinner on the basestation, ACTIVE shows the orbiting icon
- [x] 6.7 Add rApp management actions on deployed rApps: Tune button, Disable button, Rollback button
- [x] 6.8 Create `TuneModal`: slider for threshold (1-100), radio buttons for aggressiveness (LOW/MODERATE/HIGH), calls `PUT /rapps/{id}/tune`
- [x] 6.9 Implement disable action: calls `PUT /rapps/{id}/disable`, removes orbiting icon from map with fade-out animation
- [x] 6.10 Implement rollback action: calls `PUT /rapps/{id}/rollback`, shows version revert animation
- [x] 6.11 Verify: browse catalogue, deploy Energy Saver to a basestation, see it appear on map, tune it, disable it

---

## Slice 7: Events & Alerts

**Goal:** Events appear on the map and in the UI with urgency indicators.

- [x] 7.1 Handle EVENT_OCCURRED WebSocket message: add event to game state, trigger alert
- [x] 7.2 Create `EventAlert` toast notification: slides in from top-right with event type icon, severity colour, basestation name, and description. Auto-dismisses after 5s.
- [x] 7.3 Create `EventPanel` tab in sidebar: lists all active (unresolved) events sorted by severity, shows escalation level as progress dots (0-3), time since created
- [x] 7.4 Add event markers on map: pulsing warning triangle above affected basestation, colour matches severity
- [x] 7.5 Add escalation visual: as events escalate, the warning pulse gets larger and faster, tower glow shifts more toward red
- [x] 7.6 Show "Recommended rApp" hint on event cards: based on the event-rApp effectiveness mapping (e.g., "Deploy Energy Saver or Fault Predictor to resolve")
- [x] 7.7 Add event resolution animation: when an event is resolved (disappears from active list), show a green checkmark burst on the basestation
- [x] 7.8 Verify: push event via internal API, see alert toast, see warning on map, deploy effective rApp, see resolution animation

---

## Slice 8: Leaderboard & Scoring

**Goal:** Real-time leaderboard showing all players' scores with smooth rank animations.

- [x] 8.1 Create `Leaderboard` tab in sidebar: shows all players ranked by composite score
- [x] 8.2 Design leaderboard entry: rank number (with crown icon for #1), player name, composite score, mini breakdown (money/satisfaction/stability as small coloured dots)
- [x] 8.3 Handle LEADERBOARD_UPDATED WebSocket message: update scores with animated number transitions (count-up/down effect)
- [x] 8.4 Add rank change animation: when a player moves up/down, slide their row with Framer Motion layout animation
- [x] 8.5 Highlight current player's row with a subtle accent border
- [x] 8.6 Add "Your Score" summary card at top of game view: shows money remaining, satisfaction %, stability % with trend arrows (up/down from last tick)
- [x] 8.7 Verify: start game with 2 players, deploy rApps, see scores update in real-time, see rank changes animate

---

## Slice 9: Game End & Results Screen

**Goal:** When the game ends, show a polished results screen with winner announcement.

- [x] 9.1 Handle GAME_ENDED WebSocket message: transition to results page with dramatic animation
- [x] 9.2 Create `ResultsPage`: full-screen with dark overlay, winner announcement at top (player name + crown icon + final score)
- [x] 9.3 Show final leaderboard with all players' scores expanded (money, satisfaction, stability breakdown)
- [x] 9.4 Add score reveal animation: scores count up from 0 to final value with staggered timing per player
- [x] 9.5 Add confetti/particle effect for the winner
- [x] 9.6 Add "Play Again" button: returns to lobby, creates new session
- [x] 9.7 Add "Back to Home" button: returns to landing page
- [x] 9.8 Verify: play a short game (set tick.total=5 in test), game ends, results screen shows with animations

---

## Slice 10: Polish & Responsiveness

**Goal:** Final polish pass — loading states, error handling, responsive layout, accessibility.

- [x] 10.1 Add loading skeletons for all data-fetching states (catalogue, basestations, leaderboard)
- [x] 10.2 Add error boundary component with retry button
- [x] 10.3 Add responsive breakpoints: sidebar collapses to bottom sheet on mobile, map takes full width
- [x] 10.4 Add keyboard shortcuts: Escape to close modals, Tab navigation through rApp cards
- [x] 10.5 Add ARIA labels to all interactive elements for screen reader accessibility
- [x] 10.6 Add sound effects (optional, toggleable): deploy sound, event alert chime, game end fanfare
- [x] 10.7 Add dark/light theme toggle (default dark)
- [x] 10.8 Performance: lazy-load 3D map component, memoize expensive renders, virtualise long lists
- [x] 10.9 Write component tests: Lobby flow, deploy flow, leaderboard rendering (React Testing Library)
- [x] 10.10 Verify: full game playable end-to-end in browser, smooth 60fps, no console errors
