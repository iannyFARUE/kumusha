'use client';

/**
 * Action Buttons Component
 *
 * Provides Edit and Delete actions on the listing detail page.
 */

import styles from './ActionButtons.module.css';

interface ActionButtonsProps {
  onEdit: () => void;
  onDelete: () => void;
  isLoading?: boolean;
  disabled?: boolean;
}

export default function ActionButtons({
  onEdit,
  onDelete,
  isLoading = false,
  disabled = false,
}: ActionButtonsProps) {
  return (
    <div className={styles.actionButtons}>
      <button
        onClick={onEdit}
        disabled={disabled || isLoading}
        className={`${styles.button} ${styles.editButton}`}
        type="button"
      >
        {isLoading ? 'Working...' : 'Edit stay'}
      </button>

      <button
        onClick={onDelete}
        disabled={disabled || isLoading}
        className={`${styles.button} ${styles.deleteButton}`}
        type="button"
      >
        {isLoading ? 'Working...' : 'Delete stay'}
      </button>
    </div>
  );
}
