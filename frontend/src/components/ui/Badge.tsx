import { type ReactNode } from 'react';

type BadgeVariant = 'info' | 'success' | 'warning' | 'danger';

interface BadgeProps {
  variant?: BadgeVariant;
  children: ReactNode;
  className?: string;
}

const variantStyles: Record<BadgeVariant, string> = {
  info: 'bg-primary/20 text-primary',
  success: 'bg-accent/20 text-accent',
  warning: 'bg-warning/20 text-warning',
  danger: 'bg-danger/20 text-danger',
};

export function Badge({ variant = 'info', children, className = '' }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${variantStyles[variant]} ${className}`}
    >
      {children}
    </span>
  );
}
