import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { DeploymentPicker } from '../components/game/DeploymentPicker';

const basestations = [
  { id: 1, name: 'Station Alpha' },
  { id: 2, name: 'Station Beta' },
  { id: 3, name: 'Station Gamma' },
];

describe('DeploymentPicker', () => {
  let onSelect: (basestationId: number) => void;
  let onClose: () => void;

  beforeEach(() => {
    vi.useFakeTimers();
    onSelect = vi.fn<(basestationId: number) => void>();
    onClose = vi.fn<() => void>();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function renderPicker(props?: Partial<React.ComponentProps<typeof DeploymentPicker>>) {
    return render(
      <DeploymentPicker
        templateId={1}
        templateName="Energy Saver"
        basestations={basestations}
        onSelect={onSelect}
        onClose={onClose}
        {...props}
      />
    );
  }

  it('renders the template name in the header', () => {
    renderPicker();
    expect(screen.getByText('Energy Saver')).toBeInTheDocument();
  });

  it('renders all basestations as options', () => {
    renderPicker();
    expect(screen.getByText('Station Alpha')).toBeInTheDocument();
    expect(screen.getByText('Station Beta')).toBeInTheDocument();
    expect(screen.getByText('Station Gamma')).toBeInTheDocument();
  });

  it('uses role="listbox" and role="option" for accessibility', () => {
    renderPicker();
    expect(screen.getByRole('listbox')).toBeInTheDocument();
    expect(screen.getAllByRole('option')).toHaveLength(3);
  });

  it('shows "No basestations available" when list is empty', () => {
    renderPicker({ basestations: [] });
    expect(screen.getByText('No basestations available')).toBeInTheDocument();
  });

  it('navigates down with ArrowDown key', () => {
    renderPicker();
    const listbox = screen.getByRole('listbox');

    fireEvent.keyDown(listbox, { key: 'ArrowDown' });

    const options = screen.getAllByRole('option');
    expect(options[1]).toHaveAttribute('aria-selected', 'true');
  });

  it('navigates up with ArrowUp key', () => {
    renderPicker();
    const listbox = screen.getByRole('listbox');

    // Move down first, then up
    fireEvent.keyDown(listbox, { key: 'ArrowDown' });
    fireEvent.keyDown(listbox, { key: 'ArrowUp' });

    const options = screen.getAllByRole('option');
    expect(options[0]).toHaveAttribute('aria-selected', 'true');
  });

  it('does not go below the last item', () => {
    renderPicker();
    const listbox = screen.getByRole('listbox');

    // Press down more times than there are items
    fireEvent.keyDown(listbox, { key: 'ArrowDown' });
    fireEvent.keyDown(listbox, { key: 'ArrowDown' });
    fireEvent.keyDown(listbox, { key: 'ArrowDown' });
    fireEvent.keyDown(listbox, { key: 'ArrowDown' });

    const options = screen.getAllByRole('option');
    expect(options[2]).toHaveAttribute('aria-selected', 'true');
  });

  it('does not go above the first item', () => {
    renderPicker();
    const listbox = screen.getByRole('listbox');

    fireEvent.keyDown(listbox, { key: 'ArrowUp' });

    const options = screen.getAllByRole('option');
    expect(options[0]).toHaveAttribute('aria-selected', 'true');
  });

  it('confirms selection with Enter key and calls onSelect', () => {
    renderPicker();
    const listbox = screen.getByRole('listbox');

    // Navigate to second item and confirm
    fireEvent.keyDown(listbox, { key: 'ArrowDown' });
    fireEvent.keyDown(listbox, { key: 'Enter' });

    expect(onSelect).toHaveBeenCalledWith(2); // Station Beta's id
  });

  it('dismisses with Escape key and calls onClose', () => {
    renderPicker();
    const listbox = screen.getByRole('listbox');

    fireEvent.keyDown(listbox, { key: 'Escape' });

    expect(onClose).toHaveBeenCalled();
  });

  it('calls onSelect when clicking an option', () => {
    renderPicker();

    fireEvent.click(screen.getByText('Station Gamma'));

    expect(onSelect).toHaveBeenCalledWith(3);
  });

  it('auto-closes after 5 seconds of no interaction', () => {
    renderPicker();

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(onClose).toHaveBeenCalled();
  });

  it('resets auto-close timer on keyboard interaction', () => {
    renderPicker();
    const listbox = screen.getByRole('listbox');

    // Advance 4 seconds
    act(() => {
      vi.advanceTimersByTime(4000);
    });

    // Interact with keyboard (resets timer)
    fireEvent.keyDown(listbox, { key: 'ArrowDown' });

    // Advance another 4 seconds (total 8s from start, but only 4s from last interaction)
    act(() => {
      vi.advanceTimersByTime(4000);
    });

    expect(onClose).not.toHaveBeenCalled();

    // Advance 1 more second (5s from last interaction)
    act(() => {
      vi.advanceTimersByTime(1000);
    });

    expect(onClose).toHaveBeenCalled();
  });

  it('first item is selected by default', () => {
    renderPicker();
    const options = screen.getAllByRole('option');
    expect(options[0]).toHaveAttribute('aria-selected', 'true');
  });
});
