'use client';

import { useState } from 'react';
import { Listing } from '@/types/listing';
import styles from '../FormStyles.module.css';

interface EditListingFormProps {
  listing: Listing;
  onSave: (updateData: Record<string, unknown>) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

interface EditDraft {
  name: string;
  propertyType: string;
  roomType: string;
  bedType: string;
  cancellationPolicy: string;
  accommodates: string;
  bedrooms: string;
  beds: string;
  price: string;
  cleaningFee: string;
  minimumNights: string;
  maximumNights: string;
  amenities: string;
  pictureUrl: string;
  hostName: string;
  market: string;
  country: string;
  suburb: string;
  summary: string;
  description: string;
  neighborhoodOverview: string;
}

function toDraft(listing: Listing): EditDraft {
  return {
    name: listing.name ?? '',
    propertyType: listing.propertyType ?? '',
    roomType: listing.roomType ?? '',
    bedType: listing.bedType ?? '',
    cancellationPolicy: listing.cancellationPolicy ?? '',
    accommodates: listing.accommodates?.toString() ?? '',
    bedrooms: listing.bedrooms?.toString() ?? '',
    beds: listing.beds?.toString() ?? '',
    price: listing.price?.toString() ?? '',
    cleaningFee: listing.cleaningFee?.toString() ?? '',
    minimumNights: listing.minimumNights ?? '',
    maximumNights: listing.maximumNights ?? '',
    amenities: listing.amenities?.join(', ') ?? '',
    pictureUrl: listing.images?.pictureUrl ?? '',
    hostName: listing.host?.hostName ?? '',
    market: listing.address?.market ?? '',
    country: listing.address?.country ?? '',
    suburb: listing.address?.suburb ?? '',
    summary: listing.summary ?? '',
    description: listing.description ?? '',
    neighborhoodOverview: listing.neighborhoodOverview ?? '',
  };
}

/**
 * Builds a PATCH body containing only the fields the user actually changed, so the update
 * stays a genuine partial update rather than a full overwrite.
 */
function toChangedFields(draft: EditDraft, original: EditDraft): Record<string, unknown> {
  const changes: Record<string, unknown> = {};

  const textFields: (keyof EditDraft)[] = [
    'name',
    'propertyType',
    'roomType',
    'bedType',
    'cancellationPolicy',
    'minimumNights',
    'maximumNights',
    'pictureUrl',
    'hostName',
    'market',
    'country',
    'suburb',
    'summary',
    'description',
    'neighborhoodOverview',
  ];

  textFields.forEach((field) => {
    if (draft[field] !== original[field]) {
      changes[field] = draft[field];
    }
  });

  const intFields: (keyof EditDraft)[] = ['accommodates', 'bedrooms', 'beds'];
  intFields.forEach((field) => {
    if (draft[field] !== original[field] && draft[field].trim()) {
      changes[field] = parseInt(draft[field], 10);
    }
  });

  const decimalFields: (keyof EditDraft)[] = ['price', 'cleaningFee'];
  decimalFields.forEach((field) => {
    if (draft[field] !== original[field] && draft[field].trim()) {
      changes[field] = parseFloat(draft[field]);
    }
  });

  if (draft.amenities !== original.amenities) {
    changes.amenities = draft.amenities
      .split(',')
      .map((amenity) => amenity.trim())
      .filter(Boolean);
  }

  return changes;
}

export default function EditListingForm({
  listing,
  onSave,
  onCancel,
  isLoading = false,
}: EditListingFormProps) {
  const [original] = useState<EditDraft>(() => toDraft(listing));
  const [draft, setDraft] = useState<EditDraft>(() => toDraft(listing));
  const [error, setError] = useState<string | null>(null);

  const update = (field: keyof EditDraft, value: string) =>
    setDraft((previous) => ({ ...previous, [field]: value }));

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    if (!draft.name.trim()) {
      setError('Name cannot be empty.');
      return;
    }

    const changes = toChangedFields(draft, original);

    if (Object.keys(changes).length === 0) {
      setError('Nothing has changed yet.');
      return;
    }

    onSave(changes);
  };

  return (
    <form className={styles.formContainer} onSubmit={handleSubmit}>
      <h2 className={styles.formTitle}>Edit stay</h2>
      <p className={styles.formSubtitle}>Only the fields you change are sent to the server.</p>

      {error && <div className={styles.formError}>{error}</div>}

      <div className={styles.fieldGrid}>
        <div className={`${styles.field} ${styles.fieldWide}`}>
          <label className={styles.label} htmlFor="edit-name">
            Name
          </label>
          <input
            id="edit-name"
            className={styles.input}
            value={draft.name}
            onChange={(event) => update('name', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-propertyType">
            Property type
          </label>
          <input
            id="edit-propertyType"
            className={styles.input}
            value={draft.propertyType}
            onChange={(event) => update('propertyType', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-roomType">
            Room type
          </label>
          <input
            id="edit-roomType"
            className={styles.input}
            value={draft.roomType}
            onChange={(event) => update('roomType', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-bedType">
            Bed type
          </label>
          <input
            id="edit-bedType"
            className={styles.input}
            value={draft.bedType}
            onChange={(event) => update('bedType', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-cancellationPolicy">
            Cancellation policy
          </label>
          <input
            id="edit-cancellationPolicy"
            className={styles.input}
            value={draft.cancellationPolicy}
            onChange={(event) => update('cancellationPolicy', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-price">
            Nightly price
          </label>
          <input
            id="edit-price"
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
          <label className={styles.label} htmlFor="edit-cleaningFee">
            Cleaning fee
          </label>
          <input
            id="edit-cleaningFee"
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
          <label className={styles.label} htmlFor="edit-accommodates">
            Sleeps
          </label>
          <input
            id="edit-accommodates"
            type="number"
            min={1}
            className={styles.input}
            value={draft.accommodates}
            onChange={(event) => update('accommodates', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-bedrooms">
            Bedrooms
          </label>
          <input
            id="edit-bedrooms"
            type="number"
            min={0}
            className={styles.input}
            value={draft.bedrooms}
            onChange={(event) => update('bedrooms', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-beds">
            Beds
          </label>
          <input
            id="edit-beds"
            type="number"
            min={0}
            className={styles.input}
            value={draft.beds}
            onChange={(event) => update('beds', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-minimumNights">
            Minimum nights
          </label>
          <span className={styles.hint}>Stored as text in this dataset</span>
          <input
            id="edit-minimumNights"
            className={styles.input}
            value={draft.minimumNights}
            onChange={(event) => update('minimumNights', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-maximumNights">
            Maximum nights
          </label>
          <input
            id="edit-maximumNights"
            className={styles.input}
            value={draft.maximumNights}
            onChange={(event) => update('maximumNights', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-hostName">
            Host name
          </label>
          <input
            id="edit-hostName"
            className={styles.input}
            value={draft.hostName}
            onChange={(event) => update('hostName', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-market">
            Market
          </label>
          <input
            id="edit-market"
            className={styles.input}
            value={draft.market}
            onChange={(event) => update('market', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-country">
            Country
          </label>
          <input
            id="edit-country"
            className={styles.input}
            value={draft.country}
            onChange={(event) => update('country', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="edit-suburb">
            Suburb
          </label>
          <input
            id="edit-suburb"
            className={styles.input}
            value={draft.suburb}
            onChange={(event) => update('suburb', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={`${styles.field} ${styles.fieldWide}`}>
          <label className={styles.label} htmlFor="edit-pictureUrl">
            Photo URL
          </label>
          <input
            id="edit-pictureUrl"
            className={styles.input}
            value={draft.pictureUrl}
            onChange={(event) => update('pictureUrl', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={`${styles.field} ${styles.fieldWide}`}>
          <label className={styles.label} htmlFor="edit-amenities">
            Amenities
          </label>
          <span className={styles.hint}>Comma separated. Saving replaces the whole list.</span>
          <input
            id="edit-amenities"
            className={styles.input}
            value={draft.amenities}
            onChange={(event) => update('amenities', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={`${styles.field} ${styles.fieldWide}`}>
          <label className={styles.label} htmlFor="edit-summary">
            Summary
          </label>
          <textarea
            id="edit-summary"
            className={styles.textarea}
            value={draft.summary}
            onChange={(event) => update('summary', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={`${styles.field} ${styles.fieldWide}`}>
          <label className={styles.label} htmlFor="edit-description">
            Description
          </label>
          <textarea
            id="edit-description"
            className={styles.textarea}
            value={draft.description}
            onChange={(event) => update('description', event.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className={`${styles.field} ${styles.fieldWide}`}>
          <label className={styles.label} htmlFor="edit-neighborhoodOverview">
            Neighborhood overview
          </label>
          <textarea
            id="edit-neighborhoodOverview"
            className={styles.textarea}
            value={draft.neighborhoodOverview}
            onChange={(event) => update('neighborhoodOverview', event.target.value)}
            disabled={isLoading}
          />
        </div>
      </div>

      <div className={styles.formActions}>
        <button type="button" className={styles.secondaryButton} onClick={onCancel} disabled={isLoading}>
          Cancel
        </button>
        <button type="submit" className={styles.primaryButton} disabled={isLoading}>
          {isLoading ? 'Saving...' : 'Save changes'}
        </button>
      </div>
    </form>
  );
}
