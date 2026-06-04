import { useState } from 'react';
import { Rocket } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import type { RappTemplate } from './RappCatalogue';

interface Basestation {
  id: number;
  name: string;
}

interface DeployModalProps {
  isOpen: boolean;
  onClose: () => void;
  rapp: RappTemplate | null;
  basestations: Basestation[];
  preSelectedBasestationId?: number | null;
  onConfirm: (templateId: number, basestationId: number) => Promise<void>;
}

export function DeployModal({
  isOpen,
  onClose,
  rapp,
  basestations,
  preSelectedBasestationId,
  onConfirm,
}: DeployModalProps) {
  const [selectedBsId, setSelectedBsId] = useState<number | null>(
    preSelectedBasestationId ?? null,
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reset state when modal opens with new rApp
  const handleClose = () => {
    setError(null);
    setLoading(false);
    onClose();
  };

  const handleConfirm = async () => {
    if (!rapp || !selectedBsId) return;
    setLoading(true);
    setError(null);
    try {
      await onConfirm(rapp.id, selectedBsId);
      handleClose();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Deployment failed';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  if (!rapp) return null;

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Deploy rApp">
      <div className="space-y-4">
        {/* rApp info */}
        <div className="flex items-start gap-3 p-3 rounded-lg bg-surface-lighter">
          <Rocket size={20} className="text-primary shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-sm font-semibold text-text">{rapp.name}</span>
              <Badge variant="warning">€{rapp.cost}</Badge>
            </div>
            <p className="text-xs text-text-muted">{rapp.benefit}</p>
          </div>
        </div>

        {/* Target basestation selector */}
        <div>
          <label
            htmlFor="bs-select"
            className="block text-xs font-medium text-text-muted mb-1.5"
          >
            Target Basestation
          </label>
          <select
            id="bs-select"
            value={selectedBsId ?? ''}
            onChange={(e) => setSelectedBsId(Number(e.target.value) || null)}
            className="w-full rounded-lg border border-surface-lighter bg-surface px-3 py-2 text-sm text-text focus:outline-none focus:border-primary"
          >
            <option value="">Select a basestation...</option>
            {basestations.map((bs) => (
              <option key={bs.id} value={bs.id}>
                {bs.name}
              </option>
            ))}
          </select>
        </div>

        {/* Cost preview */}
        <div className="p-2.5 rounded-lg bg-warning/10 border border-warning/20">
          <p className="text-xs text-warning">
            €{rapp.cost} will be deducted from your balance
          </p>
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
            disabled={!selectedBsId || loading}
          >
            {loading ? 'Deploying...' : 'Confirm Deploy'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
