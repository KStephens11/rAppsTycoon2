import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GamePage } from '../pages/GamePage';

// Mock the API module
vi.mock('../services/api', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  ApiError: class ApiError extends Error {
    status: number;
    code: string;
    constructor(status: number, code: string, message: string) {
      super(message);
      this.status = status;
      this.code = code;
    }
  },
}));

// Mock the GameContext
vi.mock('../context/GameContext', () => ({
  useGame: () => ({
    token: 'test-token',
    sessionCode: 'ABCD1234',
    playerId: 1,
    isHost: true,
    gameState: 'active',
    players: [],
    finalLeaderboard: null,
    setSession: vi.fn(),
    setPlayers: vi.fn(),
    setGameState: vi.fn(),
    setFinalLeaderboard: vi.fn(),
    reset: vi.fn(),
  }),
  GameProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock the WebSocket hook
vi.mock('../hooks/useWebSocket', () => ({
  useWebSocket: () => ({
    connected: false,
    connecting: false,
    connectionState: 'disconnected',
    error: null,
    subscribe: vi.fn(),
    sendAction: vi.fn(),
    connect: vi.fn(),
    disconnect: vi.fn(),
  }),
}));

// Mock useGameState to return leaderboard entries
vi.mock('../hooks/useGameState', () => ({
  useGameState: () => ({
    basestations: [],
    leaderboard: [
      {
        rank: 1,
        playerId: 1,
        displayName: 'Player 1',
        compositeScore: 85.5,
        scores: { money: 90, customerSatisfaction: 80, networkStability: 86 },
      },
      {
        rank: 2,
        playerId: 2,
        displayName: 'Player 2',
        compositeScore: 72.3,
        scores: { money: 70, customerSatisfaction: 75, networkStability: 72 },
      },
    ],
    events: [],
    rappDeployments: [],
    finalLeaderboard: null,
    updateMetrics: vi.fn(),
    updateLeaderboard: vi.fn(),
    addEvent: vi.fn(),
    updateRappStatus: vi.fn(),
    setFinalLeaderboard: vi.fn(),
    resetGameState: vi.fn(),
  }),
}));

// Mock useGameSubscriptions (no-op)
vi.mock('../hooks/useGameSubscriptions', () => ({
  useGameSubscriptions: vi.fn(),
}));

// Mock useSoundEffects
vi.mock('../hooks/useSoundEffects', () => ({
  useSoundEffects: () => ({
    soundEnabled: true,
    toggleSound: vi.fn(),
    playDeploy: vi.fn(),
    playEventAlert: vi.fn(),
    playGameEnd: vi.fn(),
  }),
}));

// Mock the IsometricMap (heavy R3F component)
vi.mock('../components/game/IsometricMap', () => ({
  default: () => <div data-testid="isometric-map">IsometricMap</div>,
}));

import { apiGet } from '../services/api';

const mockCatalogue = {
  rapps: [
    {
      id: 1,
      name: 'Energy Saver',
      purpose: 'Reduces energy consumption',
      cost: 50,
      benefit: 'Reduces energy costs',
      risk: 15,
      confidence: 85,
      sideEffects: 'May reduce throughput',
      impact: {
        health: 0,
        customerExperience: 0,
        cost: -10,
        energyEfficiency: 20,
        automationReliability: 0,
        slaCompliance: 0,
      },
    },
  ],
};

const mockBasestations = {
  basestations: [
    {
      id: 1,
      name: 'BS-Alpha',
      positionX: 0,
      positionY: 0,
      metrics: {
        health: 90,
        customerExperience: 85,
        cost: 50,
        energyEfficiency: 70,
        automationReliability: 80,
        slaCompliance: 95,
      },
      deployedRapps: [],
      activeEvents: [],
    },
  ],
};

describe('GamePage simultaneous panel layout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // apiGet is called for catalogue and basestations
    vi.mocked(apiGet).mockImplementation((url: string) => {
      if (url.includes('/catalogue')) {
        return Promise.resolve(mockCatalogue);
      }
      if (url.includes('/basestations')) {
        return Promise.resolve(mockBasestations);
      }
      return Promise.resolve({});
    });
  });

  it('renders RappCatalogue, EventPanel, and Leaderboard all simultaneously (no tabs)', async () => {
    render(<GamePage />);

    // Wait for catalogue to load
    expect(await screen.findByText('rApp Catalogue')).toBeInTheDocument();

    // RappCatalogue is in the DOM (may appear in both desktop panel and mobile strip)
    expect(screen.getAllByText('Energy Saver').length).toBeGreaterThanOrEqual(1);

    // EventPanel is in the DOM (shows empty state when no events)
    // On desktop (md+) it's in the bottom bar; on mobile it's in the expandable bottom sheet
    expect(screen.getAllByText('No active events').length).toBeGreaterThanOrEqual(1);

    // Leaderboard is in the DOM with player entries
    // On desktop it's in the right panel; on mobile it's in the expandable bottom sheet
    expect(screen.getAllByText('Player 1').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Player 2').length).toBeGreaterThanOrEqual(1);

    // All three panel contents are in the DOM at the same time — no tab-based hiding
    const catalogueHeading = screen.getByText('rApp Catalogue');
    expect(catalogueHeading).toBeInTheDocument();
    expect(screen.getAllByText('No active events').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Player 1').length).toBeGreaterThanOrEqual(1);
  });

  it('does not render any tab navigation buttons in the DOM', async () => {
    render(<GamePage />);

    // Wait for content to load
    expect(await screen.findByText('rApp Catalogue')).toBeInTheDocument();

    // No tab buttons should exist — previously there were tabs like "Catalogue", "Events", "Leaderboard"
    const tabButtons = screen.queryAllByRole('tab');
    expect(tabButtons).toHaveLength(0);

    // No tablist should exist
    const tabLists = screen.queryAllByRole('tablist');
    expect(tabLists).toHaveLength(0);

    // No buttons with tab-like text for switching panels (tab navigation)
    // Note: ExpandableSection buttons for "Events" and "Leaderboard" exist in the mobile
    // bottom sheet but these are section expanders, not tab navigation buttons.
    expect(screen.queryByRole('button', { name: /^Catalogue$/i })).not.toBeInTheDocument();

    // Verify no tablist/tab roles exist (the key indicator of tab-based navigation)
    expect(screen.queryByRole('tabpanel')).not.toBeInTheDocument();
  });

  it('applies correct positioning classes for the layout structure', async () => {
    const { container } = render(<GamePage />);

    // Wait for content to load
    expect(await screen.findByText('rApp Catalogue')).toBeInTheDocument();

    // Right panel exists with correct classes
    const rightPanel = container.querySelector('.w-56') || container.querySelector('.w-72');
    expect(rightPanel).toBeInTheDocument();
    expect(rightPanel).toHaveClass('bg-surface');
    expect(rightPanel).toHaveClass('border-l');

    // Bottom bar exists with correct structure
    const bottomBar = container.querySelector('.h-36') || container.querySelector('.h-56');
    expect(bottomBar).toBeInTheDocument();
    expect(bottomBar).toHaveClass('border-t');

    // Map area has flex-1 class (takes remaining space)
    const mapArea = container.querySelector('.flex-1.relative.min-w-0.min-h-0');
    expect(mapArea).toBeInTheDocument();
  });
});
