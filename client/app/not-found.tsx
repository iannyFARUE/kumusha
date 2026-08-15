import type { Metadata } from 'next';
import Link from 'next/link';
import { ROUTES } from '@/lib/constants';
import styles from './not-found.module.css';

export const metadata: Metadata = {
  title: 'Page not found',
};

/**
 * Root not-found boundary.
 *
 * <p>Catches any URL that matches no route. Without this, a mistyped address falls through to
 * the framework's stock page, which carries none of the application's styling or navigation.
 * The listing route keeps its own narrower version for ids that do not resolve.
 */
export default function NotFound() {
  return (
    <div className={styles.container}>
      <h1 className={styles.errorCode}>404</h1>
      <h2 className={styles.title}>Page not found</h2>
      <p className={styles.description}>
        That address does not match anything here. It may have been mistyped, or the page may have
        moved.
      </p>
      <div className={styles.actions}>
        <Link href={ROUTES.home} className={styles.primaryLink}>
          Go home
        </Link>
        <Link href={ROUTES.listings} className={styles.secondaryLink}>
          Browse stays
        </Link>
      </div>
    </div>
  );
}
