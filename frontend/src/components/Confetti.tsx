import { useEffect, useRef } from 'react';

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  color: string;
  size: number;
  rotation: number;
  rotationSpeed: number;
  opacity: number;
}

const COLORS = ['#06b6d4', '#10b981', '#f59e0b', '#ec4899', '#8b5cf6', '#f43f5e', '#14b8a6'];

export function Confetti({
  duration = 5000,
  particleCount = 150,
  contained = false,
}: {
  duration?: number;
  particleCount?: number;
  /** If true, renders as absolute (fill parent) instead of fixed (fill viewport) */
  contained?: boolean;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const animationRef = useRef<number>(0);
  const startTimeRef = useRef<number>(0);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const getSize = () => contained
      ? { w: canvas.parentElement?.clientWidth ?? window.innerWidth,
          h: canvas.parentElement?.clientHeight ?? window.innerHeight }
      : { w: window.innerWidth, h: window.innerHeight };

    const { w, h } = getSize();
    canvas.width = w;
    canvas.height = h;

    const particles: Particle[] = [];

    for (let i = 0; i < particleCount; i++) {
      particles.push({
        x: Math.random() * canvas.width,
        y: -20 - Math.random() * canvas.height * 0.5,
        vx: (Math.random() - 0.5) * 4,
        vy: Math.random() * 3 + 2,
        color: COLORS[Math.floor(Math.random() * COLORS.length)],
        size: Math.random() * 8 + 4,
        rotation: Math.random() * Math.PI * 2,
        rotationSpeed: (Math.random() - 0.5) * 0.2,
        opacity: 1,
      });
    }

    startTimeRef.current = performance.now();

    function animate(now: number) {
      if (!ctx || !canvas) return;

      const elapsed = now - startTimeRef.current;
      const fadeStart = duration * 0.7;

      ctx.clearRect(0, 0, canvas.width, canvas.height);

      for (const p of particles) {
        p.x += p.vx;
        p.y += p.vy;
        p.vy += 0.05; // gravity
        p.vx *= 0.99; // air resistance
        p.rotation += p.rotationSpeed;

        // Fade out near end
        if (elapsed > fadeStart) {
          p.opacity = Math.max(0, 1 - (elapsed - fadeStart) / (duration - fadeStart));
        }

        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.rotate(p.rotation);
        ctx.globalAlpha = p.opacity;
        ctx.fillStyle = p.color;
        ctx.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2);
        ctx.restore();
      }

      if (elapsed < duration) {
        animationRef.current = requestAnimationFrame(animate);
      }
    }

    animationRef.current = requestAnimationFrame(animate);

    const handleResize = () => {
      const { w, h } = getSize();
      canvas.width = w;
      canvas.height = h;
    };
    window.addEventListener('resize', handleResize);

    return () => {
      cancelAnimationFrame(animationRef.current);
      window.removeEventListener('resize', handleResize);
    };
  }, [duration]);

  return (
    <canvas
      ref={canvasRef}
      className={`${contained ? 'absolute' : 'fixed'} inset-0 pointer-events-none z-50`}
      aria-hidden="true"
    />
  );
}
