'use client';

import { ErrorDisplay } from './components/ui';

/**
 * Root error boundary.
 *
 * <p>Catches failures on routes that do not define their own boundary, such as the home and
 * insights pages. Without it those errors fall through to the framework's stock error page.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <ErrorDisplay message={error.message || 'Something went wrong'} onRetry={reset} />
  );
}
