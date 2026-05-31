import { useState, type ReactNode } from 'react';
import { motion, useMotionValue, useTransform } from 'framer-motion';
import { GripHorizontal } from 'lucide-react';

interface PanInfo {
  offset: { x: number; y: number };
  delta: { x: number; y: number };
  velocity: { x: number; y: number };
}

interface BottomSheetProps {
  children: ReactNode;
}

/**
 * Mobile bottom sheet that shows sidebar content.
 * Starts collapsed (showing just the tab bar), can be dragged up to reveal full content.
 */
export function BottomSheet({ children }: BottomSheetProps) {
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
      animate={{ height: expanded ? '70vh' : '56px' }}
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

      {/* Content */}
      <div className={`overflow-y-auto ${expanded ? 'h-[calc(70vh-40px)]' : 'h-0 overflow-hidden'}`}>
        {children}
      </div>
    </motion.div>
  );
}
