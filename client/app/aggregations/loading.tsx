import { Skeleton } from '../components';
import styles from './aggregations.module.css';

/** Matches the three report sections the page renders once its pipelines return */
const SECTION_ROW_COUNTS = [8, 20, 25];

/**
 * Shown by Next.js while the insights page loads.
 *
 * <p>This page is the slowest in the application: it is rendered per request and awaits three
 * aggregation pipelines that each scan the whole collection, so without a fallback a navigation
 * to it appears to hang on the previous page.
 */
export default function Loading() {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>Insights</h1>
      <p className={styles.subtitle}>Running aggregation pipelines over the collection...</p>

      {SECTION_ROW_COUNTS.map((rowCount, sectionIndex) => (
        <section key={sectionIndex} className={styles.section}>
          <Skeleton variant="text" width="40%" height="1.5rem" />
          <Skeleton variant="text" width="70%" />

          <div className={styles.tableContainer}>
            {[...Array(rowCount)].map((_, rowIndex) => (
              <Skeleton key={rowIndex} variant="text" height="2.25rem" />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
