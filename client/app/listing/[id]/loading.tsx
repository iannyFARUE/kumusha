import { ListingDetailsSkeleton } from '@/components';
import styles from './page.module.css';

export default function ListingDetailsLoading() {
  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <div className={styles.listingDetails}>
          <div className={styles.photoSection}>
            <div className={styles.photoPlaceholder}>Loading...</div>
          </div>

          <div className={styles.listingInfo}>
            <ListingDetailsSkeleton />
          </div>
        </div>
      </main>
    </div>
  );
}
