'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { ROUTES } from '@/lib/constants';
import styles from './PageSizeSelector.module.css';

interface PageSizeSelectorProps {
  currentLimit: number;
}

const PAGE_SIZE_OPTIONS = [10, 20, 50];

export default function PageSizeSelector({ currentLimit }: PageSizeSelectorProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  const handlePageSizeChange = (newLimit: number) => {
    const params = new URLSearchParams(searchParams);
    params.set('limit', newLimit.toString());
    // A different page size invalidates the current offset
    params.set('page', '1');
    router.push(`${ROUTES.listings}?${params.toString()}`);
  };

  return (
    <div className={styles.pageSizeSelector}>
      <label htmlFor="pageSize" className={styles.label}>
        Stays per page:
      </label>
      <select
        id="pageSize"
        value={currentLimit}
        onChange={(event) => handlePageSizeChange(parseInt(event.target.value, 10))}
        className={styles.select}
      >
        {PAGE_SIZE_OPTIONS.map((size) => (
          <option key={size} value={size}>
            {size}
          </option>
        ))}
      </select>
    </div>
  );
}
