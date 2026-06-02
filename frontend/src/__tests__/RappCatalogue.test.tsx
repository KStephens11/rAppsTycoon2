import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { RappCatalogue } from '../components/game/RappCatalogue';
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

import { apiGet } from '../services/api';

const mockCatalogue = {
  rapps: [
    {
      id: 1,
      name: 'Energy Saver',
      purpose: 'Reduces energy consumption',
      cost: 50,
      benefit: 'Reduces energy costs by optimising power usage',
      risk: 15,
      confidence: 85,
      sideEffects: 'May slightly reduce throughput',
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
      name: 'Fault Predictor',
      purpose: 'Predicts faults before they occur',
      cost: 75,
      benefit: 'Prevents outages with predictive maintenance',
      risk: 30,
      confidence: 70,
      sideEffects: 'False positives possible',
      impact: {
        health: 15,
        customerExperience: 10,
        cost: 0,
        energyEfficiency: 0,
        automationReliability: 20,
        slaCompliance: 10,
      },
    },
  ],
};

describe('RappCatalogue', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders rApp cards with name and cost', async () => {
    vi.mocked(apiGet).mockResolvedValueOnce(mockCatalogue);

    const onDeploy = vi.fn();
    render(
      <DragProvider>
        <RappCatalogue onDeploy={onDeploy} />
      </DragProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Energy Saver')).toBeInTheDocument();
    });

    expect(screen.getByText('Fault Predictor')).toBeInTheDocument();
    expect(screen.getByText('€50')).toBeInTheDocument();
    expect(screen.getByText('€75')).toBeInTheDocument();

    // Cards are draggable (role="button" with aria-label)
    const cards = screen.getAllByRole('button', { name: /^Deploy /i });
    expect(cards.length).toBe(2);
    cards.forEach((card) => {
      expect(card).toHaveAttribute('draggable', 'true');
    });
  });

  it('calls onDeploy when card is activated via keyboard', async () => {
    vi.mocked(apiGet).mockResolvedValueOnce(mockCatalogue);

    const onDeploy = vi.fn();
    render(
      <DragProvider>
        <RappCatalogue onDeploy={onDeploy} />
      </DragProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Energy Saver')).toBeInTheDocument();
    });

    // Activate via Enter key on the card
    const card = screen.getByRole('button', { name: /Deploy Energy Saver/i });
    fireEvent.keyDown(card, { key: 'Enter' });

    expect(onDeploy).toHaveBeenCalledWith(mockCatalogue.rapps[0]);
  });

  describe('drag behaviour', () => {
    it('onDragStart fires with correct template data in dataTransfer', async () => {
      vi.mocked(apiGet).mockResolvedValueOnce(mockCatalogue);

      render(
        <DragProvider>
          <RappCatalogue />
        </DragProvider>
      );

      await waitFor(() => {
        expect(screen.getByText('Energy Saver')).toBeInTheDocument();
      });

      const card = screen.getByRole('button', { name: /Deploy Energy Saver/i });

      const setData = vi.fn();
      const dataTransfer = {
        setData,
        setDragImage: vi.fn(),
        effectAllowed: '',
      };

      fireEvent.dragStart(card, { dataTransfer });

      // Verify dataTransfer was set with the correct template ID
      expect(setData).toHaveBeenCalledWith('text/plain', '1');
      expect(dataTransfer.effectAllowed).toBe('move');
    });

    it('source card dims with opacity-50 class during drag', async () => {
      vi.mocked(apiGet).mockResolvedValueOnce(mockCatalogue);

      render(
        <DragProvider>
          <RappCatalogue />
        </DragProvider>
      );

      await waitFor(() => {
        expect(screen.getByText('Energy Saver')).toBeInTheDocument();
      });

      const card = screen.getByRole('button', { name: /Deploy Energy Saver/i });

      // Before drag, card should not have opacity-50
      expect(card).not.toHaveClass('opacity-50');

      const dataTransfer = {
        setData: vi.fn(),
        setDragImage: vi.fn(),
        effectAllowed: '',
      };

      fireEvent.dragStart(card, { dataTransfer });

      // After drag starts, the card should have opacity-50
      expect(card).toHaveClass('opacity-50');
    });

    it('onDragEnd resets visual state (removes opacity-50)', async () => {
      vi.mocked(apiGet).mockResolvedValueOnce(mockCatalogue);

      render(
        <DragProvider>
          <RappCatalogue />
        </DragProvider>
      );

      await waitFor(() => {
        expect(screen.getByText('Energy Saver')).toBeInTheDocument();
      });

      const card = screen.getByRole('button', { name: /Deploy Energy Saver/i });

      const dataTransfer = {
        setData: vi.fn(),
        setDragImage: vi.fn(),
        effectAllowed: '',
      };

      // Start drag
      fireEvent.dragStart(card, { dataTransfer });
      expect(card).toHaveClass('opacity-50');

      // End drag
      fireEvent.dragEnd(card);

      // After drag ends, opacity-50 should be removed
      expect(card).not.toHaveClass('opacity-50');
    });

    it('only the dragged card dims, not other cards', async () => {
      vi.mocked(apiGet).mockResolvedValueOnce(mockCatalogue);

      render(
        <DragProvider>
          <RappCatalogue />
        </DragProvider>
      );

      await waitFor(() => {
        expect(screen.getByText('Energy Saver')).toBeInTheDocument();
      });

      const energySaverCard = screen.getByRole('button', { name: /Deploy Energy Saver/i });
      const faultPredictorCard = screen.getByRole('button', { name: /Deploy Fault Predictor/i });

      const dataTransfer = {
        setData: vi.fn(),
        setDragImage: vi.fn(),
        effectAllowed: '',
      };

      // Drag the first card
      fireEvent.dragStart(energySaverCard, { dataTransfer });

      // Only the dragged card should be dimmed
      expect(energySaverCard).toHaveClass('opacity-50');
      expect(faultPredictorCard).not.toHaveClass('opacity-50');
    });
  });
});
