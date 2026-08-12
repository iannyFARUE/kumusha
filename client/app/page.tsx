import Link from 'next/link';
import styles from './home.module.css';
import { APP_CONFIG, ROUTES } from './lib/constants';

export default function Home() {
  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <h1 className={styles.title}>{APP_CONFIG.name}</h1>
        <p className={styles.tagline}>{APP_CONFIG.tagline}</p>
        <p className={styles.description}>
          A stay explorer built on the MongoDB <code className={styles.code}>sample_airbnb</code>{' '}
          dataset. Browse and edit listings, search them by keyword or by meaning, find what is
          near a point on the map, and see what the collection looks like in aggregate.
        </p>

        <Link href={ROUTES.listings} className={styles.button}>
          Browse stays
        </Link>

        <div className={styles.featureGrid}>
          <div className={styles.feature}>
            <h2 className={styles.featureTitle}>Browse and edit</h2>
            <p className={styles.featureText}>
              Filter by property type, market, price, capacity and rating, then create, update or
              delete stays one at a time or in batches.
            </p>
          </div>
          <div className={styles.feature}>
            <h2 className={styles.featureTitle}>Search three ways</h2>
            <p className={styles.featureText}>
              Keyword search with fuzzy matching, semantic search over listing descriptions, and
              proximity search that returns the distance to each result.
            </p>
          </div>
          <div className={styles.feature}>
            <h2 className={styles.featureTitle}>See the shape of the data</h2>
            <p className={styles.featureText}>
              Aggregation reports on review activity, price and rating by property type, and the
              amenities hosts offer most often.
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}
