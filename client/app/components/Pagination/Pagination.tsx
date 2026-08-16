'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { APP_CONFIG, ROUTES } from '@/lib/constants';
import styles from './Pagination.module.css';

interface PaginationProps {
  currentPage: number;
  hasNextPage: boolean;
  hasPrevPage: boolean;
  limit: number;

  /** How many listings match in total, across every page. */
  totalCount: number;
}

export default function Pagination({
  currentPage,
  hasNextPage,
  hasPrevPage,
  limit,
  totalCount,
}: PaginationProps) {
  const searchParams = useSearchParams();

  const createPageURL = (page: number) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', page.toString());
    if (limit !== APP_CONFIG.defaultListingLimit) {
      params.set('limit', limit.toString());
    }
    return `${ROUTES.listings}?${params.toString()}`;
  };

  const totalPages = limit > 0 ? Math.ceil(totalCount / limit) : 0;

  // Nothing to navigate to, so render nothing
  if (!hasNextPage && !hasPrevPage) {
    return null;
  }

  return (
    <nav className={styles.pagination} aria-label="Listings pagination">
      <div className={styles.paginationContainer}>
        {hasPrevPage ? (
          <Link
            href={createPageURL(currentPage - 1)}
            className={styles.pageButton}
            aria-label="Go to previous page"
          >
            &larr; Previous
          </Link>
        ) : (
          <span className={`${styles.pageButton} ${styles.disabled}`}>&larr; Previous</span>
        )}

        <div className={styles.pageInfo}>
          Page {currentPage}
          {totalPages > 0 && ` of ${totalPages.toLocaleString()}`}
        </div>

        {hasNextPage ? (
          <Link
            href={createPageURL(currentPage + 1)}
            className={styles.pageButton}
            aria-label="Go to next page"
          >
            Next &rarr;
          </Link>
        ) : (
          <span className={`${styles.pageButton} ${styles.disabled}`}>Next &rarr;</span>
        )}
      </div>

      <div className={styles.additionalInfo}>
        {totalCount.toLocaleString()} {totalCount === 1 ? 'stay' : 'stays'} match &middot; {limit}{' '}
        per page
      </div>
    </nav>
  );
}
