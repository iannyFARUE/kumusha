'use client';

import { useState } from 'react';
import styles from '../FormStyles.module.css';

/**
 * The flat shape the create endpoint accepts. Nested subdocuments such as
 * host.host_name and address.location are assembled server-side.
 */
export interface NewListingDraft {
  name: string;
  propertyType: string;
  roomType: string;
  summary: string;
  description: string;
  accommodates: string;
  bedrooms: string;
  price: string;
  amenities: string;
  pictureUrl: string;
  hostName: string;
  market: string;
  country: string;
  longitude: string;
  latitude: string;
}

interface AddListingFormProps {
  onSave: (listings: Record<string, unknown>[]) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

const EMPTY_DRAFT: NewListingDraft = {
  name: '',
  propertyType: '',
  roomType: '',
  summary: '',
  description: '',
  accommodates: '',
  bedrooms: '',
  price: '',
  amenities: '',
  pictureUrl: '',
  hostName: '',
  market: '',
  country: '',
  longitude: '',
  latitude: '',
};

/**
 * Converts a draft into the request body, dropping blanks so that optional fields are simply
 * absent rather than sent as empty strings.
 */
function toRequestBody(draft: NewListingDraft): Record<string, unknown> {
  const body: Record<string, unknown> = { name: draft.name.trim() };

  const addText = (key: string, value: string) => {
    if (value.trim()) body[key] = value.trim();
  };

  addText('propertyType', draft.propertyType);
  addText('roomType', draft.roomType);
  addText('summary', draft.summary);
  addText('description', draft.description);
  addText('pictureUrl', draft.pictureUrl);
  addText('hostName', draft.hostName);
  addText('market', draft.market);
  addText('country', draft.country);

  if (draft.accommodates.trim()) body.accommodates = parseInt(draft.accommodates, 10);
  if (draft.bedrooms.trim()) body.bedrooms = parseInt(draft.bedrooms, 10);
  if (draft.price.trim()) body.price = parseFloat(draft.price);

  if (draft.amenities.trim()) {
    body.amenities = draft.amenities
      .split(',')
      .map((amenity) => amenity.trim())
      .filter(Boolean);
  }

  // Coordinates only mean something as a pair
  if (draft.longitude.trim() && draft.latitude.trim()) {
    body.longitude = parseFloat(draft.longitude);
    body.latitude = parseFloat(draft.latitude);
  }

  return body;
}

export default function AddListingForm({ onSave, onCancel, isLoading = false }: AddListingFormProps) {
  const [drafts, setDrafts] = useState<NewListingDraft[]>([{ ...EMPTY_DRAFT }]);
  const [error, setError] = useState<string | null>(null);

  const updateDraft = (index: number, field: keyof NewListingDraft, value: string) => {
    setDrafts((previous) =>
      previous.map((draft, draftIndex) =>
        draftIndex === index ? { ...draft, [field]: value } : draft
      )
    );
  };

  const addDraft = () => setDrafts((previous) => [...previous, { ...EMPTY_DRAFT }]);

  const removeDraft = (index: number) =>
    setDrafts((previous) => previous.filter((_, draftIndex) => draftIndex !== index));

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    const missingName = drafts.findIndex((draft) => !draft.name.trim());
    if (missingName !== -1) {
      setError(`Stay ${missingName + 1} needs a name.`);
      return;
    }

    const invalidCoordinates = drafts.findIndex(
      (draft) => Boolean(draft.longitude.trim()) !== Boolean(draft.latitude.trim())
    );
    if (invalidCoordinates !== -1) {
      setError(`Stay ${invalidCoordinates + 1} needs both a longitude and a latitude, or neither.`);
      return;
    }

    onSave(drafts.map(toRequestBody));
  };

  return (
    <form className={styles.formContainer} onSubmit={handleSubmit}>
      <h2 className={styles.formTitle}>Add a stay</h2>
      <p className={styles.formSubtitle}>
        Only the name is required. Adding more than one stay uses a single insertMany call.
      </p>

      {error && <div className={styles.formError}>{error}</div>}

      {drafts.map((draft, index) => (
        <div key={index} className={styles.entry}>
          <div className={styles.entryHeader}>
            <h3 className={styles.entryTitle}>Stay {index + 1}</h3>
            {drafts.length > 1 && (
              <button
                type="button"
                className={styles.removeEntryButton}
                onClick={() => removeDraft(index)}
                disabled={isLoading}
              >
                Remove
              </button>
            )}
          </div>

          <div className={styles.fieldGrid}>
            <div className={`${styles.field} ${styles.fieldWide}`}>
              <label className={styles.label} htmlFor={`name-${index}`}>
                Name (required)
              </label>
              <input
                id={`name-${index}`}
                className={styles.input}
                value={draft.name}
                onChange={(event) => updateDraft(index, 'name', event.target.value)}
                disabled={isLoading}
                required
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`propertyType-${index}`}>
                Property type
              </label>
              <input
                id={`propertyType-${index}`}
                className={styles.input}
                placeholder="Apartment"
                value={draft.propertyType}
                onChange={(event) => updateDraft(index, 'propertyType', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`roomType-${index}`}>
                Room type
              </label>
              <input
                id={`roomType-${index}`}
                className={styles.input}
                placeholder="Entire home/apt"
                value={draft.roomType}
                onChange={(event) => updateDraft(index, 'roomType', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`price-${index}`}>
                Nightly price
              </label>
              <input
                id={`price-${index}`}
                type="number"
                min={0}
                step="0.01"
                className={styles.input}
                value={draft.price}
                onChange={(event) => updateDraft(index, 'price', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`accommodates-${index}`}>
                Sleeps
              </label>
              <input
                id={`accommodates-${index}`}
                type="number"
                min={1}
                className={styles.input}
                value={draft.accommodates}
                onChange={(event) => updateDraft(index, 'accommodates', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`bedrooms-${index}`}>
                Bedrooms
              </label>
              <input
                id={`bedrooms-${index}`}
                type="number"
                min={0}
                className={styles.input}
                value={draft.bedrooms}
                onChange={(event) => updateDraft(index, 'bedrooms', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`hostName-${index}`}>
                Host name
              </label>
              <input
                id={`hostName-${index}`}
                className={styles.input}
                value={draft.hostName}
                onChange={(event) => updateDraft(index, 'hostName', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`market-${index}`}>
                Market
              </label>
              <input
                id={`market-${index}`}
                className={styles.input}
                placeholder="Harare"
                value={draft.market}
                onChange={(event) => updateDraft(index, 'market', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`country-${index}`}>
                Country
              </label>
              <input
                id={`country-${index}`}
                className={styles.input}
                value={draft.country}
                onChange={(event) => updateDraft(index, 'country', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`longitude-${index}`}>
                Longitude
              </label>
              <span className={styles.hint}>Longitude and latitude build the GeoJSON point</span>
              <input
                id={`longitude-${index}`}
                type="number"
                step="any"
                min={-180}
                max={180}
                className={styles.input}
                value={draft.longitude}
                onChange={(event) => updateDraft(index, 'longitude', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor={`latitude-${index}`}>
                Latitude
              </label>
              <input
                id={`latitude-${index}`}
                type="number"
                step="any"
                min={-90}
                max={90}
                className={styles.input}
                value={draft.latitude}
                onChange={(event) => updateDraft(index, 'latitude', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={`${styles.field} ${styles.fieldWide}`}>
              <label className={styles.label} htmlFor={`pictureUrl-${index}`}>
                Photo URL
              </label>
              <input
                id={`pictureUrl-${index}`}
                className={styles.input}
                placeholder="https://..."
                value={draft.pictureUrl}
                onChange={(event) => updateDraft(index, 'pictureUrl', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={`${styles.field} ${styles.fieldWide}`}>
              <label className={styles.label} htmlFor={`amenities-${index}`}>
                Amenities
              </label>
              <span className={styles.hint}>Comma separated, e.g. Wifi, Kitchen, Pool</span>
              <input
                id={`amenities-${index}`}
                className={styles.input}
                value={draft.amenities}
                onChange={(event) => updateDraft(index, 'amenities', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={`${styles.field} ${styles.fieldWide}`}>
              <label className={styles.label} htmlFor={`summary-${index}`}>
                Summary
              </label>
              <textarea
                id={`summary-${index}`}
                className={styles.textarea}
                value={draft.summary}
                onChange={(event) => updateDraft(index, 'summary', event.target.value)}
                disabled={isLoading}
              />
            </div>

            <div className={`${styles.field} ${styles.fieldWide}`}>
              <label className={styles.label} htmlFor={`description-${index}`}>
                Description
              </label>
              <textarea
                id={`description-${index}`}
                className={styles.textarea}
                value={draft.description}
                onChange={(event) => updateDraft(index, 'description', event.target.value)}
                disabled={isLoading}
              />
            </div>
          </div>
        </div>
      ))}

      <div className={styles.formActions}>
        <button type="button" className={styles.addEntryButton} onClick={addDraft} disabled={isLoading}>
          + Add another stay
        </button>
        <button type="button" className={styles.secondaryButton} onClick={onCancel} disabled={isLoading}>
          Cancel
        </button>
        <button type="submit" className={styles.primaryButton} disabled={isLoading}>
          {isLoading ? 'Saving...' : `Save ${drafts.length > 1 ? `${drafts.length} stays` : 'stay'}`}
        </button>
      </div>
    </form>
  );
}
