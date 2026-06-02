import { useEffect, useState } from 'react';
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
import { useDrag } from '../../context/DragContext';

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

export function DragPreview() {
  const { dragState } = useDrag();
  const [position, setPosition] = useState({ x: 0, y: 0 });

  useEffect(() => {
    if (!dragState) return;

    function handleMove(e: MouseEvent) {
      setPosition({ x: e.clientX, y: e.clientY });
    }

    function handleDragOver(e: DragEvent) {
      // preventDefault is required for dragover to fire continuously and to allow drops
      e.preventDefault();
      if (e.clientX !== 0 || e.clientY !== 0) {
        setPosition({ x: e.clientX, y: e.clientY });
      }
    }

    document.addEventListener('mousemove', handleMove);
    document.addEventListener('dragover', handleDragOver);

    return () => {
      document.removeEventListener('mousemove', handleMove);
      document.removeEventListener('dragover', handleDragOver);
    };
  }, [dragState]);

  if (!dragState) return null;

  const Icon = getRappIcon(dragState.icon);

  return (
    <div
      className="fixed z-50 pointer-events-none flex items-center gap-2 px-3 py-2 rounded-lg bg-surface-light border border-primary/40 shadow-lg"
      style={{
        left: position.x + 12,
        top: position.y + 12,
      }}
    >
      <div className="w-6 h-6 rounded-md bg-primary/10 flex items-center justify-center">
        <Icon size={14} className="text-primary" />
      </div>
      <span className="text-sm font-medium text-text whitespace-nowrap">
        {dragState.name}
      </span>
    </div>
  );
}
