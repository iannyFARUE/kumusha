'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { ROUTES } from '@/lib/constants';
import styles from './error.module.css';

export default function ListingDetailsError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('Listing details error:', error);
  }, [error]);

  return (
    <div className={styles.container}>
      <div className={styles.errorCard}>
        <h1 className={styles.title}>Something went wrong</h1>
        <p className={styles.description}>We hit an error while loading this stay.</p>

        <div className={styles.buttonContainer}>
          <button onClick={reset} className={styles.retryButton} type="button">
            Try again
          </button>

          <Link href={ROUTES.listings} className={styles.backLink}>
            Back to stays
          </Link>
        </div>

        {process.env.NODE_ENV === 'development' && error.digest && (
          <p className={styles.errorId}>Error ID: {error.digest}</p>
        )}
      </div>
    </div>
  );
}
