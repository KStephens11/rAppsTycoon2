import '@testing-library/jest-dom/vitest';
import React from 'react';

// Polyfill ResizeObserver for jsdom
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as any;
}

// Stub HTMLCanvasElement.getContext so GameMap's 2D canvas doesn't crash in jsdom
HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement) {
  const noop = () => {};
  const self = this;
  return new Proxy({}, {
    get(_target, prop) {
      // Properties that return values
      if (prop === 'canvas') return self;
      if (prop === 'measureText') return () => ({ width: 0, actualBoundingBoxAscent: 0, actualBoundingBoxDescent: 0 });
      if (prop === 'createLinearGradient' || prop === 'createRadialGradient')
        return () => new Proxy({}, { get: () => noop });
      if (prop === 'getImageData') return () => ({ data: new Uint8ClampedArray(0), width: 0, height: 0 });
      if (prop === 'createPattern') return () => ({});
      // String/number properties
      if (typeof prop === 'string' && ['fillStyle', 'strokeStyle', 'font', 'textAlign', 'textBaseline',
        'lineCap', 'lineJoin', 'globalCompositeOperation', 'direction', 'filter'].includes(prop))
        return '';
      if (typeof prop === 'string' && ['lineWidth', 'globalAlpha', 'shadowBlur', 'shadowOffsetX',
        'shadowOffsetY', 'lineDashOffset', 'miterLimit'].includes(prop))
        return 0;
      // Everything else is a no-op function
      return noop;
    },
    set() { return true; },
  });
} as any;

// Mock framer-motion to avoid animation issues in tests
vi.mock('framer-motion', async () => {
  // Generic SVG element forwarder
  const svgElement = (tag: string) =>
    React.forwardRef(({ children, initial, animate, exit, transition, ...rest }: any, ref: any) =>
      React.createElement(tag, { ref, ...rest }, children),
    );

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
      circle: svgElement('circle'),
      rect: svgElement('rect'),
      path: svgElement('path'),
      svg: svgElement('svg'),
      g: svgElement('g'),
      line: svgElement('line'),
      ellipse: svgElement('ellipse'),
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
