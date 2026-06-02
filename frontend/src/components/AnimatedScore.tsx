import { useEffect, useState, useRef } from 'react';

interface AnimatedScoreProps {
  value: number;
  delay?: number;
  duration?: number;
}

export function AnimatedScore({ value, delay = 0, duration = 1.5 }: AnimatedScoreProps) {
  const [displayValue, setDisplayValue] = useState(0);
  const animationRef = useRef<number>(0);
  const startTimeRef = useRef<number | null>(null);
  const hasStarted = useRef(false);

  useEffect(() => {
    const delayMs = delay * 1000;
    const durationMs = duration * 1000;

    const timeout = setTimeout(() => {
      hasStarted.current = true;
      startTimeRef.current = null;

      function animate(timestamp: number) {
        if (startTimeRef.current === null) {
          startTimeRef.current = timestamp;
        }

        const elapsed = timestamp - startTimeRef.current;
        const progress = Math.min(elapsed / durationMs, 1);

        // Ease-out cubic for a satisfying deceleration
        const eased = 1 - Math.pow(1 - progress, 3);
        const current = Math.round(eased * value);

        setDisplayValue(current);

        if (progress < 1) {
          animationRef.current = requestAnimationFrame(animate);
        }
      }

      animationRef.current = requestAnimationFrame(animate);
    }, delayMs);

    return () => {
      clearTimeout(timeout);
      cancelAnimationFrame(animationRef.current);
    };
  }, [value, delay, duration]);

  return <span>{displayValue.toLocaleString()}</span>;
}
