import { motion, AnimatePresence } from 'framer-motion';
import { type ReactNode, useState } from 'react';

interface TooltipProps {
  content: ReactNode;
  children: ReactNode;
  className?: string;
  /** Which side to show the tooltip. Defaults to 'top'. */
  side?: 'top' | 'right' | 'bottom' | 'left';
}

const sideClasses: Record<NonNullable<TooltipProps['side']>, string> = {
  top:    'bottom-full left-1/2 -translate-x-1/2 mb-2',
  bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
  right:  'left-full top-1/2 -translate-y-1/2 ml-2',
  left:   'right-full top-1/2 -translate-y-1/2 mr-2',
};

const sideMotion = {
  top:    { initial: { opacity: 0, y: 4  }, animate: { opacity: 1, y: 0  } },
  bottom: { initial: { opacity: 0, y: -4 }, animate: { opacity: 1, y: 0  } },
  right:  { initial: { opacity: 0, x: -4 }, animate: { opacity: 1, x: 0  } },
  left:   { initial: { opacity: 0, x: 4  }, animate: { opacity: 1, x: 0  } },
} as const;

export function Tooltip({ content, children, className = '', side = 'top' }: TooltipProps) {
  const [isVisible, setIsVisible] = useState(false);
  const motion_ = sideMotion[side];

  return (
    <div
      className={`relative ${className}`}
      onMouseEnter={() => setIsVisible(true)}
      onMouseLeave={() => setIsVisible(false)}
    >
      {children}
      <AnimatePresence>
        {isVisible && (
          <motion.div
            className={`absolute z-50 w-64 rounded-lg bg-surface-lighter border border-surface-lighter shadow-xl ${sideClasses[side]}`}
            initial={motion_.initial}
            animate={motion_.animate}
            exit={motion_.initial}
            transition={{ duration: 0.15 }}
            role="tooltip"
          >
            {content}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
