import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GamePage } from '../pages/GamePage';
import { CatalogueStrip } from '../components/game/CatalogueStrip';
import { DragProvider } from '../context/DragContext';

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

// Mock useGameState to return leaderboard entries and events
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
    {
      id: 2,
      name: 'Capacity Optimiser',
      purpose: 'Optimises capacity',
      cost: 75,
      benefit: 'Increases capacity',
      risk: 10,
      confidence: 90,
      sideEffects: 'None',
      impact: {
        health: 5,
        customerExperience: 10,
        cost: -5,
        energyEfficiency: 0,
        automationReliability: 5,
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

describe('Mobile responsive behaviour', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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

  describe('Bottom sheet renders CatalogueStrip at < 768px viewport', () => {
    it('renders a mobile bottom sheet with md:hidden class (visible only on mobile)', async () => {
      const { container } = render(<GamePage />);

      // Wait for catalogue data to load (appears in both desktop and mobile)
      await screen.findAllByText('Energy Saver');

      // The BottomSheet has md:hidden class — visible on mobile, hidden on md+
      const mobileBottomSheet = container.querySelector('.md\\:hidden');
      expect(mobileBottomSheet).toBeInTheDocument();
    });

    it('renders CatalogueStrip inside the mobile bottom sheet', async () => {
      const { container } = render(<GamePage />);

      // Wait for catalogue data to load
      await screen.findAllByText('Energy Saver');

      // The mobile bottom sheet (md:hidden) should contain the CatalogueStrip
      const mobileBottomSheet = container.querySelector('.md\\:hidden');
      expect(mobileBottomSheet).toBeInTheDocument();

      // CatalogueStrip renders rApp cards with overflow-x-auto for horizontal scroll
      const stripContainer = mobileBottomSheet!.querySelector('.overflow-x-auto');
      expect(stripContainer).toBeInTheDocument();
    });

    it('renders rApp cards in the CatalogueStrip within the bottom sheet', async () => {
      const { container } = render(<GamePage />);

      // Wait for catalogue data to load
      await screen.findAllByText('Energy Saver');

      // The mobile bottom sheet should contain rApp names from the catalogue
      const mobileBottomSheet = container.querySelector('.md\\:hidden');
      expect(mobileBottomSheet).toBeInTheDocument();

      // Check that rApp cards are rendered inside the bottom sheet
      const energySaverCards = mobileBottomSheet!.querySelectorAll('[aria-label*="Energy Saver"]');
      expect(energySaverCards.length).toBeGreaterThan(0);
    });

    it('hides the right panel on mobile (hidden md:flex)', async () => {
      const { container } = render(<GamePage />);

      // Wait for content to load
      await screen.findAllByText('Energy Saver');

      // The right panel has 'hidden md:flex' — hidden on mobile, visible on md+
      const rightPanel = container.querySelector('.hidden.md\\:flex.w-56') || container.querySelector('.hidden.md\\:flex.w-72') || container.querySelector('.hidden.md\\:flex.w-64');
      expect(rightPanel).toBeInTheDocument();
      expect(rightPanel).toHaveClass('hidden');
      expect(rightPanel).toHaveClass('md:flex');
    });
  });

  describe('Events and Leaderboard are accessible via expandable indicators', () => {
    it('renders expandable section buttons for Events and Leaderboard in the bottom sheet', async () => {
      const { container } = render(<GamePage />);

      // Wait for content to load
      await screen.findAllByText('Energy Saver');

      // The mobile bottom sheet should contain expandable sections
      const mobileBottomSheet = container.querySelector('.md\\:hidden');
      expect(mobileBottomSheet).toBeInTheDocument();

      // Find expandable section buttons within the bottom sheet
      const expandButtons = mobileBottomSheet!.querySelectorAll('button[aria-expanded]');
      expect(expandButtons.length).toBeGreaterThanOrEqual(2);

      // Check that Events and Leaderboard section titles exist
      const eventsButton = Array.from(expandButtons).find((btn) =>
        btn.textContent?.includes('Events')
      );
      const leaderboardButton = Array.from(expandButtons).find((btn) =>
        btn.textContent?.includes('Leaderboard')
      );
      expect(eventsButton).toBeDefined();
      expect(leaderboardButton).toBeDefined();
    });

    it('expandable sections start collapsed (aria-expanded=false)', async () => {
      const { container } = render(<GamePage />);

      // Wait for content to load
      await screen.findAllByText('Energy Saver');

      const mobileBottomSheet = container.querySelector('.md\\:hidden');
      const expandButtons = mobileBottomSheet!.querySelectorAll('button[aria-expanded]');

      // Sections should start collapsed
      for (const btn of expandButtons) {
        expect(btn).toHaveAttribute('aria-expanded', 'false');
      }
    });

    it('clicking an expandable section toggles it open', async () => {
      const { container } = render(<GamePage />);

      // Wait for content to load
      await screen.findAllByText('Energy Saver');

      const mobileBottomSheet = container.querySelector('.md\\:hidden');
      const expandButtons = mobileBottomSheet!.querySelectorAll('button[aria-expanded]');

      // Find the Events button and click it
      const eventsButton = Array.from(expandButtons).find((btn) =>
        btn.textContent?.includes('Events')
      );
      expect(eventsButton).toBeDefined();

      fireEvent.click(eventsButton!);

      // After clicking, it should be expanded
      expect(eventsButton).toHaveAttribute('aria-expanded', 'true');
    });

    it('clicking an expandable section again collapses it', async () => {
      const { container } = render(<GamePage />);

      // Wait for content to load
      await screen.findAllByText('Energy Saver');

      const mobileBottomSheet = container.querySelector('.md\\:hidden');
      const expandButtons = mobileBottomSheet!.querySelectorAll('button[aria-expanded]');

      const leaderboardButton = Array.from(expandButtons).find((btn) =>
        btn.textContent?.includes('Leaderboard')
      );
      expect(leaderboardButton).toBeDefined();

      // Expand
      fireEvent.click(leaderboardButton!);
      expect(leaderboardButton).toHaveAttribute('aria-expanded', 'true');

      // Collapse
      fireEvent.click(leaderboardButton!);
      expect(leaderboardButton).toHaveAttribute('aria-expanded', 'false');
    });
  });

  describe('Horizontal scroll on CatalogueStrip', () => {
    it('CatalogueStrip container has overflow-x-auto for horizontal scrolling', () => {
      const rapps = [
        { id: 1, name: 'Energy Saver', purpose: 'Test', cost: 50, benefit: 'Test', risk: 10, confidence: 90, sideEffects: 'None', impact: { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 } },
        { id: 2, name: 'Capacity Optimiser', purpose: 'Test', cost: 75, benefit: 'Test', risk: 10, confidence: 90, sideEffects: 'None', impact: { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 } },
      ];

      const { container } = render(
        <DragProvider>
          <CatalogueStrip rapps={rapps} onDeploy={vi.fn()} dragState={null} />
        </DragProvider>
      );

      // The outer container should have overflow-x-auto
      const scrollContainer = container.querySelector('.overflow-x-auto');
      expect(scrollContainer).toBeInTheDocument();
    });

    it('CatalogueStrip cards use flex-shrink-0 to prevent shrinking in scroll container', () => {
      const rapps = [
        { id: 1, name: 'Energy Saver', purpose: 'Test', cost: 50, benefit: 'Test', risk: 10, confidence: 90, sideEffects: 'None', impact: { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 } },
        { id: 2, name: 'Capacity Optimiser', purpose: 'Test', cost: 75, benefit: 'Test', risk: 10, confidence: 90, sideEffects: 'None', impact: { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 } },
        { id: 3, name: 'Fault Predictor', purpose: 'Test', cost: 100, benefit: 'Test', risk: 20, confidence: 80, sideEffects: 'None', impact: { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 } },
      ];

      const { container } = render(
        <DragProvider>
          <CatalogueStrip rapps={rapps} onDeploy={vi.fn()} dragState={null} />
        </DragProvider>
      );

      // Each card should have flex-shrink-0 to maintain width in horizontal scroll
      const cards = container.querySelectorAll('.flex-shrink-0');
      expect(cards.length).toBe(3);
    });

    it('CatalogueStrip inner container uses flex layout with min-w-min for scroll content', () => {
      const rapps = [
        { id: 1, name: 'Energy Saver', purpose: 'Test', cost: 50, benefit: 'Test', risk: 10, confidence: 90, sideEffects: 'None', impact: { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 } },
      ];

      const { container } = render(
        <DragProvider>
          <CatalogueStrip rapps={rapps} onDeploy={vi.fn()} dragState={null} />
        </DragProvider>
      );

      // The inner flex container should have min-w-min to allow content to overflow
      const flexContainer = container.querySelector('.flex.gap-2');
      expect(flexContainer).toBeInTheDocument();
      expect(flexContainer).toHaveClass('min-w-min');
    });

    it('CatalogueStrip cards have fixed width (w-28) for consistent horizontal layout', () => {
      const rapps = [
        { id: 1, name: 'Energy Saver', purpose: 'Test', cost: 50, benefit: 'Test', risk: 10, confidence: 90, sideEffects: 'None', impact: { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 } },
        { id: 2, name: 'Capacity Optimiser', purpose: 'Test', cost: 75, benefit: 'Test', risk: 10, confidence: 90, sideEffects: 'None', impact: { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 } },
      ];

      const { container } = render(
        <DragProvider>
          <CatalogueStrip rapps={rapps} onDeploy={vi.fn()} dragState={null} />
        </DragProvider>
      );

      // Cards should have w-28 for fixed width in the horizontal strip
      const cards = container.querySelectorAll('.w-28');
      expect(cards.length).toBe(2);
    });
  });
});
