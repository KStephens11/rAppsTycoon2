import { Volume2, VolumeX, Sun, Moon } from 'lucide-react';
import { useSoundEffects } from '../../hooks/useSoundEffects';
import { useTheme } from '../../hooks/useTheme';

/**
 * Small floating toolbar with sound and theme toggle buttons.
 * Place in the top-right corner of any page.
 */
export function SettingsToolbar() {
  const { soundEnabled, toggleSound } = useSoundEffects();
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="flex items-center gap-1">
      <button
        onClick={toggleSound}
        className="p-2 rounded-lg text-text-muted hover:text-text hover:bg-surface-light transition-colors"
        aria-label={soundEnabled ? 'Mute sound effects' : 'Enable sound effects'}
        title={soundEnabled ? 'Mute sound effects' : 'Enable sound effects'}
      >
        {soundEnabled ? <Volume2 size={18} /> : <VolumeX size={18} />}
      </button>
      <button
        onClick={toggleTheme}
        className="p-2 rounded-lg text-text-muted hover:text-text hover:bg-surface-light transition-colors"
        aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
        title={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
      >
        {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
      </button>
    </div>
  );
}
