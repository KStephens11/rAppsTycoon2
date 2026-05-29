# rApp Tycoon Frontend UI — Technical Design Document

## Context

### What Are We Building?

The rApp Tycoon Frontend is a React-based browser application that provides the player interface for a competitive multiplayer strategy game. Players manage virtual 5G network regions by deploying automation applications (rApps) to basestations, responding to real-time events, and competing on a live leaderboard. The frontend must display complex network state information in an intuitive isometric 3D map view, provide real-time updates via WebSocket, and support responsive design across desktop and tablet devices.

### Why?

The frontend is the primary user-facing interface for the rApp Tycoon game. It must:
- Enable players to create/join sessions and manage game state
- Visualize network topology and health status in real-time
- Provide responsive controls for deploying and tuning rApps
- Display real-time metrics, leaderboards, and incident feeds
- Support accessibility standards (WCAG 2.1 AA) for inclusive gameplay
- Deliver responsive performance (3s initial load, 60 FPS animations)

### Key Constraints

- **Backend Contract**: REST API + WebSocket (STOMP) communication defined in API_CONTRACT.md
- **Browser Environment**: Must work on modern browsers (Chrome, Firefox, Safari, Edge)
- **Device Support**: Desktop (1920×1080+), tablet (768×1024+), responsive down to 768px width
- **Real-Time Requirements**: Updates within 1-2 seconds of WebSocket messages
- **Performance Targets**: Lighthouse score ≥80, accessibility score ≥90, 60 FPS animations
- **Game Duration**: 5 minutes (60 ticks at 5s/tick) with continuous real-time updates

---

## Architecture

### High-Level Component Hierarchy

```
App
├── Router
│   ├── LobbyPage
│   │   ├── CreateSessionForm
│   │   └── JoinSessionForm
│   ├── LobbyWaitingPage
│   │   ├── SessionCodeDisplay
│   │   ├── PlayerList
│   │   └── StartGameButton
│   ├── GameBoardPage
│   │   ├── GameHeader (player name, money, timer)
│   │   ├── RegionSelector
│   │   ├── RegionMap (3D isometric visualization)
│   │   ├── ActionButtons (Deploy, Tune, Disable, Rollback)
│   │   ├── rAppCatalog (left sidebar)
│   │   ├── NetworkMetricsDashboard (bottom left)
│   │   ├── SessionScoreboard (top right)
│   │   ├── LiveIncidentFeed (bottom right)
│   │   └── DeploymentConfirmationDialog
│   └── GameResultsPage
│       ├── FinalLeaderboard
│       ├── WinnerDisplay
│       └── ShareResultsButton
├── WebSocketProvider (context for real-time updates)
├── GameStateProvider (context for game state management)
└── ErrorBoundary
```

### State Management Architecture

**Technology**: React Context API + useReducer for simplicity and minimal dependencies

**State Layers**:

1. **Global Game State** (GameStateContext)
   - Session info (code, state, players)
   - Current player info (id, name, token, money)
   - All basestations and their metrics
   - Deployed rApps and their status
   - Active events
   - Leaderboard data
   - Game timer and tick count

2. **UI State** (UIStateContext)
   - Selected region (A, B, C, D)
   - Selected basestation
   - Active dialogs (deployment confirmation, tune config)
   - Loading states
   - Error messages
   - Responsive layout state

3. **WebSocket State** (WebSocketContext)
   - Connection status (connected, disconnected, reconnecting)
   - Subscription status
   - Reconnection attempt count
   - Last message timestamp

4. **Local Component State**
   - Form inputs (display name, session code)
   - UI animations and transitions
   - Tooltip visibility
   - Scroll positions

### Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    React Components                          │
│  (LobbyPage, GameBoardPage, GameResultsPage, etc.)          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────┐
        │   Context Providers        │
        │ ┌──────────────────────┐   │
        │ │ GameStateContext     │   │
        │ │ UIStateContext       │   │
        │ │ WebSocketContext     │   │
        │ └──────────────────────┘   │
        └────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
   ┌─────────────┐         ┌──────────────┐
   │ REST Client │         │ WebSocket    │
   │ (Axios)     │         │ (STOMP)      │
   └─────────────┘         └──────────────┘
        │                         │
        ▼                         ▼
   ┌─────────────────────────────────────┐
   │   Backend (Spring Boot)             │
   │ ┌─────────────────────────────────┐ │
   │ │ REST API Endpoints              │ │
   │ │ WebSocket STOMP Destinations    │ │
   │ │ Event Generator Integration     │ │
   │ └─────────────────────────────────┘ │
   └─────────────────────────────────────┘
```

### Component Responsibilities

#### Page Components
- **LobbyPage**: Session creation/join forms, input validation
- **LobbyWaitingPage**: Player list, start game button, real-time player updates
- **GameBoardPage**: Main game interface, layout orchestration, panel coordination
- **GameResultsPage**: Final leaderboard, winner display, share functionality

#### Feature Components
- **RegionMap**: 3D isometric visualization using Three.js/Babylon.js
- **rAppCatalog**: Catalog display, filtering, deployment UI
- **NetworkMetricsDashboard**: Sparkline charts, metric display, color coding
- **SessionScoreboard**: Real-time leaderboard with rank changes
- **LiveIncidentFeed**: Event notifications with severity filtering
- **RegionSelector**: Region switching with visual feedback
- **ActionButtons**: Deploy, Tune, Disable, Rollback controls

#### Utility Components
- **DeploymentConfirmationDialog**: Cost/impact preview before deployment
- **TuneConfigurationDialog**: Threshold and aggressiveness sliders
- **ErrorBoundary**: Graceful error handling
- **LoadingSpinner**: Loading state indicator
- **Toast/Notification**: User feedback messages

### API Integration Layer

**REST Client** (Axios instance with interceptors):
- Base URL configuration (dev/prod)
- Request/response interceptors for token injection
- Error handling and retry logic
- Request timeout (30s)
- Automatic token refresh on 401

**WebSocket Client** (STOMP over WebSocket):
- Connection establishment with token in headers
- Automatic subscription to player-specific topics
- Message parsing and validation
- Reconnection with exponential backoff (1s, 2s, 4s, 8s, max 30s)
- Heartbeat/keepalive mechanism

**API Endpoints Used**:
- `POST /api/sessions` — Create session
- `POST /api/sessions/{code}/join` — Join session
- `POST /api/sessions/{code}/start` — Start game
- `GET /api/sessions/{code}` — Get session state
- `GET /api/sessions/{code}/basestations` — Get player's basestations
- `GET /api/rapps/catalogue` — Get rApp catalog
- `POST /api/sessions/{code}/rapps/deploy` — Deploy rApp
- `PUT /api/sessions/{code}/rapps/{id}/tune` — Tune rApp
- `PUT /api/sessions/{code}/rapps/{id}/disable` — Disable rApp
- `PUT /api/sessions/{code}/rapps/{id}/rollback` — Rollback rApp
- `GET /api/sessions/{code}/leaderboard` — Get leaderboard

**WebSocket Topics Subscribed**:
- `/topic/session/{code}/game` — Game-wide broadcasts
- `/topic/session/{code}/leaderboard` — Leaderboard updates
- `/topic/session/{code}/player/{playerId}/events` — Events
- `/topic/session/{code}/player/{playerId}/metrics` — Metrics
- `/topic/session/{code}/player/{playerId}/rapps` — rApp status

### Routing Structure

```
/                          → LobbyPage (create/join)
/lobby/waiting/{code}      → LobbyWaitingPage
/game/{code}               → GameBoardPage
/results/{code}            → GameResultsPage
```

---

## Data Models

### TypeScript Interfaces

#### Session & Player

```typescript
interface GameSession {
  sessionCode: string;
  sessionId: number;
  state: 'LOBBY' | 'ACTIVE' | 'COMPLETED';
  maxPlayers: number;
  createdAt: string;
  startedAt?: string;
  endedAt?: string;
  players: Player[];
}

interface Player {
  id: number;
  displayName: string;
  sessionToken: string;
  isHost: boolean;
  connected: boolean;
  money: number;
  basestations: Basestation[];
}

interface CurrentPlayer {
  id: number;
  displayName: string;
  sessionToken: string;
  sessionCode: string;
  money: number;
  region: 'A' | 'B' | 'C' | 'D';
}
```

#### Basestation & Metrics

```typescript
interface Basestation {
  id: number;
  name: string;
  positionX: number;
  positionY: number;
  region: 'A' | 'B' | 'C' | 'D';
  metrics: Metrics;
  deployedRapps: RAppDeployment[];
  activeEvents: GameEvent[];
  healthStatus: 'active' | 'warning' | 'critical'; // derived from health metric
}

interface Metrics {
  health: number; // 0-100
  customerExperience: number; // 0-100
  cost: number; // 0-unlimited
  energyEfficiency: number; // 0-100
  automationReliability: number; // 0-100
  slaCompliance: number; // 0-100
}

interface MetricsHistory {
  basestationId: number;
  timestamp: string;
  metrics: Metrics;
}

interface MetricsSparkline {
  basestationId: number;
  metricName: keyof Metrics;
  history: number[]; // last 60 seconds / 12 ticks
  current: number;
  trend: 'up' | 'down' | 'stable';
}
```

#### rApp & Deployment

```typescript
interface RAppTemplate {
  id: number;
  name: string;
  purpose: string;
  cost: number;
  benefit: string;
  risk: number; // 0-100
  confidence: number; // 0-100
  sideEffects: string;
  impact: Impact;
}

interface RAppDeployment {
  id: number;
  templateId: number;
  name: string;
  basestationId: number;
  status: 'DEPLOYING' | 'ACTIVE' | 'DISABLED' | 'ROLLING_BACK';
  version: number;
  configuration: RAppConfiguration;
  deployedAt: string;
}

interface RAppConfiguration {
  threshold?: number; // 1-100
  aggressiveness?: 'LOW' | 'MODERATE' | 'HIGH';
}

interface Impact {
  health: number;
  customerExperience: number;
  cost: number;
  energyEfficiency: number;
  automationReliability: number;
  slaCompliance: number;
}
```

#### Events

```typescript
interface GameEvent {
  id: number;
  basestationId: number;
  basestationName: string;
  eventType: 'POWER_OUTAGE' | 'TRAFFIC_SPIKE' | 'HARDWARE_FAILURE' | 'SLA_BREACH' | 'INTERFERENCE' | 'CAPACITY_OVERFLOW';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  escalationLevel: number; // 0-3
  impact: Impact;
  createdAt: string;
  resolvedAt?: string;
}
```

#### Leaderboard

```typescript
interface LeaderboardEntry {
  rank: number;
  playerId: number;
  displayName: string;
  scores: {
    money: number;
    customerSatisfaction: number;
    networkStability: number;
  };
  compositeScore: number;
}

interface Leaderboard {
  entries: LeaderboardEntry[];
  gameState: 'ACTIVE' | 'COMPLETED';
}
```

#### WebSocket Messages

```typescript
interface WebSocketMessage<T> {
  type: 'GAME_STARTED' | 'GAME_ENDED' | 'EVENT_OCCURRED' | 'METRICS_UPDATED' | 'LEADERBOARD_UPDATED' | 'RAPP_STATUS_CHANGED';
  timestamp: string;
  payload: T;
}

interface GameStartedPayload {
  sessionCode: string;
  players: { id: number; displayName: string }[];
}

interface GameEndedPayload {
  sessionCode: string;
  winner: { playerId: number; displayName: string; compositeScore: number };
  finalLeaderboard: LeaderboardEntry[];
}

interface EventOccurredPayload {
  eventId: number;
  basestationId: number;
  basestationName: string;
  eventType: string;
  severity: string;
  description: string;
  impact: Impact;
}

interface MetricsUpdatedPayload {
  basestationId: number;
  basestationName: string;
  metrics: Metrics;
}

interface LeaderboardUpdatedPayload {
  leaderboard: LeaderboardEntry[];
}

interface RAppStatusChangedPayload {
  deploymentId: number;
  basestationId: number;
  name: string;
  previousStatus: string;
  newStatus: string;
  version: number;
}
```

#### UI State

```typescript
interface UIState {
  selectedRegion: 'A' | 'B' | 'C' | 'D';
  selectedBasestationId?: number;
  activeDialog?: 'deployment' | 'tune' | 'disable' | 'rollback';
  dialogData?: any;
  loading: boolean;
  error?: {
    code: string;
    message: string;
    timestamp: string;
  };
  notifications: Notification[];
  responsiveLayout: 'desktop' | 'tablet' | 'mobile';
}

interface Notification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  timestamp: string;
  duration?: number; // ms, undefined = persistent
}
```

---

## Security

### Authentication & Authorization

**Session Token Management**:
- Player token received on session join/creation
- Stored in `sessionStorage` (not `localStorage`) for session-only persistence
- Injected in `X-Session-Token` header for all API requests
- Cleared on logout or session end
- Validated on page reload (check token validity before navigation)

**Token Validation**:
- On app load, verify stored token is still valid by calling `GET /api/sessions/{code}`
- If 401 response, clear token and redirect to lobby
- If session has ended, display message and redirect to lobby

**CORS & CSP**:
- Backend should set `Access-Control-Allow-Origin` to frontend domain
- Content Security Policy headers to prevent XSS
- No credentials in CORS requests (token in header, not cookie)

### Input Validation

**Client-Side Validation** (before API submission):
- Display name: 1-50 characters, alphanumeric + spaces/hyphens
- Session code: 8 characters, alphanumeric
- Threshold: 1-100 (numeric)
- Aggressiveness: enum validation (LOW, MODERATE, HIGH)
- All form inputs sanitized to prevent XSS

**Server-Side Validation** (backend responsibility):
- All inputs re-validated on backend
- Error responses include field-level validation errors
- Frontend displays validation errors next to problematic fields

### XSS Prevention

- All user-generated content (player names, event descriptions) escaped before rendering
- Use React's built-in XSS protection (JSX escapes by default)
- Avoid `dangerouslySetInnerHTML` except for trusted content
- Sanitize WebSocket message payloads before rendering

### API Security

- All endpoints (except session creation) require `X-Session-Token` header
- Backend validates token ownership (player can only access their own data)
- No sensitive data in URL parameters (use request body)
- HTTPS in production (enforce via backend)
- WebSocket connection requires token in headers

### Error Handling

- Never expose internal error codes or stack traces to user
- Display user-friendly error messages
- Log errors to console for debugging (dev mode only)
- Implement error boundaries to catch React component errors
- Graceful degradation on API failures

---

## Performance Optimization

### Code Splitting & Lazy Loading

**Route-Based Code Splitting**:
```typescript
const LobbyPage = lazy(() => import('./pages/LobbyPage'));
const GameBoardPage = lazy(() => import('./pages/GameBoardPage'));
const GameResultsPage = lazy(() => import('./pages/GameResultsPage'));
```

**Component-Based Code Splitting**:
- Lazy load 3D map library (Three.js/Babylon.js) only on GameBoardPage
- Lazy load chart library (Recharts) only when metrics panel is visible
- Lazy load heavy components (rApp catalog) with Suspense boundaries

### Memoization & Re-render Optimization

**React.memo** for expensive components:
- RegionMap (3D rendering)
- NetworkMetricsDashboard (sparkline charts)
- SessionScoreboard (large lists)
- LiveIncidentFeed (event list)

**useMemo** for expensive computations:
- Filtered basestation list by region
- Leaderboard sorting and ranking
- Metric trend calculations
- Color coding logic

**useCallback** for event handlers:
- Deployment confirmation handlers
- Region selector click handlers
- Dialog open/close handlers

### Debouncing & Throttling

**Debounce**:
- Window resize events (layout recalculation)
- Search/filter inputs in rApp catalog
- Tooltip hover events

**Throttle**:
- WebSocket message processing (max 1 update per 100ms)
- Metrics sparkline updates (batch updates)
- Scroll events in incident feed

### Bundle Optimization

**Dependency Management**:
- Use lightweight alternatives (e.g., date-fns instead of moment.js)
- Tree-shake unused code with production builds
- Remove dev dependencies from production bundle
- Analyze bundle size with `webpack-bundle-analyzer`

**Asset Optimization**:
- Compress images (PNG/WebP for icons)
- Minify CSS and JavaScript
- Gzip compression on server
- Cache static assets with service worker

### Network Optimization

**API Caching**:
- Cache rApp catalog (GET /api/rapps/catalogue) for session duration
- Cache leaderboard with 2-second TTL
- Implement request deduplication (prevent duplicate in-flight requests)

**WebSocket Optimization**:
- Batch metrics updates (send once per tick instead of per-metric)
- Compress message payloads
- Implement message queuing during reconnection

### Rendering Performance

**60 FPS Target**:
- Use `requestAnimationFrame` for animations
- Avoid layout thrashing (batch DOM reads/writes)
- Use CSS transforms for animations (GPU-accelerated)
- Implement virtual scrolling for long lists (incident feed)

**Initial Load Performance**:
- Target: 3 seconds on 4G connection
- Implement skeleton loaders for async content
- Preload critical resources (fonts, CSS)
- Defer non-critical JavaScript

### Monitoring & Metrics

**Performance Monitoring**:
- Use Web Vitals API to track Core Web Vitals
- Log performance metrics to analytics service
- Monitor WebSocket latency
- Track API response times

---

## Error Handling

### Error Boundaries

**Component-Level Error Boundary**:
```typescript
<ErrorBoundary fallback={<ErrorFallback />}>
  <GameBoardPage />
</ErrorBoundary>
```

**Page-Level Error Boundary**:
- Catches errors in entire page
- Displays error message with retry button
- Logs error for debugging

### API Error Handling

**Error Response Processing**:
```typescript
if (response.status === 401) {
  // Unauthorized: clear token, redirect to lobby
  clearToken();
  navigate('/');
} else if (response.status === 404) {
  // Not found: display user-friendly message
  showNotification('Session not found. Please check the code.');
} else if (response.status === 409) {
  // Conflict: display specific error (SESSION_FULL, INVALID_STATE)
  showNotification(response.data.message);
} else if (response.status === 500) {
  // Server error: display generic message, log error
  logError(response.data);
  showNotification('Server error. Please try again later.');
}
```

**Retry Logic**:
- Automatic retry for network timeouts (max 3 attempts)
- Exponential backoff for retries (1s, 2s, 4s)
- Manual retry button for user-initiated actions

### WebSocket Error Handling

**Connection Loss**:
- Detect disconnection (no heartbeat for 30s)
- Display "Connection Lost" banner
- Disable action buttons
- Attempt reconnection with exponential backoff

**Reconnection**:
- On successful reconnection, resync game state
- Call `GET /api/sessions/{code}` to get latest state
- Replay any missed WebSocket messages (if available)
- Re-subscribe to all topics

**Message Validation**:
- Validate incoming WebSocket messages against schema
- Discard malformed messages
- Log validation errors for debugging

### User Feedback

**Toast Notifications**:
- Success: "rApp deployed successfully"
- Error: "Failed to deploy rApp: Insufficient funds"
- Warning: "Connection unstable, attempting to reconnect"
- Info: "Game will end in 30 seconds"

**Loading States**:
- Skeleton loaders for async content
- Spinner for in-progress operations
- Disabled buttons during operations

**Error Messages**:
- User-friendly language (no technical jargon)
- Actionable guidance (e.g., "Check your session code")
- Timestamp for debugging

---

## Implementation Considerations

### Technology Stack

**Core**:
- React 18+ (hooks, context, suspense)
- TypeScript for type safety
- React Router v6 for routing
- Axios for HTTP client
- STOMP client for WebSocket

**Styling**:
- styled-components or emotion for CSS-in-JS
- Tailwind CSS for utility classes (optional)
- CSS Grid/Flexbox for responsive layout

**3D Visualization**:
- Three.js or Babylon.js for isometric map
- Alternatively: Cesium.js for geospatial visualization

**Charts & Metrics**:
- Recharts for sparkline charts
- Alternatively: Chart.js or D3.js

**State Management**:
- React Context API + useReducer (no Redux needed)
- Zustand as lightweight alternative

**Build & Dev Tools**:
- Vite for fast development and optimized builds
- Vitest for unit testing
- Playwright or Cypress for E2E testing
- ESLint + Prettier for code quality

### Browser Support

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+
- Mobile browsers (iOS Safari, Chrome Mobile)

### Deployment

**Development**:
- Local dev server with hot module replacement
- Mock API server for frontend-only development

**Production**:
- Build optimization (minification, tree-shaking, code splitting)
- CDN for static assets
- Service worker for offline support (optional)
- Environment-based configuration (dev/staging/prod)

### Monitoring & Analytics

**Error Tracking**:
- Sentry for error reporting
- Custom error logging to backend

**Performance Monitoring**:
- Web Vitals tracking
- Custom performance metrics (API latency, WebSocket latency)
- User session tracking

**User Analytics**:
- Page view tracking
- User action tracking (deploy, tune, disable, rollback)
- Session duration tracking

---

## Summary

The rApp Tycoon Frontend is a complex, real-time multiplayer game interface that requires careful attention to state management, performance, accessibility, and error handling. The architecture uses React Context API for state management, REST + WebSocket for backend communication, and Three.js for 3D visualization. The design prioritizes responsive performance (3s load, 60 FPS), accessibility (WCAG 2.1 AA), and comprehensive error handling to ensure a smooth player experience across devices and network conditions.
