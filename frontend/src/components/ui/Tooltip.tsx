import { motion, AnimatePresence } from 'framer-motion';
import { type ReactNode, useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';

interface TooltipProps {
  content: ReactNode;
  children: ReactNode;
  className?: string;
  /** Which side to show the tooltip. Defaults to 'top'. */
  side?: 'top' | 'right' | 'bottom' | 'left';
  /** When true, the tooltip is suppressed */
  disabled?: boolean;
}

const GAP = 8; // px gap between trigger and tooltip
const TOOLTIP_WIDTH = 256; // w-64

interface Coords { top: number; left: number }

function computeCoords(
  rect: DOMRect,
  side: NonNullable<TooltipProps['side']>,
): Coords {
  const vw = window.innerWidth;
  let left: number;
  let top: number;

  switch (side) {
    case 'right':
      top = rect.top + rect.height / 2;
      left = rect.right + GAP;
      break;
    case 'left':
      top = rect.top + rect.height / 2;
      left = rect.left - GAP - TOOLTIP_WIDTH;
      break;
    case 'bottom':
      top = rect.bottom + GAP;
      left = rect.left + rect.width / 2 - TOOLTIP_WIDTH / 2;
      break;
    case 'top':
    default:
      top = rect.top - GAP;
      left = rect.left + rect.width / 2 - TOOLTIP_WIDTH / 2;
      break;
  }

  // Clamp horizontally so it doesn't overflow the viewport edges
  const EDGE_PADDING = 8;
  if (left < EDGE_PADDING) left = EDGE_PADDING;
  if (left + TOOLTIP_WIDTH > vw - EDGE_PADDING) left = vw - EDGE_PADDING - TOOLTIP_WIDTH;

  return { top, left };
}

const sideMotion = {
  top:    { initial: { opacity: 0, y: 4  }, animate: { opacity: 1, y: 0  } },
  bottom: { initial: { opacity: 0, y: -4 }, animate: { opacity: 1, y: 0  } },
  right:  { initial: { opacity: 0, x: -6 }, animate: { opacity: 1, x: 0  } },
  left:   { initial: { opacity: 0, x: 6  }, animate: { opacity: 1, x: 0  } },
} as const;

export function Tooltip({ content, children, className = '', side = 'top', disabled = false }: TooltipProps) {
  const [isVisible, setIsVisible] = useState(false);
  const [coords, setCoords] = useState<Coords>({ top: 0, left: 0 });
  const triggerRef = useRef<HTMLDivElement>(null);

  const updateCoords = useCallback(() => {
    if (triggerRef.current) {
      setCoords(computeCoords(triggerRef.current.getBoundingClientRect(), side));
    }
  }, [side]);

  const handleMouseEnter = useCallback(() => {
    if (disabled) return;
    updateCoords();
    setIsVisible(true);
  }, [updateCoords, disabled]);

  // Hide when disabled changes to true
  useEffect(() => {
    if (disabled) setIsVisible(false);
  }, [disabled]);

  // Keep position in sync while visible (handles scroll inside the panel)
  useEffect(() => {
    if (!isVisible) return;
    const id = setInterval(updateCoords, 100);
    return () => clearInterval(id);
  }, [isVisible, updateCoords]);

  const motion_ = sideMotion[side];

  // For 'top' side, use bottom positioning so the tooltip grows upward
  const positionStyle = side === 'top'
    ? { bottom: window.innerHeight - coords.top, left: coords.left }
    : side === 'right' || side === 'left'
    ? { top: coords.top, left: coords.left, transform: 'translateY(-50%)' }
    : { top: coords.top, left: coords.left };

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
              style={positionStyle}
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
