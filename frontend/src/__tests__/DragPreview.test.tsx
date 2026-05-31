import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { DragPreview } from '../components/game/DragPreview';

// Mock the DragContext
const mockUseDrag = vi.fn();
vi.mock('../context/DragContext', () => ({
  useDrag: () => mockUseDrag(),
}));

describe('DragPreview', () => {
  it('renders nothing when dragState is null', () => {
    mockUseDrag.mockReturnValue({
      dragState: null,
      hoveredTargetId: null,
      startDrag: vi.fn(),
      setHoveredTarget: vi.fn(),
      endDrag: vi.fn(),
    });

    const { container } = render(<DragPreview />);
    expect(container.firstChild).toBeNull();
  });

  it('renders rApp name and icon when dragState is active', () => {
    mockUseDrag.mockReturnValue({
      dragState: { templateId: 1, name: 'Energy Saver', icon: 'Energy Saver' },
      hoveredTargetId: null,
      startDrag: vi.fn(),
      setHoveredTarget: vi.fn(),
      endDrag: vi.fn(),
    });

    render(<DragPreview />);
    expect(screen.getByText('Energy Saver')).toBeInTheDocument();
  });

  it('has pointer-events-none and fixed positioning', () => {
    mockUseDrag.mockReturnValue({
      dragState: { templateId: 2, name: 'Fault Predictor', icon: 'Fault Predictor' },
      hoveredTargetId: null,
      startDrag: vi.fn(),
      setHoveredTarget: vi.fn(),
      endDrag: vi.fn(),
    });

    render(<DragPreview />);
    const preview = screen.getByText('Fault Predictor').closest('div[class*="fixed"]');
    expect(preview).not.toBeNull();
    expect(preview?.className).toContain('pointer-events-none');
    expect(preview?.className).toContain('fixed');
  });

  it('updates position on mousemove events', () => {
    mockUseDrag.mockReturnValue({
      dragState: { templateId: 1, name: 'Energy Saver', icon: 'Energy Saver' },
      hoveredTargetId: null,
      startDrag: vi.fn(),
      setHoveredTarget: vi.fn(),
      endDrag: vi.fn(),
    });

    render(<DragPreview />);

    fireEvent.mouseMove(document, { clientX: 200, clientY: 300 });

    const preview = screen.getByText('Energy Saver').closest('div[class*="fixed"]');
    expect(preview).toHaveStyle({ left: '212px', top: '312px' });
  });

  it('updates position on dragover events', () => {
    mockUseDrag.mockReturnValue({
      dragState: { templateId: 1, name: 'Energy Saver', icon: 'Energy Saver' },
      hoveredTargetId: null,
      startDrag: vi.fn(),
      setHoveredTarget: vi.fn(),
      endDrag: vi.fn(),
    });

    render(<DragPreview />);

    // DragEvent in jsdom doesn't support clientX/clientY directly,
    // so we dispatch a MouseEvent on the 'dragover' event type.
    act(() => {
      const dragOverEvent = new MouseEvent('dragover', {
        clientX: 150,
        clientY: 250,
        bubbles: true,
      });
      document.dispatchEvent(dragOverEvent);
    });

    const preview = screen.getByText('Energy Saver').closest('div[class*="fixed"]');
    expect(preview).toHaveStyle({ left: '162px', top: '262px' });
  });
});
