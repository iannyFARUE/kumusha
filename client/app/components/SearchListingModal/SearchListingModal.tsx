'use client';

import { useState } from 'react';
import styles from '../FormStyles.module.css';

export type SearchType = 'mongodb-search' | 'vector-search' | 'nearby';

export interface SearchParams {
  searchType: SearchType;
  /** MongoDB Search fields */
  summary?: string;
  description?: string;
  neighborhood?: string;
  name?: string;
  host?: string;
  amenities?: string;
  searchOperator?: 'must' | 'should' | 'mustNot' | 'filter';
  /** Vector search query */
  q?: string;
  /** Proximity search */
  longitude?: number;
  latitude?: number;
  maxDistanceMeters?: number;
  limit?: number;
}

interface SearchListingModalProps {
  onSearch: (params: SearchParams) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

/**
 * Search panel offering the three query styles this dataset supports:
 * full-text search, semantic vector search, and geospatial proximity search.
 */
export default function SearchListingModal({
  onSearch,
  onCancel,
  isLoading = false,
}: SearchListingModalProps) {
  const [searchType, setSearchType] = useState<SearchType>('mongodb-search');
  const [error, setError] = useState<string | null>(null);

  // MongoDB Search state
  const [summary, setSummary] = useState('');
  const [description, setDescription] = useState('');
  const [neighborhood, setNeighborhood] = useState('');
  const [name, setName] = useState('');
  const [host, setHost] = useState('');
  const [amenities, setAmenities] = useState('');
  const [searchOperator, setSearchOperator] = useState<'must' | 'should' | 'mustNot' | 'filter'>('must');

  // Vector search state
  const [semanticQuery, setSemanticQuery] = useState('');

  // Proximity search state
  const [longitude, setLongitude] = useState('');
  const [latitude, setLatitude] = useState('');
  const [radius, setRadius] = useState('5000');

  const [limit, setLimit] = useState('20');

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    const parsedLimit = parseInt(limit, 10) || 20;

    if (searchType === 'mongodb-search') {
      const hasField = [summary, description, neighborhood, name, host, amenities].some((value) =>
        value.trim()
      );

      if (!hasField) {
        setError('Fill in at least one field to search.');
        return;
      }

      onSearch({
        searchType,
        summary: summary.trim() || undefined,
        description: description.trim() || undefined,
        neighborhood: neighborhood.trim() || undefined,
        name: name.trim() || undefined,
        host: host.trim() || undefined,
        amenities: amenities.trim() || undefined,
        searchOperator,
        limit: parsedLimit,
      });
      return;
    }

    if (searchType === 'vector-search') {
      if (!semanticQuery.trim()) {
        setError('Describe the kind of stay you are looking for.');
        return;
      }

      onSearch({ searchType, q: semanticQuery.trim(), limit: parsedLimit });
      return;
    }

    const parsedLongitude = parseFloat(longitude);
    const parsedLatitude = parseFloat(latitude);

    if (Number.isNaN(parsedLongitude) || Number.isNaN(parsedLatitude)) {
      setError('Enter both a longitude and a latitude.');
      return;
    }

    if (parsedLongitude < -180 || parsedLongitude > 180) {
      setError('Longitude must be between -180 and 180.');
      return;
    }

    if (parsedLatitude < -90 || parsedLatitude > 90) {
      setError('Latitude must be between -90 and 90.');
      return;
    }

    onSearch({
      searchType,
      longitude: parsedLongitude,
      latitude: parsedLatitude,
      maxDistanceMeters: parseInt(radius, 10) || 5000,
      limit: parsedLimit,
    });
  };

  return (
    <form className={styles.formContainer} onSubmit={handleSubmit}>
      <h2 className={styles.formTitle}>Search stays</h2>
      <p className={styles.formSubtitle}>
        Three ways to query the same collection: keyword search, meaning-based search, and
        distance from a point.
      </p>

      <div className={styles.modeToggle}>
        <button
          type="button"
          className={`${styles.modeButton} ${searchType === 'mongodb-search' ? styles.modeButtonActive : ''}`}
          onClick={() => setSearchType('mongodb-search')}
          disabled={isLoading}
        >
          Full-text search
        </button>
        <button
          type="button"
          className={`${styles.modeButton} ${searchType === 'vector-search' ? styles.modeButtonActive : ''}`}
          onClick={() => setSearchType('vector-search')}
          disabled={isLoading}
        >
          Semantic search
        </button>
        <button
          type="button"
          className={`${styles.modeButton} ${searchType === 'nearby' ? styles.modeButtonActive : ''}`}
          onClick={() => setSearchType('nearby')}
          disabled={isLoading}
        >
          Near a point
        </button>
      </div>

      {error && <div className={styles.formError}>{error}</div>}

      {searchType === 'mongodb-search' && (
        <div className={styles.fieldGrid}>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-summary">
              Summary
            </label>
            <span className={styles.hint}>Matched as an exact phrase</span>
            <input
              id="search-summary"
              className={styles.input}
              value={summary}
              onChange={(event) => setSummary(event.target.value)}
              disabled={isLoading}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-description">
              Description
            </label>
            <span className={styles.hint}>Matched as an exact phrase</span>
            <input
              id="search-description"
              className={styles.input}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              disabled={isLoading}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-neighborhood">
              Neighborhood
            </label>
            <span className={styles.hint}>Matched as an exact phrase</span>
            <input
              id="search-neighborhood"
              className={styles.input}
              value={neighborhood}
              onChange={(event) => setNeighborhood(event.target.value)}
              disabled={isLoading}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-name">
              Listing name
            </label>
            <span className={styles.hint}>Typo tolerant</span>
            <input
              id="search-name"
              className={styles.input}
              value={name}
              onChange={(event) => setName(event.target.value)}
              disabled={isLoading}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-host">
              Host name
            </label>
            <span className={styles.hint}>Typo tolerant</span>
            <input
              id="search-host"
              className={styles.input}
              value={host}
              onChange={(event) => setHost(event.target.value)}
              disabled={isLoading}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-amenities">
              Amenities
            </label>
            <span className={styles.hint}>Typo tolerant</span>
            <input
              id="search-amenities"
              className={styles.input}
              value={amenities}
              onChange={(event) => setAmenities(event.target.value)}
              disabled={isLoading}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-operator">
              Combine fields with
            </label>
            <select
              id="search-operator"
              className={styles.select}
              value={searchOperator}
              onChange={(event) =>
                setSearchOperator(event.target.value as 'must' | 'should' | 'mustNot' | 'filter')
              }
              disabled={isLoading}
            >
              <option value="must">must (all must match)</option>
              <option value="should">should (any may match)</option>
              <option value="mustNot">mustNot (exclude matches)</option>
              <option value="filter">filter (match without scoring)</option>
            </select>
          </div>
        </div>
      )}

      {searchType === 'vector-search' && (
        <div className={styles.fieldGrid}>
          <div className={`${styles.field} ${styles.fieldWide}`}>
            <label className={styles.label} htmlFor="search-semantic">
              Describe the stay
            </label>
            <span className={styles.hint}>
              For example: a quiet cabin near the beach with a fireplace. Requires embeddings to
              have been generated on the server.
            </span>
            <textarea
              id="search-semantic"
              className={styles.textarea}
              value={semanticQuery}
              onChange={(event) => setSemanticQuery(event.target.value)}
              disabled={isLoading}
            />
          </div>
        </div>
      )}

      {searchType === 'nearby' && (
        <div className={styles.fieldGrid}>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-longitude">
              Longitude
            </label>
            <span className={styles.hint}>e.g. -8.61308 for Porto</span>
            <input
              id="search-longitude"
              type="number"
              step="any"
              className={styles.input}
              value={longitude}
              onChange={(event) => setLongitude(event.target.value)}
              disabled={isLoading}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-latitude">
              Latitude
            </label>
            <span className={styles.hint}>e.g. 41.1413 for Porto</span>
            <input
              id="search-latitude"
              type="number"
              step="any"
              className={styles.input}
              value={latitude}
              onChange={(event) => setLatitude(event.target.value)}
              disabled={isLoading}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label} htmlFor="search-radius">
              Radius in metres
            </label>
            <input
              id="search-radius"
              type="number"
              min={1}
              max={200000}
              className={styles.input}
              value={radius}
              onChange={(event) => setRadius(event.target.value)}
              disabled={isLoading}
            />
          </div>
        </div>
      )}

      <div className={styles.fieldGrid}>
        <div className={styles.field}>
          <label className={styles.label} htmlFor="search-limit">
            Results to fetch
          </label>
          <input
            id="search-limit"
            type="number"
            min={1}
            max={100}
            className={styles.input}
            value={limit}
            onChange={(event) => setLimit(event.target.value)}
            disabled={isLoading}
          />
        </div>
      </div>

      <div className={styles.formActions}>
        <button type="button" className={styles.secondaryButton} onClick={onCancel} disabled={isLoading}>
          Cancel
        </button>
        <button type="submit" className={styles.primaryButton} disabled={isLoading}>
          {isLoading ? 'Searching...' : 'Search'}
        </button>
      </div>
    </form>
  );
}
