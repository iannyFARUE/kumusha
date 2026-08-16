import type { Metadata } from 'next';
import Link from 'next/link';
import { ROUTES } from '@/lib/constants';
import styles from './not-found.module.css';

/**
 * Calling notFound() discards whatever generateMetadata returned, so the title for a missing
 * stay has to be declared here rather than on the page.
 */
export const metadata: Metadata = {
  title: 'Stay not found',
};

export default function NotFound() {
  return (
    <div className={styles.container}>
      <h1 className={styles.errorCode}>404</h1>
      <h2 className={styles.title}>Stay not found</h2>
      <p className={styles.description}>
        This listing does not exist, or it has been removed from the collection.
      </p>
      <Link href={ROUTES.listings} className={styles.backLink}>
        &larr; Back to stays
      </Link>
    </div>
  );
}
