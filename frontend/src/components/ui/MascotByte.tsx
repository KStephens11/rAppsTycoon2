import { motion } from 'framer-motion';

export type MascotMood = 'happy' | 'excited' | 'thinking' | 'waving' | 'celebrating';

interface MascotByteProps {
  mood?: MascotMood;
  /** Width in px. Height is derived from the 100:118 aspect ratio. Defaults to 88. */
  size?: number;
  className?: string;
}

export function MascotByte({ mood = 'happy', size = 88, className = '' }: MascotByteProps) {
  const isExcited = mood === 'excited' || mood === 'celebrating';
  const isThinking = mood === 'thinking';
  const isWaving = mood === 'waving';
  const isCelebrating = mood === 'celebrating';
  const height = Math.round(size * 1.182); // preserve 100:118 viewBox ratio

  return (
    <motion.div
      className={`flex flex-col items-center select-none shrink-0 ${className}`}
      animate={{ y: [0, -5, 0] }}
      transition={{ duration: 2.5, repeat: Infinity, ease: 'easeInOut' }}
    >
      <svg
        viewBox="0 0 100 118"
        width={size}
        height={height}
        aria-hidden="true"
      >
        {/* Antenna stem */}
        <line x1="50" y1="7" x2="50" y2="24" stroke="#06b6d4" strokeWidth="3" strokeLinecap="round" />
        {/* Antenna tip */}
        <circle cx="50" cy="5" r="4" fill="#06b6d4" />
        {/* Pulsing signal rings */}
        <motion.circle
          cx="50" cy="5" r="8"
          fill="none" stroke="#06b6d4" strokeWidth="1.5"
          animate={{ r: [8, 14], opacity: [0.7, 0] }}
          transition={{ duration: 1.6, repeat: Infinity, ease: 'easeOut' }}
        />
        <motion.circle
          cx="50" cy="5" r="13"
          fill="none" stroke="#06b6d4" strokeWidth="1"
          animate={{ r: [13, 20], opacity: [0.3, 0] }}
          transition={{ duration: 1.6, repeat: Infinity, ease: 'easeOut', delay: 0.35 }}
        />

        {/* Head */}
        <rect x="15" y="22" width="70" height="56" rx="17" fill="#1e293b" stroke="#06b6d4" strokeWidth="2" />

        {/* Eyes */}
        {isThinking ? (
          <>
            {/* Left eye squinted */}
            <path d="M 27 44 Q 36 39 45 44" stroke="#06b6d4" strokeWidth="2.5" fill="none" strokeLinecap="round" />
            {/* Right eye open */}
            <circle cx="65" cy="42" r="7.5" fill="#0f172a" />
            <circle cx="65" cy="42" r="5"   fill="#06b6d4" />
            <circle cx="67" cy="40" r="1.5" fill="white" />
          </>
        ) : isExcited ? (
          <>
            {/* Wide sparkling eyes */}
            <circle cx="36" cy="42" r="9.5"  fill="#0f172a" />
            <circle cx="36" cy="42" r="6.5"  fill="#06b6d4" />
            <circle cx="38.5" cy="39.5" r="2" fill="white" />
            <circle cx="64" cy="42" r="9.5"  fill="#0f172a" />
            <circle cx="64" cy="42" r="6.5"  fill="#06b6d4" />
            <circle cx="66.5" cy="39.5" r="2" fill="white" />
          </>
        ) : (
          <>
            {/* Normal eyes */}
            <circle cx="36" cy="42" r="7.5" fill="#0f172a" />
            <circle cx="36" cy="42" r="5"   fill="#06b6d4" />
            <circle cx="38" cy="40" r="1.5" fill="white" />
            <circle cx="64" cy="42" r="7.5" fill="#0f172a" />
            <circle cx="64" cy="42" r="5"   fill="#06b6d4" />
            <circle cx="66" cy="40" r="1.5" fill="white" />
          </>
        )}

        {/* Mouth */}
        {isThinking ? (
          <path d="M 36 60 Q 50 58 64 60" stroke="#64748b" strokeWidth="2"   fill="none" strokeLinecap="round" />
        ) : isExcited ? (
          <path d="M 31 58 Q 50 74 69 58"  stroke="#10b981" strokeWidth="3"   fill="none" strokeLinecap="round" />
        ) : (
          <path d="M 34 59 Q 50 71 66 59"  stroke="#10b981" strokeWidth="2.5" fill="none" strokeLinecap="round" />
        )}

        {/* Cheeks */}
        <ellipse cx="22" cy="51" rx="5" ry="3.5" fill="#f472b6" opacity="0.22" />
        <ellipse cx="78" cy="51" rx="5" ry="3.5" fill="#f472b6" opacity="0.22" />

        {/* Body */}
        <rect x="18" y="80" width="64" height="36" rx="12" fill="#1e293b" stroke="#06b6d4" strokeWidth="1.5" />
        {/* Chest screen */}
        <rect x="26" y="86" width="48" height="22" rx="5" fill="#0f172a" />
        {/* Signal icon on chest */}
        <circle cx="50" cy="102" r="3" fill={isCelebrating ? '#f59e0b' : '#10b981'} />
        <path d="M 44 98 Q 50 93 56 98"  stroke={isCelebrating ? '#f59e0b' : '#10b981'} strokeWidth="2"   fill="none" strokeLinecap="round" />
        <path d="M 38 94 Q 50 87 62 94"  stroke="#06b6d4" strokeWidth="1.5" fill="none" strokeLinecap="round" opacity="0.6" />

        {/* Left arm — always resting */}
        <rect x="5" y="82" width="13" height="9" rx="4.5" fill="#1e293b" stroke="#06b6d4" strokeWidth="1.5" />

        {/* Right arm — raised & rotated when waving */}
        {isWaving ? (
          <rect
            x="82" y="75" width="13" height="9" rx="4.5"
            fill="#1e293b" stroke="#06b6d4" strokeWidth="1.5"
            transform="rotate(-40 88 84)"
          />
        ) : (
          <rect x="82" y="82" width="13" height="9" rx="4.5" fill="#1e293b" stroke="#06b6d4" strokeWidth="1.5" />
        )}
      </svg>

      <span
        className="font-bold text-primary tracking-widest mt-0.5"
        style={{ fontSize: Math.round(size * 0.114) }}
      >
        BYTE
      </span>
    </motion.div>
  );
}
