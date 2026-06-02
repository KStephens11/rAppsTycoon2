import { useCallback, useState } from 'react';
import {
  Zap,
  Maximize,
  Shield,
  FileCheck,
  Settings,
  GitBranch,
  BellOff,
  type LucideIcon,
} from 'lucide-react';
import { useDrag, type DragState } from '../../context/DragContext';
import { Badge } from '../ui/Badge';
import { DeploymentPicker } from './DeploymentPicker';
import type { RappTemplate } from './RappCatalogue';

export interface CatalogueStripProps {
  rapps: RappTemplate[];
  onDeploy: (rapp: RappTemplate) => void;
  dragState: DragState | null;
  basestations?: Array<{ id: number; name: string }>;
  onConfirmDeploy?: (templateId: number, basestationId: number) => void;
}

const rappIcons: Record<string, LucideIcon> = {
  'Energy Saver': Zap,
  'Capacity Optimiser': Maximize,
  'Fault Predictor': Shield,
  'SLA Guardian': FileCheck,
  'Configuration Drift Detector': Settings,
  'Traffic Balancer': GitBranch,
  'Alarm Noise Reducer': BellOff,
};

function getRappIcon(name: string): LucideIcon {
  return rappIcons[name] || Settings;
}

/**
 * A horizontally scrollable compact version of the rApp catalogue
 * designed for mobile bottom sheet usage. Each card is draggable
 * and supports tap-to-deploy via DeploymentPicker.
 */
export function CatalogueStrip({
  rapps,
  onDeploy,
  dragState: dragStateProp,
  basestations,
  onConfirmDeploy,
}: CatalogueStripProps) {
  const { startDrag, endDrag } = useDrag();
  const [pickerOpenForId, setPickerOpenForId] = useState<number | null>(null);

  const handleDragStart = useCallback(
    (e: React.DragEvent<HTMLDivElement>, rapp: RappTemplate) => {
      e.dataTransfer.setData('text/plain', String(rapp.id));
      e.dataTransfer.effectAllowed = 'move';
      // Hide the native drag ghost image — we use a custom DragPreview instead
      const emptyImg = new Image();
      emptyImg.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
      e.dataTransfer.setDragImage(emptyImg, 0, 0);
      startDrag({ templateId: rapp.id, name: rapp.name, icon: rapp.name });
    },
    [startDrag]
  );

  const handleDragEnd = useCallback(() => {
    endDrag();
  }, [endDrag]);

  const handleCardActivate = useCallback(
    (rapp: RappTemplate) => {
      if (basestations && onConfirmDeploy) {
        setPickerOpenForId(rapp.id);
      } else {
        onDeploy(rapp);
      }
    },
    [basestations, onConfirmDeploy, onDeploy]
  );

  return (
    <div className="w-full overflow-x-auto">
      <div className="flex gap-2 px-2 py-2 min-w-min">
        {rapps.map((rapp) => {
          const Icon = getRappIcon(rapp.name);
          const isDragging = dragStateProp?.templateId === rapp.id;

          return (
            <div
              key={rapp.id}
              draggable
              tabIndex={0}
              role="button"
              aria-label={`Deploy ${rapp.name} - €${rapp.cost}`}
              onDragStart={(e) => handleDragStart(e, rapp)}
              onDragEnd={handleDragEnd}
              onClick={() => handleCardActivate(rapp)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  handleCardActivate(rapp);
                }
              }}
              className={`relative flex-shrink-0 w-28 p-2 rounded-lg bg-surface-light border border-surface-lighter hover:border-primary/30 focus:border-primary/50 focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors cursor-grab ${
                isDragging ? 'opacity-50' : ''
              }`}
            >
              <div className="flex flex-col items-center gap-1 text-center">
                <div className="w-7 h-7 rounded-md bg-primary/10 flex items-center justify-center">
                  <Icon size={14} className="text-primary" />
                </div>
                <span className="text-xs font-semibold text-text truncate w-full">
                  {rapp.name}
                </span>
                <Badge variant="warning">€{rapp.cost}</Badge>
              </div>

              {pickerOpenForId === rapp.id && basestations && onConfirmDeploy && (
                <DeploymentPicker
                  templateId={rapp.id}
                  templateName={rapp.name}
                  basestations={basestations}
                  onSelect={(basestationId) => {
                    onConfirmDeploy(rapp.id, basestationId);
                    setPickerOpenForId(null);
                  }}
                  onClose={() => setPickerOpenForId(null)}
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
