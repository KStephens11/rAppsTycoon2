import '@testing-library/jest-dom/vitest';
import React from 'react';

// Mock framer-motion to avoid animation issues in tests
vi.mock('framer-motion', async () => {
  return {
    AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    motion: {
      div: React.forwardRef(({ children, initial, animate, exit, transition, layout, whileHover, whileTap, ...rest }: any, ref: any) => (
        <div ref={ref} {...rest}>{children}</div>
      )),
      button: React.forwardRef(({ children, initial, animate, exit, transition, whileHover, whileTap, ...rest }: any, ref: any) => (
        <button ref={ref} {...rest}>{children}</button>
      )),
      span: React.forwardRef(({ children, initial, animate, exit, transition, ...rest }: any, ref: any) => (
        <span ref={ref} {...rest}>{children}</span>
      )),
    },
    useMotionValue: (initial: number) => ({
      get: () => initial,
      set: () => {},
      on: () => () => {},
    }),
    useTransform: () => ({
      get: () => '0',
      on: () => () => {},
    }),
    animate: () => ({ stop: () => {} }),
  };
});

// Mock react-router-dom
vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useLocation: () => ({ pathname: '/' }),
  Link: ({ children, to, ...props }: any) => <a href={to} {...props}>{children}</a>,
  BrowserRouter: ({ children }: any) => <>{children}</>,
}));
