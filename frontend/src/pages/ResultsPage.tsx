import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Crown, Trophy, RotateCcw, Home } from 'lucide-react';
import { useGame } from '../context/GameContext';
import { Confetti } from '../components/Confetti';
import { AnimatedScore } from '../components/AnimatedScore';

export function ResultsPage() {
  const { finalLeaderboard, reset } = useGame();
  const navigate = useNavigate();

  const handlePlayAgain = () => {
    reset();
    navigate('/');
  };

  const handleBackToHome = () => {
    reset();
    navigate('/');
  };

  if (!finalLeaderboard || finalLeaderboard.length === 0) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="text-center">
          <Trophy className="w-16 h-16 text-text-muted mx-auto mb-4" />
          <h1 className="text-2xl font-bold text-text mb-2">No Results Available</h1>
          <p className="text-text-muted mb-6">
            There are no game results to display.
          </p>
          <button
            onClick={handleBackToHome}
            className="px-6 py-3 bg-primary text-surface font-semibold rounded-lg hover:bg-primary-dark transition-colors"
          >
            Back to Lobby
          </button>
        </div>
      </div>
    );
  }

  const winner = finalLeaderboard[0];

  return (
    <div className="fixed inset-0 z-40 overflow-y-auto">
      {/* Dark gradient overlay */}
      <div className="min-h-full bg-gradient-to-b from-surface via-surface-light to-surface p-6 flex flex-col items-center">
        {/* Confetti for the winner */}
        <Confetti duration={6000} />

        {/* Winner Announcement */}
        <motion.div
          className="text-center mt-12 mb-10"
          initial={{ opacity: 0, scale: 0.5, y: -50 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          transition={{ duration: 0.8, type: 'spring', bounce: 0.4 }}
        >
          <motion.div
            className="inline-flex items-center justify-center mb-4"
            animate={{ rotate: [0, -5, 5, -5, 0] }}
            transition={{ duration: 1.5, delay: 0.8, repeat: 2 }}
          >
            <Crown className="w-16 h-16 text-warning" />
          </motion.div>

          <motion.h1
            className="text-5xl font-bold text-text mb-2"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3, duration: 0.6 }}
          >
            {winner.displayName}
          </motion.h1>

          <motion.p
            className="text-xl text-text-muted mb-2"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.6 }}
          >
            Winner!
          </motion.p>

          <motion.div
            className="text-4xl font-bold text-primary"
            initial={{ opacity: 0, scale: 0 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 1, type: 'spring', bounce: 0.5 }}
          >
            <AnimatedScore value={winner.compositeScore} delay={1} duration={2} />
            <span className="text-lg text-text-muted ml-2">pts</span>
          </motion.div>
        </motion.div>

        {/* Final Leaderboard */}
        <motion.div
          className="w-full max-w-2xl"
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 1.2, duration: 0.6 }}
        >
          <h2 className="text-2xl font-bold text-text mb-4 text-center">Final Standings</h2>

          <div className="space-y-3">
            {finalLeaderboard.map((entry, index) => (
              <motion.div
                key={entry.playerId}
                className={`rounded-xl p-4 ${
                  index === 0
                    ? 'bg-gradient-to-r from-warning/20 to-warning/5 border border-warning/30'
                    : 'bg-surface-light border border-surface-lighter'
                }`}
                initial={{ opacity: 0, x: -30 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 1.4 + index * 0.3, duration: 0.5 }}
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <span className="text-2xl font-bold text-text-muted w-8">
                      {index === 0 ? (
                        <Crown className="w-6 h-6 text-warning" />
                      ) : (
                        `#${entry.rank}`
                      )}
                    </span>
                    <span className="text-lg font-semibold text-text">
                      {entry.displayName}
                    </span>
                  </div>
                  <div className="text-xl font-bold text-primary">
                    <AnimatedScore
                      value={entry.compositeScore}
                      delay={1.6 + index * 0.3}
                      duration={1.5}
                    />
                    <span className="text-sm text-text-muted ml-1">pts</span>
                  </div>
                </div>

                {/* Score Breakdown */}
                <div className="grid grid-cols-3 gap-3">
                  <ScoreBreakdownItem
                    label="Money"
                    value={entry.scores.money}
                    suffix="€"
                    color="text-accent"
                    delay={1.8 + index * 0.3}
                  />
                  <ScoreBreakdownItem
                    label="Satisfaction"
                    value={entry.scores.customerSatisfaction}
                    suffix="%"
                    color="text-primary"
                    delay={2.0 + index * 0.3}
                  />
                  <ScoreBreakdownItem
                    label="Stability"
                    value={entry.scores.networkStability}
                    suffix="%"
                    color="text-warning"
                    delay={2.2 + index * 0.3}
                  />
                </div>
              </motion.div>
            ))}
          </div>
        </motion.div>

        {/* Action Buttons */}
        <motion.div
          className="flex gap-4 mt-10 mb-8"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 2.5, duration: 0.5 }}
        >
          <button
            onClick={handlePlayAgain}
            className="flex items-center gap-2 px-6 py-3 bg-primary text-surface font-semibold rounded-lg hover:bg-primary-dark transition-colors"
          >
            <RotateCcw className="w-5 h-5" />
            Play Again
          </button>
          <button
            onClick={handleBackToHome}
            className="flex items-center gap-2 px-6 py-3 bg-surface-lighter text-text font-semibold rounded-lg hover:bg-surface-light transition-colors border border-surface-lighter"
          >
            <Home className="w-5 h-5" />
            Back to Home
          </button>
        </motion.div>
      </div>
    </div>
  );
}

function ScoreBreakdownItem({
  label,
  value,
  suffix,
  color,
  delay,
}: {
  label: string;
  value: number;
  suffix: string;
  color: string;
  delay: number;
}) {
  return (
    <div className="bg-surface/50 rounded-lg p-2 text-center">
      <div className="text-xs text-text-muted mb-1">{label}</div>
      <div className={`text-sm font-bold ${color}`}>
        <AnimatedScore value={value} delay={delay} duration={1.2} />
        <span className="text-text-muted">{suffix}</span>
      </div>
    </div>
  );
}
