interface SkeletonProps {
  className?: string;
}

/** Reusable animated placeholder — a pulsing grey block */
export function Skeleton({ className = '' }: SkeletonProps) {
  return (
    <div
      className={`rounded-lg bg-surface-lighter animate-pulse ${className}`}
    />
  );
}

/** Skeleton matching the rApp catalogue card layout (4 cards) */
export function CatalogueSkeleton() {
  return (
    <div className="space-y-2">
      <div className="h-4 w-28 rounded bg-surface-lighter animate-pulse mb-3" />
      {[1, 2, 3, 4].map((i) => (
        <div
          key={i}
          className="p-3 rounded-lg bg-surface-light border border-surface-lighter"
        >
          <div className="flex items-start gap-2.5">
            <div className="w-8 h-8 rounded-md bg-surface-lighter animate-pulse shrink-0" />
            <div className="flex-1 space-y-2">
              <div className="h-4 w-32 rounded bg-surface-lighter animate-pulse" />
              <div className="h-3 w-48 rounded bg-surface-lighter animate-pulse" />
              <div className="flex gap-3">
                <div className="h-2 w-16 rounded bg-surface-lighter animate-pulse" />
                <div className="h-2 w-16 rounded bg-surface-lighter animate-pulse" />
              </div>
            </div>
            <div className="h-7 w-16 rounded-md bg-surface-lighter animate-pulse shrink-0" />
          </div>
        </div>
      ))}
    </div>
  );
}

/** Skeleton matching the leaderboard row layout (4 rows) */
export function LeaderboardSkeleton() {
  return (
    <div className="space-y-1">
      {[1, 2, 3, 4].map((i) => (
        <div
          key={i}
          className="flex items-center gap-3 p-2.5 rounded-lg bg-surface-light"
        >
          <div className="w-7 h-7 rounded-full bg-surface-lighter animate-pulse shrink-0" />
          <div className="flex-1 space-y-1.5">
            <div className="flex items-center justify-between">
              <div className="h-4 w-24 rounded bg-surface-lighter animate-pulse" />
              <div className="h-4 w-10 rounded bg-surface-lighter animate-pulse" />
            </div>
            <div className="flex gap-1.5">
              <div className="h-2 w-8 rounded bg-surface-lighter animate-pulse" />
              <div className="h-2 w-8 rounded bg-surface-lighter animate-pulse" />
              <div className="h-2 w-8 rounded bg-surface-lighter animate-pulse" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

/** Skeleton for basestations loading state */
export function BasestationsSkeleton() {
  return (
    <div className="absolute inset-0 flex items-center justify-center bg-surface/80">
      <div className="text-center space-y-3">
        <div className="w-12 h-12 rounded-full bg-surface-lighter animate-pulse mx-auto" />
        <div className="h-3 w-32 rounded bg-surface-lighter animate-pulse mx-auto" />
        <div className="h-2 w-24 rounded bg-surface-lighter animate-pulse mx-auto" />
      </div>
    </div>
  );
}
