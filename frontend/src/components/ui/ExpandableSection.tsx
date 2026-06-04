import { useState, type ReactNode } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown } from 'lucide-react';

interface ExpandableSectionProps {
  title: string;
  badge?: string | number;
  children: ReactNode;
  defaultExpanded?: boolean;
}

/**
 * A collapsible section with a tap-to-expand header.
 * Used in the mobile bottom sheet for Events and Leaderboard panels.
 */
export function ExpandableSection({
  title,
  badge,
  children,
  defaultExpanded = false,
}: ExpandableSectionProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);

  return (
    <div className="border-t border-surface-lighter">
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center justify-between px-3 py-2.5 text-left hover:bg-surface-light transition-colors"
        aria-expanded={expanded}
      >
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-text">{title}</span>
          {badge !== undefined && (
            <span className="text-[10px] font-bold bg-primary/20 text-primary px-1.5 py-0.5 rounded-full">
              {badge}
            </span>
          )}
        </div>
        <motion.div
          animate={{ rotate: expanded ? 180 : 0 }}
          transition={{ duration: 0.2 }}
        >
          <ChevronDown size={16} className="text-text-muted" />
        </motion.div>
      </button>

      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: 'easeInOut' }}
            className="overflow-hidden"
          >
            <div className="px-3 pb-3">
              {children}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
