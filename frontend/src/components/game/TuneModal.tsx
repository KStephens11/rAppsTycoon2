import { useState } from 'react';
import { Sliders } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';

type Aggressiveness = 'LOW' | 'MODERATE' | 'HIGH';

interface TuneModalProps {
  isOpen: boolean;
  onClose: () => void;
  rappName: string;
  rappId: number;
  currentThreshold?: number;
  currentAggressiveness?: Aggressiveness;
  onConfirm: (rappId: number, threshold: number, aggressiveness: Aggressiveness) => Promise<void>;
}

export function TuneModal({
  isOpen,
  onClose,
  rappName,
  rappId,
  currentThreshold = 50,
  currentAggressiveness = 'MODERATE',
  onConfirm,
}: TuneModalProps) {
  const [threshold, setThreshold] = useState(currentThreshold);
  const [aggressiveness, setAggressiveness] = useState<Aggressiveness>(currentAggressiveness);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = () => {
    setError(null);
    setLoading(false);
    onClose();
  };

  const handleConfirm = async () => {
    setLoading(true);
    setError(null);
    try {
      await onConfirm(rappId, threshold, aggressiveness);
      handleClose();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Tune failed';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const aggressivenessOptions: { value: Aggressiveness; label: string; description: string }[] = [
    { value: 'LOW', label: 'Low', description: 'Conservative, minimal impact' },
    { value: 'MODERATE', label: 'Moderate', description: 'Balanced approach' },
    { value: 'HIGH', label: 'High', description: 'Aggressive, maximum effect' },
  ];

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={`Tune ${rappName}`}>
      <div className="space-y-5">
        {/* Header info */}
        <div className="flex items-center gap-2 text-text-muted">
          <Sliders size={16} />
          <span className="text-xs">Adjust configuration parameters</span>
        </div>

        {/* Threshold slider */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label htmlFor="threshold" className="text-xs font-medium text-text-muted">
              Threshold
            </label>
            <span className="text-sm font-medium text-primary tabular-nums">
              {threshold}
            </span>
          </div>
          <input
            id="threshold"
            type="range"
            min={1}
            max={100}
            value={threshold}
            onChange={(e) => setThreshold(Number(e.target.value))}
            className="w-full h-2 rounded-full appearance-none bg-surface-lighter cursor-pointer
              [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4
              [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-primary [&::-webkit-slider-thumb]:cursor-pointer
              [&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:h-4 [&::-moz-range-thumb]:rounded-full
              [&::-moz-range-thumb]:bg-primary [&::-moz-range-thumb]:border-0 [&::-moz-range-thumb]:cursor-pointer"
          />
          <div className="flex justify-between text-[10px] text-text-muted mt-1">
            <span>1</span>
            <span>100</span>
          </div>
        </div>

        {/* Aggressiveness radio buttons */}
        <div>
          <span className="block text-xs font-medium text-text-muted mb-2">
            Aggressiveness
          </span>
          <div className="space-y-1.5">
            {aggressivenessOptions.map((opt) => (
              <label
                key={opt.value}
                className={`flex items-center gap-3 p-2.5 rounded-lg border cursor-pointer transition-colors ${
                  aggressiveness === opt.value
                    ? 'border-primary bg-primary/5'
                    : 'border-surface-lighter hover:border-surface-light'
                }`}
              >
                <input
                  type="radio"
                  name="aggressiveness"
                  value={opt.value}
                  checked={aggressiveness === opt.value}
                  onChange={() => setAggressiveness(opt.value)}
                  className="sr-only"
                />
                <div
                  className={`w-3.5 h-3.5 rounded-full border-2 flex items-center justify-center ${
                    aggressiveness === opt.value
                      ? 'border-primary'
                      : 'border-surface-lighter'
                  }`}
                >
                  {aggressiveness === opt.value && (
                    <div className="w-1.5 h-1.5 rounded-full bg-primary" />
                  )}
                </div>
                <div className="flex-1">
                  <span className="text-sm font-medium text-text">{opt.label}</span>
                  <p className="text-[10px] text-text-muted">{opt.description}</p>
                </div>
              </label>
            ))}
          </div>
        </div>

        {/* Error message */}
        {error && (
          <div className="p-2.5 rounded-lg bg-danger/10 border border-danger/20">
            <p className="text-xs text-danger">{error}</p>
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-2 justify-end">
          <Button variant="secondary" size="sm" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleConfirm}
            disabled={loading}
          >
            {loading ? 'Applying...' : 'Apply'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
