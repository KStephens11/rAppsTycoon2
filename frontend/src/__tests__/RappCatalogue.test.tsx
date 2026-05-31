import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { RappCatalogue } from '../components/game/RappCatalogue';

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

  it('renders rApp cards with name, cost, and deploy button', async () => {
    vi.mocked(apiGet).mockResolvedValueOnce(mockCatalogue);

    const onDeploy = vi.fn();
    render(<RappCatalogue onDeploy={onDeploy} />);

    await waitFor(() => {
      expect(screen.getByText('Energy Saver')).toBeInTheDocument();
    });

    expect(screen.getByText('Fault Predictor')).toBeInTheDocument();
    expect(screen.getByText('€50')).toBeInTheDocument();
    expect(screen.getByText('€75')).toBeInTheDocument();

    // Deploy buttons (the explicit <Button> elements with exact "Deploy X" aria-label)
    const deployButtons = screen.getAllByRole('button', { name: /^Deploy /i });
    expect(deployButtons.length).toBeGreaterThanOrEqual(2);
  });

  it('calls onDeploy when deploy button is clicked', async () => {
    vi.mocked(apiGet).mockResolvedValueOnce(mockCatalogue);

    const onDeploy = vi.fn();
    render(<RappCatalogue onDeploy={onDeploy} />);

    await waitFor(() => {
      expect(screen.getByText('Energy Saver')).toBeInTheDocument();
    });

    // Click the specific "Deploy Energy Saver" button (the small button, not the card)
    const deployButton = screen.getByRole('button', { name: 'Deploy Energy Saver' });
    fireEvent.click(deployButton);

    expect(onDeploy).toHaveBeenCalledWith(mockCatalogue.rapps[0]);
  });
});
