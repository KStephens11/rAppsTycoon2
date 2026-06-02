import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LobbyPage } from '../pages/LobbyPage';

// Mock the API module
vi.mock('../services/api', () => ({
  apiPost: vi.fn(),
  apiGet: vi.fn(),
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
const mockSetSession = vi.fn();
const mockSetPlayers = vi.fn();
const mockGameState = {
  sessionCode: null,
  token: null,
  playerId: null,
  isHost: false,
  gameState: null,
  players: [],
  finalLeaderboard: null,
  setSession: mockSetSession,
  setPlayers: mockSetPlayers,
  setGameState: vi.fn(),
  setFinalLeaderboard: vi.fn(),
  reset: vi.fn(),
};

vi.mock('../context/GameContext', () => ({
  useGame: () => mockGameState,
  GameProvider: ({ children }: { children: React.ReactNode }) => children,
}));

import { apiPost } from '../services/api';

describe('LobbyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGameState.gameState = null;
  });

  it('renders Create Game and Join Game forms', () => {
    render(<LobbyPage />);

    expect(screen.getByText('Create Game')).toBeInTheDocument();
    expect(screen.getByText('Join Game')).toBeInTheDocument();
    // Both forms have "Display name" inputs
    expect(screen.getAllByLabelText('Display name')).toHaveLength(2);
    expect(screen.getByLabelText('Session code')).toBeInTheDocument();
  });

  it('calls API on Create Game form submission', async () => {
    const mockResponse = {
      sessionCode: 'ABCD1234',
      sessionId: 1,
      hostPlayer: { id: 1, displayName: 'TestPlayer', sessionToken: 'token123' },
      state: 'LOBBY',
      maxPlayers: 6,
    };
    vi.mocked(apiPost).mockResolvedValueOnce(mockResponse);

    render(<LobbyPage />);

    // First "Enter your name" input is in the Create Game form
    const nameInputs = screen.getAllByPlaceholderText('Enter your name');
    fireEvent.change(nameInputs[0], { target: { value: 'TestPlayer' } });

    const createButton = screen.getByRole('button', { name: /create session/i });
    fireEvent.click(createButton);

    await waitFor(() => {
      expect(apiPost).toHaveBeenCalledWith('/api/sessions', { hostName: 'TestPlayer' });
    });

    await waitFor(() => {
      expect(mockSetSession).toHaveBeenCalledWith({
        sessionCode: 'ABCD1234',
        token: 'token123',
        playerId: 1,
        isHost: true,
      });
    });
  });

  it('calls API on Join Game form submission', async () => {
    const mockResponse = {
      player: { id: 2, displayName: 'Joiner', sessionToken: 'token456' },
      session: {
        sessionCode: 'ABCD1234',
        state: 'LOBBY',
        players: [
          { id: 1, displayName: 'Host', isHost: true },
          { id: 2, displayName: 'Joiner', isHost: false },
        ],
        maxPlayers: 6,
      },
    };
    vi.mocked(apiPost).mockResolvedValueOnce(mockResponse);

    render(<LobbyPage />);

    const codeInput = screen.getByLabelText('Session code');
    fireEvent.change(codeInput, { target: { value: 'ABCD1234' } });

    // The Join Game form has its own name input — it's the second "Enter your name" placeholder
    const nameInputs = screen.getAllByPlaceholderText('Enter your name');
    fireEvent.change(nameInputs[1], { target: { value: 'Joiner' } });

    const joinButton = screen.getByRole('button', { name: /^join$/i });
    fireEvent.click(joinButton);

    await waitFor(() => {
      expect(apiPost).toHaveBeenCalledWith('/api/sessions/ABCD1234/join', {
        displayName: 'Joiner',
      });
    });

    await waitFor(() => {
      expect(mockSetSession).toHaveBeenCalledWith({
        sessionCode: 'ABCD1234',
        token: 'token456',
        playerId: 2,
        isHost: false,
      });
    });
  });
});
