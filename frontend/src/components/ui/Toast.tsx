import { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, AlertCircle } from 'lucide-react';

export interface ToastMessage {
  id: string;
  message: string;
  type: 'error' | 'success' | 'info';
}

interface ToastProps {
  toasts: ToastMessage[];
  onDismiss: (id: string) => void;
}

export function ToastContainer({ toasts, onDismiss }: ToastProps) {
  return (
    <div
      className="flex flex-col gap-2 max-w-sm"
      aria-live="polite"
      aria-atomic="false"
      role="status"
    >
      <AnimatePresence>
        {toasts.map((toast) => (
          <Toast key={toast.id} toast={toast} onDismiss={onDismiss} />
        ))}
      </AnimatePresence>
    </div>
  );
}

function Toast({ toast, onDismiss }: { toast: ToastMessage; onDismiss: (id: string) => void }) {
  useEffect(() => {
    const timer = setTimeout(() => onDismiss(toast.id), 5000);
    return () => clearTimeout(timer);
  }, [toast.id, onDismiss]);

  const bgColor = 'bg-surface-light border-surface-lighter';
  const textColor = 'text-white';

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.3 }}
      className={`flex items-start gap-3 rounded-lg border p-3 ${bgColor}`}
    >
      <AlertCircle size={18} className={`mt-0.5 shrink-0 ${textColor}`} />
      <p className="text-sm text-white flex-1">{toast.message}</p>
      <button
        onClick={() => onDismiss(toast.id)}
        className="shrink-0 text-white hover:text-white/70 transition-colors cursor-pointer"
        aria-label="Dismiss notification"
      >
        <X size={14} />
      </button>
    </motion.div>
  );
}
