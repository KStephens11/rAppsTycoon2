import { motion, AnimatePresence } from 'framer-motion';
import { type ReactNode, useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';

interface TooltipProps {
  content: ReactNode;
  children: ReactNode;
  className?: string;
  /** Which side to show the tooltip. Defaults to 'top'. */
  side?: 'top' | 'right' | 'bottom' | 'left';
}

const GAP = 8; // px gap between trigger and tooltip
const TOOLTIP_WIDTH = 256; // w-64

interface Coords { top: number; left: number }

function computeCoords(
  rect: DOMRect,
  side: NonNullable<TooltipProps['side']>,
): Coords {
  switch (side) {
    case 'right':
      return {
        top:  rect.top + rect.height / 2,
        left: rect.right + GAP,
      };
    case 'left':
      return {
        top:  rect.top + rect.height / 2,
        left: rect.left - GAP - TOOLTIP_WIDTH,
      };
    case 'bottom':
      return {
        top:  rect.bottom + GAP,
        left: rect.left + rect.width / 2 - TOOLTIP_WIDTH / 2,
      };
    case 'top':
    default:
      return {
        top:  rect.top - GAP,  // tooltip positioned via translateY(-100%)
        left: rect.left + rect.width / 2 - TOOLTIP_WIDTH / 2,
      };
  }
}

const sideTransform: Record<NonNullable<TooltipProps['side']>, string> = {
  top:    'translateY(-100%)',
  bottom: 'translateY(0)',
  right:  'translateY(-50%)',
  left:   'translateY(-50%)',
};

const sideMotion = {
  top:    { initial: { opacity: 0, y: 4  }, animate: { opacity: 1, y: 0  } },
  bottom: { initial: { opacity: 0, y: -4 }, animate: { opacity: 1, y: 0  } },
  right:  { initial: { opacity: 0, x: -6 }, animate: { opacity: 1, x: 0  } },
  left:   { initial: { opacity: 0, x: 6  }, animate: { opacity: 1, x: 0  } },
} as const;

export function Tooltip({ content, children, className = '', side = 'top' }: TooltipProps) {
  const [isVisible, setIsVisible] = useState(false);
  const [coords, setCoords] = useState<Coords>({ top: 0, left: 0 });
  const triggerRef = useRef<HTMLDivElement>(null);

  const updateCoords = useCallback(() => {
    if (triggerRef.current) {
      setCoords(computeCoords(triggerRef.current.getBoundingClientRect(), side));
    }
  }, [side]);

  const handleMouseEnter = useCallback(() => {
    updateCoords();
    setIsVisible(true);
  }, [updateCoords]);

  // Keep position in sync while visible (handles scroll inside the panel)
  useEffect(() => {
    if (!isVisible) return;
    const id = setInterval(updateCoords, 100);
    return () => clearInterval(id);
  }, [isVisible, updateCoords]);

  const motion_ = sideMotion[side];
  const transform = sideTransform[side];

  return (
    <div
      ref={triggerRef}
      className={className}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={() => setIsVisible(false)}
    >
      {children}
      {createPortal(
        <AnimatePresence>
          {isVisible && (
            <motion.div
              className="fixed z-[9999] w-64 rounded-lg bg-surface-lighter border border-surface-lighter/80 shadow-2xl pointer-events-none"
              style={{ top: coords.top, left: coords.left, transform }}
              initial={motion_.initial}
              animate={motion_.animate}
              exit={motion_.initial}
              transition={{ duration: 0.15 }}
              role="tooltip"
            >
              {content}
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </div>
  );
}
