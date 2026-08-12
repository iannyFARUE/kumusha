import Link from 'next/link';
import { ROUTES } from '@/lib/constants';
import styles from './not-found.module.css';

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
