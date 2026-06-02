import { useState, useEffect, useRef, useCallback } from 'react';

export interface DeploymentPickerProps {
  templateId: number;
  templateName: string;
  basestations: Array<{ id: number; name: string }>;
  onSelect: (basestationId: number) => void;
  onClose: () => void;
}

const AUTO_CLOSE_DELAY = 5000;

export function DeploymentPicker({
  templateName,
  basestations,
  onSelect,
  onClose,
}: DeploymentPickerProps) {
  const [activeIndex, setActiveIndex] = useState(0);
  const listRef = useRef<HTMLUListElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const resetTimer = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
    timerRef.current = setTimeout(() => {
      onClose();
    }, AUTO_CLOSE_DELAY);
  }, [onClose]);

  // Start auto-close timer on mount and reset on interaction
  useEffect(() => {
    resetTimer();
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, [resetTimer]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      resetTimer();

      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          if (basestations.length > 0) {
            setActiveIndex((prev) =>
              prev < basestations.length - 1 ? prev + 1 : prev
            );
          }
          break;
        case 'ArrowUp':
          e.preventDefault();
          if (basestations.length > 0) {
            setActiveIndex((prev) => (prev > 0 ? prev - 1 : prev));
          }
          break;
        case 'Enter':
          e.preventDefault();
          if (basestations.length > 0) {
            onSelect(basestations[activeIndex].id);
          }
          break;
        case 'Escape':
          e.preventDefault();
          onClose();
          break;
      }
    },
    [basestations, activeIndex, onSelect, onClose, resetTimer]
  );

  // Focus the list on mount for keyboard navigation
  useEffect(() => {
    listRef.current?.focus();
  }, []);

  // Scroll active option into view
  useEffect(() => {
    if (listRef.current && basestations.length > 0) {
      const activeOption = listRef.current.children[activeIndex] as HTMLElement;
      if (activeOption?.scrollIntoView) {
        activeOption.scrollIntoView({ block: 'nearest' });
      }
    }
  }, [activeIndex, basestations.length]);

  return (
    <div className="absolute z-50 mt-1 w-56 rounded-lg bg-surface border border-surface-lighter shadow-lg">
      <div className="px-3 py-2 border-b border-surface-lighter">
        <p className="text-xs text-text-muted">
          Deploy <span className="font-semibold text-text">{templateName}</span> to:
        </p>
      </div>

      {basestations.length === 0 ? (
        <div className="px-3 py-4 text-center">
          <p className="text-sm text-text-muted">No basestations available</p>
        </div>
      ) : (
        <ul
          ref={listRef}
          role="listbox"
          tabIndex={0}
          aria-label={`Deploy ${templateName} to basestation`}
          onKeyDown={handleKeyDown}
          className="max-h-48 overflow-y-auto py-1 focus:outline-none"
        >
          {basestations.map((bs, index) => (
            <li
              key={bs.id}
              role="option"
              aria-selected={index === activeIndex}
              onClick={() => {
                resetTimer();
                onSelect(bs.id);
              }}
              className={`px-3 py-2 text-sm cursor-pointer transition-colors ${
                index === activeIndex
                  ? 'bg-primary/20 text-text'
                  : 'text-text-muted hover:bg-surface-light'
              }`}
            >
              {bs.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
