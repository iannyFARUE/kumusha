'use client';

import styles from './LoadingSpinner.module.css';

/**
 * Loading spinner component
 */

interface LoadingSpinnerProps {
  size?: 'small' | 'medium' | 'large';
  message?: string;
}

export default function LoadingSpinner({
  size = 'medium',
  message = 'Loading...',
}: LoadingSpinnerProps) {
  const sizeClass = {
    small: styles.small,
    medium: styles.medium,
    large: styles.large,
  }[size];

  return (
    <div className={styles.container}>
      <div className={`${styles.spinner} ${sizeClass}`} role="status" aria-label="Loading" />
      {message && <p className={styles.message}>{message}</p>}
    </div>
  );
}
