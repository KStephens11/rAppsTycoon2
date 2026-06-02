import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Leaderboard } from '../components/game/Leaderboard';
import type { LeaderboardEntry } from '../hooks/useGameState';

const mockEntries: LeaderboardEntry[] = [
  {
    rank: 1,
    playerId: 1,
    displayName: 'Alice',
    compositeScore: 95.5,
    scores: { money: 90, customerSatisfaction: 98, networkStability: 98 },
  },
  {
    rank: 2,
    playerId: 2,
    displayName: 'Bob',
    compositeScore: 82.3,
    scores: { money: 75, customerSatisfaction: 85, networkStability: 87 },
  },
  {
    rank: 3,
    playerId: 3,
    displayName: 'Charlie',
    compositeScore: 70.1,
    scores: { money: 60, customerSatisfaction: 75, networkStability: 75 },
  },
];

describe('Leaderboard', () => {
  it('renders all leaderboard entries with names and scores', () => {
    render(<Leaderboard entries={mockEntries} currentPlayerId={null} />);

    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('Charlie')).toBeInTheDocument();

    // Scores are rendered via AnimatedScore (ref-based), check initial text content
    expect(screen.getByText('95.5')).toBeInTheDocument();
    expect(screen.getByText('82.3')).toBeInTheDocument();
    expect(screen.getByText('70.1')).toBeInTheDocument();
  });

  it('shows crown icon for rank #1 and rank numbers for others', () => {
    render(<Leaderboard entries={mockEntries} currentPlayerId={null} />);

    // Rank #1 should NOT show "#1" text — it shows a crown icon instead
    expect(screen.queryByText('#1')).not.toBeInTheDocument();

    // Other ranks show their number
    expect(screen.getByText('#2')).toBeInTheDocument();
    expect(screen.getByText('#3')).toBeInTheDocument();
  });

  it('highlights the current player row with "(you)" indicator', () => {
    render(<Leaderboard entries={mockEntries} currentPlayerId={2} />);

    expect(screen.getByText('(you)')).toBeInTheDocument();
  });
});
