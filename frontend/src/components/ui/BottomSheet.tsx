import { useState, type ReactNode } from 'react';
import { motion, useMotionValue, useTransform } from 'framer-motion';
import { GripHorizontal } from 'lucide-react';

interface PanInfo {
  offset: { x: number; y: number };
  delta: { x: number; y: number };
  velocity: { x: number; y: number };
}

interface BottomSheetProps {
  /** The always-visible strip content (e.g. CatalogueStrip) shown when collapsed */
  strip?: ReactNode;
  /** The expandable content (e.g. Events and Leaderboard sections) */
  children: ReactNode;
}

/**
 * Mobile bottom sheet that shows the catalogue strip when collapsed
 * and reveals Events/Leaderboard expandable sections when dragged up.
 * Only visible on mobile (< 768px).
 */
export function BottomSheet({ strip, children }: BottomSheetProps) {
  const [expanded, setExpanded] = useState(false);
  const y = useMotionValue(0);
  const opacity = useTransform(y, [-200, 0], [1, 0.6]);

  const handleDragEnd = (_: unknown, info: PanInfo) => {
    if (info.offset.y < -50) {
      setExpanded(true);
    } else if (info.offset.y > 50) {
      setExpanded(false);
    }
  };

  return (
    <motion.div
      className="fixed inset-x-0 bottom-0 z-40 md:hidden bg-surface border-t border-surface-lighter rounded-t-2xl shadow-2xl"
      animate={{ height: expanded ? '70vh' : 'auto' }}
      transition={{ type: 'spring', stiffness: 400, damping: 35 }}
      style={{ opacity }}
    >
      {/* Drag handle */}
      <motion.div
        className="flex items-center justify-center py-2 cursor-grab active:cursor-grabbing"
        drag="y"
        dragConstraints={{ top: 0, bottom: 0 }}
        dragElastic={0.1}
        onDragEnd={handleDragEnd}
        style={{ y }}
        onClick={() => setExpanded(!expanded)}
      >
        <GripHorizontal size={20} className="text-text-muted" />
      </motion.div>

      {/* Always-visible strip (CatalogueStrip) */}
      {strip && (
        <div className="px-1 pb-[15px]">
          {strip}
        </div>
      )}

      {/* Expandable content (Events + Leaderboard sections) */}
      <div className={`overflow-y-auto ${expanded ? 'max-h-[calc(70vh-100px)]' : 'max-h-0 overflow-hidden'}`}>
        {children}
      </div>
    </motion.div>
  );
}
