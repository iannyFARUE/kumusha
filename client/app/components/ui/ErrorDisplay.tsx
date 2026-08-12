'use client';

import styles from './ErrorDisplay.module.css';

/**
 * Error component for displaying error states
 */

interface ErrorDisplayProps {
  message?: string;
  onRetry?: () => void;
}

export default function ErrorDisplay({
  message = 'Something went wrong',
  onRetry,
}: ErrorDisplayProps) {
  return (
    <div className={styles.errorDisplay} role="alert">
      <h2 className={styles.title}>Error</h2>
      <p className={styles.message}>{message}</p>
      {onRetry && (
        <button onClick={onRetry} type="button" className={styles.retryButton}>
          Try Again
        </button>
      )}
    </div>
  );
}
