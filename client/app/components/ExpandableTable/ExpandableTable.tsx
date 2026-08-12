'use client';

import React, { useState } from 'react';
import styles from './ExpandableTable.module.css';

interface ExpandableTableProps {
  children: React.ReactNode;
  initialRowCount?: number;
  totalRowCount: number;
}

/**
 * Wraps a table in a collapsible container so long aggregation results do not dominate the
 * page. The button only appears when there is more to reveal.
 */
export default function ExpandableTable({
  children,
  initialRowCount = 10,
  totalRowCount,
}: ExpandableTableProps) {
  const [isExpanded, setIsExpanded] = useState(false);
  const showExpandButton = totalRowCount > initialRowCount;

  const wrapperClass = showExpandButton
    ? `${styles.tableWrapper} ${isExpanded ? styles.expanded : styles.collapsed}`
    : styles.tableWrapper;

  return (
    <div className={styles.container}>
      <div className={wrapperClass}>{children}</div>
      {showExpandButton && (
        <div className={styles.buttonContainer}>
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className={styles.expandButton}
            aria-expanded={isExpanded}
            type="button"
          >
            {isExpanded
              ? 'Show less'
              : `Show all ${totalRowCount} rows (${totalRowCount - initialRowCount} more)`}
          </button>
        </div>
      )}
    </div>
  );
}
