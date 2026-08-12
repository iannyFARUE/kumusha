'use client';

import { useState } from 'react';
import styles from '../FormStyles.module.css';

interface BatchEditListingFormProps {
  selectedCount: number;
  onSave: (updateData: Record<string, unknown>) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

interface BatchDraft {
  propertyType: string;
  roomType: string;
  cancellationPolicy: string;
  price: string;
  cleaningFee: string;
  minimumNights: string;
  market: string;
  country: string;
}

const EMPTY_DRAFT: BatchDraft = {
  propertyType: '',
  roomType: '',
  cancellationPolicy: '',
  price: '',
  cleaningFee: '',
  minimumNights: '',
  market: '',
  country: '',
};

/**
 * Applies the same set of fields to every selected listing with one updateMany call.
 *
 * Blank inputs are omitted rather than written, so a batch edit never clears a field by
 * accident: leaving a box empty means "leave this alone".
 */
export default function BatchEditListingForm({
  selectedCount,
  onSave,
  onCancel,
  isLoading = false,
}: BatchEditListingFormProps) {
  const [draft, setDraft] = useState<BatchDraft>({ ...EMPTY_DRAFT });
  const [error, setError] = useState<string | null>(null);

  const update = (field: keyof BatchDraft, value: string) =>
    setDraft((previous) => ({ ...previous, [field]: value }));

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    const updateData: Record<string, unknown> = {};

    if (draft.propertyType.trim()) updateData.propertyType = draft.propertyType.trim();
    if (draft.roomType.trim()) updateData.roomType = draft.roomType.trim();
    if (draft.cancellationPolicy.trim()) updateData.cancellationPolicy = draft.cancellationPolicy.trim();
    if (draft.minimumNights.trim()) updateData.minimumNights = draft.minimumNights.trim();
    if (draft.market.trim()) updateData.market = draft.market.trim();
    if (draft.country.trim()) updateData.country = draft.country.trim();
    if (draft.price.trim()) updateData.price = parseFloat(draft.price);
    if (draft.cleaningFee.trim()) updateData.cleaningFee = parseFloat(draft.cleaningFee);

    if (Object.keys(updateData).length === 0) {
      setError('Fill in at least one field to apply to the selected stays.');
      return;
    }

    onSave(updateData);
  };

  return (
    <form className={styles.formContainer} onSubmit={handleSubmit}>
      <h2 className={styles.formTitle}>
        Edit {selectedCount} selected stay{selectedCount === 1 ? '' : 's'}
      </h2>
      <p className={styles.formSubtitle}>
        Every field you fill in is applied to all selected stays in a single updateMany call.
        Fields left blank are untouched.
      </p>

      {error && <div className={styles.formError}>{error}</div>}

      <div className={styles.fieldGrid}>
        <div className={styles.field}>
          <label className={styles.label} htmlFor="batch-propertyType">
            Property type
          </label>
          <input
            id="batch-propertyType"
            className={styles.input}
            value={draft.propertyType}
            onChange={(event) => update('propertyType', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="batch-roomType">
            Room type
          </label>
          <input
            id="batch-roomType"
            className={styles.input}
            value={draft.roomType}
            onChange={(event) => update('roomType', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="batch-cancellationPolicy">
            Cancellation policy
          </label>
          <input
            id="batch-cancellationPolicy"
            className={styles.input}
            placeholder="flexible"
            value={draft.cancellationPolicy}
            onChange={(event) => update('cancellationPolicy', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="batch-price">
            Nightly price
          </label>
          <input
            id="batch-price"
            type="number"
            min={0}
            step="0.01"
            className={styles.input}
            value={draft.price}
            onChange={(event) => update('price', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="batch-cleaningFee">
            Cleaning fee
          </label>
          <input
            id="batch-cleaningFee"
            type="number"
            min={0}
            step="0.01"
            className={styles.input}
            value={draft.cleaningFee}
            onChange={(event) => update('cleaningFee', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="batch-minimumNights">
            Minimum nights
          </label>
          <input
            id="batch-minimumNights"
            className={styles.input}
            value={draft.minimumNights}
            onChange={(event) => update('minimumNights', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="batch-market">
            Market
          </label>
          <input
            id="batch-market"
            className={styles.input}
            value={draft.market}
            onChange={(event) => update('market', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="batch-country">
            Country
          </label>
          <input
            id="batch-country"
            className={styles.input}
            value={draft.country}
            onChange={(event) => update('country', event.target.value)}
            disabled={isLoading}
          />
        </div>
      </div>

      <div className={styles.formActions}>
        <button type="button" className={styles.secondaryButton} onClick={onCancel} disabled={isLoading}>
          Cancel
        </button>
        <button type="submit" className={styles.primaryButton} disabled={isLoading}>
          {isLoading ? 'Updating...' : `Update ${selectedCount} stay${selectedCount === 1 ? '' : 's'}`}
        </button>
      </div>
    </form>
  );
}
