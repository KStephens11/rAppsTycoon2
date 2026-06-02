import { useCallback, useEffect, useRef, useState } from 'react';

const STORAGE_KEY = 'rapp-tycoon-sound-enabled';

function getStoredSoundEnabled(): boolean {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === null ? true : stored === 'true';
  } catch {
    return true;
  }
}

/**
 * Creates an AudioContext lazily (browsers require user interaction before audio can play).
 */
function getAudioContext(): AudioContext | null {
  try {
    return new AudioContext();
  } catch {
    return null;
  }
}

/**
 * Play a short ascending two-tone "beep-boop" for deploy success.
 */
function playDeploySound(ctx: AudioContext) {
  const now = ctx.currentTime;

  // First tone (lower)
  const osc1 = ctx.createOscillator();
  const gain1 = ctx.createGain();
  osc1.type = 'sine';
  osc1.frequency.setValueAtTime(440, now);
  gain1.gain.setValueAtTime(0.3, now);
  gain1.gain.exponentialRampToValueAtTime(0.01, now + 0.15);
  osc1.connect(gain1).connect(ctx.destination);
  osc1.start(now);
  osc1.stop(now + 0.15);

  // Second tone (higher)
  const osc2 = ctx.createOscillator();
  const gain2 = ctx.createGain();
  osc2.type = 'sine';
  osc2.frequency.setValueAtTime(660, now + 0.12);
  gain2.gain.setValueAtTime(0.3, now + 0.12);
  gain2.gain.exponentialRampToValueAtTime(0.01, now + 0.3);
  osc2.connect(gain2).connect(ctx.destination);
  osc2.start(now + 0.12);
  osc2.stop(now + 0.3);
}

/**
 * Play a two-note warning chime for event alerts.
 */
function playEventAlertSound(ctx: AudioContext) {
  const now = ctx.currentTime;

  // First note (high)
  const osc1 = ctx.createOscillator();
  const gain1 = ctx.createGain();
  osc1.type = 'triangle';
  osc1.frequency.setValueAtTime(880, now);
  gain1.gain.setValueAtTime(0.25, now);
  gain1.gain.exponentialRampToValueAtTime(0.01, now + 0.2);
  osc1.connect(gain1).connect(ctx.destination);
  osc1.start(now);
  osc1.stop(now + 0.2);

  // Second note (slightly lower, creates urgency)
  const osc2 = ctx.createOscillator();
  const gain2 = ctx.createGain();
  osc2.type = 'triangle';
  osc2.frequency.setValueAtTime(740, now + 0.15);
  gain2.gain.setValueAtTime(0.25, now + 0.15);
  gain2.gain.exponentialRampToValueAtTime(0.01, now + 0.4);
  osc2.connect(gain2).connect(ctx.destination);
  osc2.start(now + 0.15);
  osc2.stop(now + 0.4);
}

/**
 * Play a short ascending arpeggio fanfare for game end.
 */
function playGameEndSound(ctx: AudioContext) {
  const now = ctx.currentTime;
  const notes = [523.25, 659.25, 783.99, 1046.5]; // C5, E5, G5, C6

  notes.forEach((freq, i) => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    const startTime = now + i * 0.12;
    osc.frequency.setValueAtTime(freq, startTime);
    gain.gain.setValueAtTime(0.25, startTime);
    gain.gain.exponentialRampToValueAtTime(0.01, startTime + 0.3);
    osc.connect(gain).connect(ctx.destination);
    osc.start(startTime);
    osc.stop(startTime + 0.3);
  });
}

export function useSoundEffects() {
  const [soundEnabled, setSoundEnabled] = useState(getStoredSoundEnabled);
  const ctxRef = useRef<AudioContext | null>(null);

  // Persist preference
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, String(soundEnabled));
    } catch {
      // Ignore storage errors
    }
  }, [soundEnabled]);

  const ensureContext = useCallback((): AudioContext | null => {
    if (!ctxRef.current || ctxRef.current.state === 'closed') {
      ctxRef.current = getAudioContext();
    }
    // Resume if suspended (browser autoplay policy)
    if (ctxRef.current?.state === 'suspended') {
      ctxRef.current.resume();
    }
    return ctxRef.current;
  }, []);

  const toggleSound = useCallback(() => {
    setSoundEnabled((prev) => !prev);
  }, []);

  const playDeploy = useCallback(() => {
    if (!soundEnabled) return;
    const ctx = ensureContext();
    if (ctx) playDeploySound(ctx);
  }, [soundEnabled, ensureContext]);

  const playEventAlert = useCallback(() => {
    if (!soundEnabled) return;
    const ctx = ensureContext();
    if (ctx) playEventAlertSound(ctx);
  }, [soundEnabled, ensureContext]);

  const playGameEnd = useCallback(() => {
    if (!soundEnabled) return;
    const ctx = ensureContext();
    if (ctx) playGameEndSound(ctx);
  }, [soundEnabled, ensureContext]);

  return {
    soundEnabled,
    toggleSound,
    playDeploy,
    playEventAlert,
    playGameEnd,
  };
}
