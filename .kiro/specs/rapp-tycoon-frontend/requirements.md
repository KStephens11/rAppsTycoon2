# Requirements Document

# rApp Tycoon Frontend UI Requirements

## Introduction

The rApp Tycoon Frontend is a React-based browser application that provides the player interface for the rApp Tycoon multiplayer strategy game. The frontend enables players to create or join game sessions, manage their basestations, deploy and tune rApps, monitor real-time metrics, and compete on a live leaderboard. The UI must display complex network state information in an intuitive isometric 3D map view, provide real-time updates via WebSocket, and support responsive design across desktop and tablet devices. The frontend communicates with the Java Spring Boot backend via REST API and WebSocket, consuming the contract defined in API_CONTRACT.md.

## Glossary

- **Frontend**: The React-based browser client providing the player interface
- **UI_Component**: A reusable React component representing a visual element (e.g., Button, Card, Map)
- **Page**: A top-level view in the application (e.g., Lobby Page, Game Board Page)
- **Session_Code**: An 8-character alphanumeric code identifying a game session
- **Player_Token**: A session token returned by the backend used to authenticate API requests
- **Basestation**: A virtual 5G network node displayed on the map with health status and deployed rApps
- **rApp_Catalog**: A sidebar component displaying available rApps with their properties
- **Region_Map**: The central isometric 3D visualization showing basestations and their status
- **Session_Scoreboard**: A real-time leaderboard showing player rankings and scores
- **Network_Metrics**: A dashboard displaying six key performance indicators as sparkline charts
- **Live_Incident_Feed**: A notification panel showing real-time events with severity levels
- **Player_Region_Selector**: UI controls allowing players to switch between their assigned regions
- **Action_Button**: A control triggering player actions (Deploy, Tune, Disable, Rollback)
- **WebSocket_Connection**: A persistent bidirectional communication channel for real-time updates
- **Error_State**: A UI state displayed when an operation fails or the connection is lost
- **Loading_State**: A UI state displayed while data is being fetched or an operation is in progress
- **Responsive_Design**: UI that adapts layout and sizing based on viewport dimensions
- **Accessibility**: Design ensuring the UI is usable by people with disabilities (WCAG 2.1 AA compliance)

## Requirements

### Requirement 1: Lobby and Session Creation UI

**User Story:** As a player, I want to create a new game session or join an existing one through an intuitive lobby interface, so that I can start playing with other players.

#### Acceptance Criteria

1. WHEN a Player navigates to the application, THE Frontend SHALL display a Lobby Page with options to create or join a session
2. WHEN a Player clicks the create session button, THE Frontend SHALL display a form requesting the Player's display name
3. WHEN a Player submits the create session form with a valid display name (1-50 characters), THE Frontend SHALL call the Backend's POST /api/sessions endpoint and display the generated Session_Code
4. IF the create session request fails, THEN THE Frontend SHALL display an error message describing the failure without exposing internal system details
5. WHEN a Player clicks the join session button, THE Frontend SHALL display a form requesting the Session_Code and display name
6. WHEN a Player submits the join session form with a valid Session_Code and display name, THE Frontend SHALL call the Backend's POST /api/sessions/{code}/join endpoint
7. IF the join session request fails with SESSION_FULL error, THEN THE Frontend SHALL display a message indicating the session is full
8. IF the join session request fails with SESSION_NOT_FOUND error, THEN THE Frontend SHALL display a message indicating the session code is invalid
9. WHEN the Backend successfully confirms the join request, THE Frontend SHALL store the Player_Token in secure session storage and navigate to the Lobby Waiting Page
10. THE Frontend SHALL validate all user input on the client side before submitting to the Backend

### Requirement 2: Lobby Waiting Room UI

**User Story:** As a player in a lobby, I want to see other players joining and wait for the host to start the game, so that I know the session is ready to begin.

#### Acceptance Criteria

1. WHEN a Player enters the Lobby Waiting Page, THE Frontend SHALL display the Session_Code prominently for sharing with other players
2. THE Frontend SHALL display a list of all Players currently in the Lobby with their display names
3. WHEN another Player joins the Lobby, THE Frontend SHALL update the player list in real-time without requiring a page refresh
4. IF the Player is the host, THE Frontend SHALL display a start game button that is enabled only when at least 2 Players are in the Lobby
5. IF the Player is the host and fewer than 2 Players are in the Lobby, THE Frontend SHALL display a message indicating additional players are needed
6. IF the Player is not the host, THE Frontend SHALL display a message indicating the host must start the game
6. WHEN the host clicks the start game button, THE Frontend SHALL call the Backend's POST /api/sessions/{code}/start endpoint
7. IF the start game request fails with INVALID_STATE error, THEN THE Frontend SHALL display a message indicating insufficient players
8. WHEN the game starts successfully, THE Frontend SHALL establish a WebSocket connection immediately and navigate to the Game Board Page
9. THE Frontend SHALL display a leave session button that allows Players to exit the Lobby before the game starts
10. WHEN a Player clicks leave session, THE Frontend SHALL navigate back to the Lobby Page

### Requirement 3: Game Board Layout and Navigation

**User Story:** As a player during active gameplay, I want to see the game board with all UI panels organized logically, so that I can monitor my network and make strategic decisions.

#### Acceptance Criteria

1. WHEN a Player enters the Game Board Page, THE Frontend SHALL display a multi-panel layout with the following components:
   - Left Sidebar: rApp_Catalog (20% width)
   - Center: Region_Map (60% width)
   - Top Right: Session_Scoreboard (20% width, 40% height)
   - Bottom Left: Network_Metrics (20% width, 60% height)
   - Bottom Right: Live_Incident_Feed (20% width, 60% height)
2. THE Frontend SHALL ensure all panels are visible simultaneously without requiring horizontal scrolling on desktop viewports (1920×1080 or larger)
3. THE Frontend SHALL display a Player_Region_Selector above the Region_Map allowing players to switch between their assigned regions (A, B, C, D)
4. THE Frontend SHALL display Action_Buttons below the Region_Map for Deploy, Tune, Disable, and Rollback operations
5. THE Frontend SHALL display a game timer showing elapsed time and remaining time until game end
6. THE Frontend SHALL display the current Player's name and remaining money prominently in the top-left corner
7. WHEN the viewport width is less than 1920 pixels, THE Frontend SHALL adapt the layout to maintain usability (e.g., stacking panels vertically or hiding non-critical information)
8. THE Frontend SHALL ensure the layout remains responsive and readable on tablet devices (768×1024 or larger)

### Requirement 4: Basestation Map Visualization

**User Story:** As a player, I want to see my basestations on an isometric 3D map with color-coded health status, so that I can quickly assess network health and identify problem areas.

#### Acceptance Criteria

1. WHEN the Region_Map loads, THE Frontend SHALL render an isometric 3D view of the selected region showing all Basestations owned by the Player
2. EACH Basestation SHALL be displayed as a 3D tower icon with a color-coded health indicator:
   - Green: Health ≥ 80 (Active)
   - Yellow: Health 50-79 (Warning)
   - Red: Health < 50 (Critical)
3. WHEN a Basestation's health metric changes, THE Frontend SHALL update the color indicator within 2 seconds of receiving the METRICS_UPDATED WebSocket message
4. WHEN a Player hovers over a Basestation, THE Frontend SHALL display a tooltip showing the Basestation name, health percentage, and number of deployed rApps
5. WHEN a Player clicks on a Basestation, THE Frontend SHALL highlight the selected Basestation and display detailed metrics in the Network_Metrics panel
6. THE Frontend SHALL display the Basestation's position label (e.g., "BS-Alpha") below or adjacent to the tower icon
7. WHEN an Event affects a Basestation, THE Frontend SHALL display a visual indicator (e.g., animated alert icon or pulsing border) within 2 seconds of receiving the EVENT_OCCURRED WebSocket message
8. THE Frontend SHALL support zooming and panning of the map to allow players to focus on specific areas
9. THE Frontend SHALL render the map using a performant graphics library (e.g., Three.js or Babylon.js) to support smooth animations and interactions
10. THE Frontend SHALL display a legend explaining the color coding and visual indicators

### Requirement 5: rApp Catalog Display and Deployment UI

**User Story:** As a player, I want to browse available rApps with their characteristics and deploy them to my basestations, so that I can optimise my network performance.

#### Acceptance Criteria

1. WHEN the rApp_Catalog loads, THE Frontend SHALL call the Backend's GET /api/rapps/catalogue endpoint and display all available rApps
2. IF the API call fails or takes longer than 10 seconds, THE Frontend SHALL display a loading state or empty catalog until the API responds successfully
   - Name
   - Purpose (brief description)
   - Cost (in euros)
   - Benefit (brief description)
   - Risk percentage
   - Confidence percentage
   - Side effects (if any)
3. THE Frontend SHALL sort rApps by name alphabetically by default
4. THE Frontend SHALL allow Players to filter rApps by cost range or risk level
5. WHEN a Player clicks on an rApp in the catalog, THE Frontend SHALL display a detailed view with the full Impact object showing effects on each metric
6. WHEN a Player selects a Basestation on the map and clicks a deploy button on an rApp, THE Frontend SHALL display a confirmation dialog showing the deployment cost and expected impacts
7. WHEN a Player confirms the deployment, THE Frontend SHALL call the Backend's POST /api/sessions/{code}/rapps/deploy endpoint with the templateId and basestationId
8. IF the deployment request succeeds, THE Frontend SHALL display a success message and update the Basestation's deployed rApps list
9. IF the deployment request fails, THE Frontend SHALL display an error message describing the failure and NOT update the Basestation's deployed rApps list
10. WHILE an rApp is in DEPLOYING status, THE Frontend SHALL display a loading indicator next to the rApp name
11. WHEN an rApp transitions to ACTIVE status, THE Frontend SHALL update the UI to show the rApp as active and display its current configuration

### Requirement 6: Real-Time Metrics Dashboard

**User Story:** As a player, I want to see real-time network metrics displayed as sparkline charts, so that I can monitor trends and make informed deployment decisions.

#### Acceptance Criteria

1. WHEN a Basestation is selected on the map, THE Frontend SHALL display the Network_Metrics panel showing six key metrics:
   - Network Health
   - Latency (inverse of customer experience)
   - Customer Satisfaction
   - Energy Efficiency
   - Operating Cost
   - SLA Compliance
2. EACH metric SHALL be displayed as a sparkline chart showing the metric's value over the last 60 seconds (or last 12 game ticks)
3. EACH metric SHALL display the current value prominently (e.g., "85.5%") and the trend direction (up/down arrow)
4. WHEN a METRICS_UPDATED WebSocket message is received, THE Frontend SHALL update the corresponding sparkline chart within 1 second
5. THE Frontend SHALL use color coding to indicate metric health:
   - Green: Metric ≥ 80
   - Yellow: Metric 50-79
   - Red: Metric < 50
6. WHEN a Player hovers over a sparkline, THE Frontend SHALL display a tooltip showing the metric value at that point in time
7. THE Frontend SHALL display the Basestation name and position at the top of the metrics panel
8. THE Frontend SHALL display a summary showing the average metric values across all of the Player's Basestations
9. THE Frontend SHALL update all sparklines smoothly without flickering or jarring transitions

### Requirement 7: Session Leaderboard Display

**User Story:** As a player, I want to see a real-time leaderboard showing all players' scores and rankings, so that I can track my competitive standing.

#### Acceptance Criteria

1. WHEN the Game Board Page loads, THE Frontend SHALL call the Backend's GET /api/sessions/{code}/leaderboard endpoint and display the Session_Scoreboard
2. THE Session_Scoreboard SHALL display all Players ranked by composite score in descending order
3. EACH Player entry SHALL display:
   - Rank (1st, 2nd, etc.)
   - Player name
   - Composite score
   - Individual score components (money, customer satisfaction, network stability)
4. WHEN a LEADERBOARD_UPDATED WebSocket message is received, THE Frontend SHALL update the leaderboard within 2 seconds
5. IF the current Player's rank changes, THE Frontend SHALL highlight the change (e.g., with a color animation or arrow indicator)
6. THE Frontend SHALL display the leaderboard in a scrollable container to maintain consistent UI layout regardless of player count
7. THE Frontend SHALL update the leaderboard smoothly without flickering or re-rendering the entire list

### Requirement 8: Live Incident Feed and Event Notifications

**User Story:** As a player, I want to receive real-time notifications about events affecting my basestations, so that I can react quickly to network incidents.

#### Acceptance Criteria

1. WHEN an EVENT_OCCURRED WebSocket message is received, THE Frontend SHALL display a notification in the Live_Incident_Feed within 2 seconds
2. EACH event notification SHALL display:
   - Event type (e.g., POWER_OUTAGE)
   - Severity level (LOW, MEDIUM, HIGH, CRITICAL) with color coding
   - Affected Basestation name
   - Brief description of the event
   - Timestamp
3. THE Frontend SHALL color-code events by severity:
   - Green: LOW
   - Yellow: MEDIUM
   - Orange: HIGH
   - Red: CRITICAL
4. WHEN a CRITICAL event is received, THE Frontend SHALL display an audible alert (if the browser allows) and a prominent visual notification
5. THE Frontend SHALL display events in reverse chronological order (newest first) in the Live_Incident_Feed
6. THE Frontend SHALL limit the visible event history to the last 20 events, with older events accessible via scrolling
7. WHEN a Player clicks on an event in the feed, THE Frontend SHALL highlight the affected Basestation on the map and display the event details only after the user clicks
8. THE Frontend SHALL automatically dismiss or archive events after 5 minutes if they are resolved
9. THE Frontend SHALL display a count of unresolved events prominently in the feed header
10. THE Frontend SHALL support filtering events by severity level or affected Basestation

### Requirement 9: Player Action Controls (Deploy, Tune, Disable, Rollback)

**User Story:** As a player, I want to perform actions on my deployed rApps (deploy, tune, disable, rollback), so that I can optimise my network in response to events.

#### Acceptance Criteria

1. WHEN a Player selects a Basestation on the map, THE Frontend SHALL display the list of deployed rApps on that Basestation
2. FOR each deployed rApp, THE Frontend SHALL display:
   - rApp name
   - Current status (DEPLOYING, ACTIVE, DISABLED, ROLLING_BACK)
   - Current version
   - Current configuration (if tuned)
   - Action buttons (Tune, Disable, Rollback)
3. WHEN a Player clicks the Tune button on an rApp, THE Frontend SHALL display a configuration dialog allowing adjustment of:
   - Threshold (slider 1-100)
   - Aggressiveness (dropdown: LOW, MODERATE, HIGH)
4. WHEN a Player submits the tune configuration, THE Frontend SHALL call the Backend's PUT /api/sessions/{code}/rapps/{id}/tune endpoint
5. IF the tune request succeeds, THE Frontend SHALL display a success message and update the rApp's configuration display
6. WHEN a Player clicks the Disable button on an rApp, THE Frontend SHALL display a confirmation dialog
7. WHEN a Player confirms the disable action, THE Frontend SHALL call the Backend's PUT /api/sessions/{code}/rapps/{id}/disable endpoint
8. IF the disable request succeeds, THE Frontend SHALL update the rApp's status to DISABLED and remove its visual indicators from the map
9. WHEN a Player clicks the Rollback button on an rApp, THE Frontend SHALL display a confirmation dialog showing the previous version's configuration
10. WHEN a Player confirms the rollback action, THE Frontend SHALL call the Backend's PUT /api/sessions/{code}/rapps/{id}/rollback endpoint
11. IF the rollback request fails with INVALID_STATE error, THE Frontend SHALL display a message indicating the rApp cannot be rolled back
12. THE Frontend SHALL disable action buttons when the rApp is in a transitional state (DEPLOYING, ROLLING_BACK)
13. WHEN a RAPP_STATUS_CHANGED WebSocket message is received, THE Frontend SHALL update the rApp's status display within 1 second

### Requirement 10: Player Region Selector

**User Story:** As a player with multiple basestations in different regions, I want to switch between region views, so that I can focus on managing specific areas of my network.

#### Acceptance Criteria

1. WHEN the Game Board Page loads, THE Frontend SHALL display a Player_Region_Selector with buttons for each region (A, B, C, D)
2. EACH region button SHALL display the region letter and indicate whether the Player owns basestations in that region
3. WHEN a Player clicks a region button, THE Frontend SHALL update the Region_Map to display only Basestations in that region
4. THE Frontend SHALL highlight the currently selected region button with a distinct visual indicator (e.g., bold text or background color)
5. WHEN the Region_Map updates, THE Frontend SHALL smoothly transition the view (e.g., with a fade or zoom animation)
6. THE Frontend SHALL update the Network_Metrics panel to show metrics for Basestations in the selected region
7. IF a Player owns no Basestations in a region, THE Frontend SHALL display the region button as disabled or grayed out
8. THE Frontend SHALL remember the Player's last selected region and restore it if the Player navigates away and returns to the Game Board Page

### Requirement 11: WebSocket Real-Time Updates Integration

**User Story:** As a player, I want to receive real-time updates about game state changes via WebSocket, so that I see current information without manual refreshes.

#### Acceptance Criteria

1. WHEN a Player enters the Game Board Page, THE Frontend SHALL establish a WebSocket connection to ws://localhost:8080/ws/game with the Player_Token in the connection headers
2. WHEN the WebSocket connection is established, THE Frontend SHALL subscribe to the following destinations:
   - /topic/session/{code}/game
   - /topic/session/{code}/leaderboard
   - /topic/session/{code}/player/{playerId}/events
   - /topic/session/{code}/player/{playerId}/metrics
   - /topic/session/{code}/player/{playerId}/rapps
3. WHEN a GAME_STARTED message is received, THE Frontend SHALL display a notification and update the game state
4. WHEN a GAME_ENDED message is received, THE Frontend SHALL navigate to the Game Results Page and display the final leaderboard
5. WHEN an EVENT_OCCURRED message is received, THE Frontend SHALL display the event in the Live_Incident_Feed within 2 seconds
6. WHEN a METRICS_UPDATED message is received, THE Frontend SHALL atomically update both the Network_Metrics panel and map indicators within 1 second (both must succeed or both must fail)
7. WHEN a LEADERBOARD_UPDATED message is received, THE Frontend SHALL update the Session_Scoreboard within 2 seconds
8. WHEN a RAPP_STATUS_CHANGED message is received, THE Frontend SHALL update the rApp status display within 1 second
9. IF the WebSocket connection is lost, THE Frontend SHALL display a connection error message and attempt to reconnect with exponential backoff (1s, 2s, 4s, 8s, max 30s)
10. WHEN the WebSocket connection is restored, THE Frontend SHALL resynchronise game state by calling the Backend's GET /api/sessions/{code} endpoint
11. WHILE the WebSocket connection is disconnected, THE Frontend SHALL disable all player action buttons and display a warning message
12. THE Frontend SHALL validate all incoming WebSocket messages and discard malformed messages without crashing

### Requirement 12: Error Handling and User Feedback

**User Story:** As a player, I want clear error messages and feedback when operations fail, so that I understand what went wrong and how to recover.

#### Acceptance Criteria

1. WHEN an API request fails, THE Frontend SHALL display an error message describing the failure in user-friendly language
2. IF the error is a validation error (e.g., invalid input), THEN THE Frontend SHALL display the specific validation error and highlight the problematic field
3. IF the error is a session error (e.g., SESSION_NOT_FOUND), THEN THE Frontend SHALL display a message and offer to return to the Lobby Page
4. IF the error is an authorization error (e.g., UNAUTHORIZED), THEN THE Frontend SHALL clear the stored Player_Token and navigate to the Lobby Page
5. WHEN a network request times out after 30 seconds, THE Frontend SHALL display a timeout error message and offer to retry
6. WHEN a player action fails (e.g., deploy fails), THE Frontend SHALL display the error message without changing the UI state
7. THE Frontend SHALL NOT expose internal error codes or stack traces to the player
8. WHEN an error occurs, THE Frontend SHALL log the error to the browser console for debugging purposes
9. IF the Backend returns a 500 error in response to an API request, THEN THE Frontend SHALL display a user-friendly error message explaining the failure in plain language
10. WHEN multiple errors occur in quick succession, THE Frontend SHALL display them in a queue or stack to avoid overwhelming the player

### Requirement 13: Game Results and End State

**User Story:** As a player, I want to see the final results and leaderboard when the game ends, so that I can see who won and review the final scores.

#### Acceptance Criteria

1. WHEN a GAME_ENDED WebSocket message is received, THE Frontend SHALL navigate to the Game Results Page
2. THE Game Results Page SHALL display:
   - Final leaderboard with all players ranked by composite score
   - Winner's name and final score prominently
   - Each player's final score components (money, customer satisfaction, network stability)
   - Game duration and end time
3. THE Frontend SHALL display a button to return to the Lobby Page to create or join a new session
4. THE Frontend SHALL display a button to share the game results (e.g., copy to clipboard or social media)
5. THE Frontend SHALL display a summary of key statistics (e.g., total events, total rApps deployed, average metrics)

### Requirement 14: Responsive Design and Mobile Support

**User Story:** As a player on a tablet or smaller screen, I want the UI to adapt to my device's screen size, so that I can play comfortably without excessive scrolling.

#### Acceptance Criteria

1. THE Frontend SHALL support responsive design for viewports ranging from 768×1024 (tablet) to 2560×1440 (large desktop)
2. ON tablet viewports (768-1024px width), THE Frontend SHALL stack panels vertically or use a tabbed interface to maintain usability
3. ON tablet viewports, THE Frontend SHALL hide non-critical information (e.g., detailed tooltips) to reduce clutter
4. ON tablet viewports, THE Frontend SHALL increase touch target sizes for buttons and interactive elements to at least 44×44 pixels
5. THE Frontend SHALL use responsive font sizes that scale with viewport width
6. THE Frontend SHALL ensure all text remains readable without zooming on tablet devices
7. THE Frontend SHALL support landscape and portrait orientations on tablet devices
8. THE Frontend SHALL NOT require horizontal scrolling on any viewport size
9. WHEN the viewport is resized, THE Frontend SHALL smoothly adapt the layout without requiring a page refresh
10. THE Frontend SHALL test and verify responsive design on common devices (iPad, iPad Pro, Android tablets)

### Requirement 15: Accessibility and WCAG 2.1 AA Compliance

**User Story:** As a player with visual or motor disabilities, I want the UI to be accessible with keyboard navigation and screen readers, so that I can play the game independently.

#### Acceptance Criteria

1. THE Frontend SHALL implement keyboard navigation for all interactive elements (buttons, links, form inputs)
2. THE Frontend SHALL support Tab key navigation through all interactive elements in a logical order
3. THE Frontend SHALL support Enter and Space keys to activate buttons and links
4. THE Frontend SHALL support arrow keys to navigate lists and select options
5. ALL interactive elements SHALL have visible focus indicators (e.g., outline or highlight)
6. ALL images and icons SHALL have descriptive alt text or aria-labels
7. ALL form inputs SHALL have associated labels or aria-labels
8. THE Frontend SHALL use semantic HTML elements (button, link, heading, list) instead of generic divs
9. THE Frontend SHALL implement ARIA roles and attributes to describe complex components (e.g., aria-live for real-time updates)
10. THE Frontend SHALL ensure color is not the only means of conveying information (e.g., use icons or text in addition to color)
11. THE Frontend SHALL support screen reader navigation and announce important state changes (e.g., "Event occurred: Power Outage at BS-Alpha")
12. THE Frontend SHALL test accessibility with automated tools (e.g., axe, Lighthouse) and manual testing with screen readers
13. THE Frontend SHALL achieve a Lighthouse accessibility score of at least 90

### Requirement 16: Performance and Optimization

**User Story:** As a player, I want the UI to be responsive and fast, so that I can interact with the game without lag or delays.

#### Acceptance Criteria

1. THE Frontend SHALL load the initial page within 3 seconds on a 4G connection
2. THE Frontend SHALL render the Region_Map with smooth animations (60 FPS) even with 12 basestations visible
3. THE Frontend SHALL update the Network_Metrics sparklines within 1 second of receiving a METRICS_UPDATED message
4. THE Frontend SHALL update the Session_Scoreboard within 2 seconds of receiving a LEADERBOARD_UPDATED message
5. THE Frontend SHALL debounce or throttle frequent updates (e.g., metrics updates) to avoid excessive re-renders
6. THE Frontend SHALL lazy-load images and non-critical components to reduce initial page load time
7. THE Frontend SHALL use code splitting to load only the necessary JavaScript for each page
8. THE Frontend SHALL cache API responses (e.g., rApp catalogue) to reduce network requests
9. THE Frontend SHALL minimize bundle size by removing unused dependencies and optimizing imports
10. THE Frontend SHALL use production builds with minification and tree-shaking enabled
11. THE Frontend SHALL achieve a Lighthouse performance score of at least 80

### Requirement 17: Session Persistence and Reconnection

**User Story:** As a player, I want my session to persist if I accidentally close the browser or lose connection, so that I can resume playing without losing progress.

#### Acceptance Criteria

1. WHEN a Player closes the browser or navigates away from the game, THE Frontend SHALL store the Player_Token and Session_Code in browser storage (localStorage or sessionStorage)
2. WHEN a Player returns to the application within 1 hour, THE Frontend SHALL attempt to restore the previous session using the stored token and code
3. WHEN the Backend confirms the session is still active, THE Frontend SHALL re-check session status immediately before navigation and then navigate to the Game Board Page
4. IF the session has ended, THE Frontend SHALL display a message indicating the session is no longer active and offer to return to the Lobby Page
5. IF the Player_Token is invalid or expired, THE Frontend SHALL clear the stored token and navigate to the Lobby Page
6. THE Frontend SHALL only store the Player_Token and Session_Code in browser storage, treating all other user data as potentially sensitive
7. THE Frontend SHALL clear stored session data when the Player logs out or the session ends

### Requirement 18: UI Theming and Customization

**User Story:** As a player, I want the UI to have a cohesive visual design that reflects the game's theme, so that I enjoy the aesthetic experience.

#### Acceptance Criteria

1. THE Frontend SHALL implement a consistent color scheme reflecting the 5G network theme (e.g., blues, purples, teals)
2. THE Frontend SHALL use a consistent typography system with defined font sizes and weights
3. THE Frontend SHALL implement a component library with reusable UI components (Button, Card, Input, etc.)
4. THE Frontend SHALL support a dark mode theme option that players can toggle
5. THE Frontend SHALL use CSS-in-JS or CSS modules to scope styles and avoid conflicts
6. THE Frontend SHALL implement smooth transitions and animations for state changes (e.g., color changes, panel slides)
7. THE Frontend SHALL use a design system or style guide to ensure consistency across all pages and components
8. THE Frontend SHALL test the UI on different browsers (Chrome, Firefox, Safari, Edge) to ensure visual consistency

### Requirement 19: Data Validation and Input Sanitization

**User Story:** As a system administrator, I want the frontend to validate and sanitize all user input, so that the application is protected against injection attacks and malformed data.

#### Acceptance Criteria

1. THE Frontend SHALL validate all user input on the client side before submitting to the Backend
2. THE Frontend SHALL enforce input constraints (e.g., max length, allowed characters) for all form fields
3. THE Frontend SHALL sanitize all user input to prevent XSS (cross-site scripting) attacks
4. THE Frontend SHALL escape all user-generated content before displaying it in the UI
5. THE Frontend SHALL validate API responses from the Backend before using them in the UI
6. THE Frontend SHALL reject malformed or unexpected API responses and display an error message
7. THE Frontend SHALL use Content Security Policy (CSP) headers to prevent inline script execution
8. WHEN XSS prevention is enabled through content escaping, THE Frontend SHALL require XSS prevention to be active
9. THE Frontend SHALL NOT store API keys in the frontend code or browser storage

### Requirement 20: Browser Compatibility and Testing

**User Story:** As a developer, I want the frontend to work reliably across modern browsers, so that players can access the game from their preferred browser.

#### Acceptance Criteria

1. THE Frontend SHALL support the latest versions of Chrome, Firefox, Safari, and Edge browsers
2. THE Frontend SHALL support browsers released within the last 2 years
3. THE Frontend SHALL use feature detection and polyfills for older browser compatibility where necessary
4. THE Frontend SHALL test all features on multiple browsers to ensure consistent behavior
5. THE Frontend SHALL use automated testing tools (e.g., Selenium, Cypress) to test cross-browser compatibility
6. THE Frontend SHALL display a browser compatibility warning if the player is using an unsupported browser
7. THE Frontend SHALL gracefully degrade functionality for browsers that do not support certain features (e.g., WebSocket)

