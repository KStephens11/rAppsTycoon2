import { Component, type ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * Catches rendering errors in child components and shows a friendly fallback UI.
 * Does not catch API errors (those are handled by toasts).
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex items-center justify-center min-h-screen bg-surface p-6">
          <div className="text-center max-w-md space-y-4">
            <div className="w-14 h-14 rounded-full bg-warning/10 flex items-center justify-center mx-auto">
              <AlertTriangle size={28} className="text-warning" />
            </div>
            <h2 className="text-lg font-semibold text-text">
              Something went wrong
            </h2>
            <p className="text-sm text-text-muted">
              An unexpected error occurred while rendering the page. You can try again or refresh the browser.
            </p>
            {this.state.error && (
              <p className="text-xs text-text-muted bg-surface-light rounded-lg p-3 font-mono break-all">
                {this.state.error.message}
              </p>
            )}
            <button
              onClick={this.handleReset}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-white text-sm font-medium hover:bg-primary-dark transition-colors"
            >
              Try Again
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
