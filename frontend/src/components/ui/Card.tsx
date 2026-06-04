import { motion } from 'framer-motion';
import { type ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  header?: ReactNode;
  className?: string;
}

export function Card({ children, header, className = '' }: CardProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className={`rounded-xl border border-surface-lighter bg-surface-light p-4 ${className}`}
    >
      {header && (
        <div className="mb-3 border-b border-surface-lighter pb-3 text-sm font-semibold text-text">
          {header}
        </div>
      )}
      {children}
    </motion.div>
  );
}
