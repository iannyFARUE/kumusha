'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchFacets, type ListingFilterParams } from '@/lib/api';
import { ListingFacets } from '@/types/listing';
import styles from './FilterBar.module.css';

const SORT_OPTIONS = [
  { value: 'name', label: 'Name' },
  { value: 'price', label: 'Price' },
  { value: 'reviewScore', label: 'Review score' },
  { value: 'numberOfReviews', label: 'Number of reviews' },
  { value: 'accommodates', label: 'Guests' },
];

interface FilterBarProps {
  onFilterChange: (filters: ListingFilterParams) => void;
  isLoading?: boolean;
  initialFilters?: ListingFilterParams;
}

/**
 * Compares two filter objects so the component only resets its state when the URL genuinely
 * changed, rather than on every render that produces a new object identity.
 */
function areFiltersEqual(a: ListingFilterParams, b: ListingFilterParams): boolean {
  return (
    a.propertyType === b.propertyType &&
    a.roomType === b.roomType &&
    a.market === b.market &&
    a.amenity === b.amenity &&
    a.minPrice === b.minPrice &&
    a.maxPrice === b.maxPrice &&
    a.minBedrooms === b.minBedrooms &&
    a.minAccommodates === b.minAccommodates &&
    a.minRating === b.minRating &&
    a.superhostOnly === b.superhostOnly &&
    a.sortBy === b.sortBy &&
    a.sortOrder === b.sortOrder
  );
}

export default function FilterBar({
  onFilterChange,
  isLoading = false,
  initialFilters = {},
}: FilterBarProps) {
  const [filters, setFilters] = useState<ListingFilterParams>(initialFilters);
  const [facets, setFacets] = useState<ListingFacets>({
    propertyTypes: [],
    roomTypes: [],
    markets: [],
  });
  const [isLoadingFacets, setIsLoadingFacets] = useState(true);

  const previousInitialFilters = useRef<ListingFilterParams>(initialFilters);

  // One request supplies every dropdown plus the price bounds
  useEffect(() => {
    async function loadFacets() {
      setIsLoadingFacets(true);
      setFacets(await fetchFacets());
      setIsLoadingFacets(false);
    }
    loadFacets();
  }, []);

  // Keep internal state in step with URL-driven navigation
  useEffect(() => {
    if (!areFiltersEqual(previousInitialFilters.current, initialFilters)) {
      setFilters(initialFilters);
      previousInitialFilters.current = initialFilters;
    }
  }, [initialFilters]);

  const handleFilterChange = useCallback(
    (key: keyof ListingFilterParams, value: string | number | boolean | undefined) => {
      setFilters((previous) => {
        const next = { ...previous };
        if (value === '' || value === undefined || value === false) {
          delete next[key];
        } else {
          (next as Record<string, unknown>)[key] = value;
        }
        return next;
      });
    },
    []
  );

  const handleApplyFilters = useCallback(() => {
    onFilterChange(filters);
  }, [filters, onFilterChange]);

  const handleClearFilters = useCallback(() => {
    setFilters({});
    onFilterChange({});
  }, [onFilterChange]);

  const hasActiveFilters = Object.keys(filters).length > 0;

  const activeFilterChips: { key: string; label: string }[] = [];
  if (filters.propertyType) activeFilterChips.push({ key: 'propertyType', label: `Type: ${filters.propertyType}` });
  if (filters.roomType) activeFilterChips.push({ key: 'roomType', label: `Room: ${filters.roomType}` });
  if (filters.market) activeFilterChips.push({ key: 'market', label: `Market: ${filters.market}` });
  if (filters.amenity) activeFilterChips.push({ key: 'amenity', label: `Amenity: ${filters.amenity}` });
  if (filters.minPrice !== undefined) activeFilterChips.push({ key: 'minPrice', label: `Min $${filters.minPrice}` });
  if (filters.maxPrice !== undefined) activeFilterChips.push({ key: 'maxPrice', label: `Max $${filters.maxPrice}` });
  if (filters.minBedrooms !== undefined) activeFilterChips.push({ key: 'minBedrooms', label: `${filters.minBedrooms}+ bedrooms` });
  if (filters.minAccommodates !== undefined) activeFilterChips.push({ key: 'minAccommodates', label: `Sleeps ${filters.minAccommodates}+` });
  if (filters.minRating !== undefined) activeFilterChips.push({ key: 'minRating', label: `Rating ${filters.minRating}+` });
  if (filters.superhostOnly) activeFilterChips.push({ key: 'superhostOnly', label: 'Superhosts only' });
  if (filters.sortBy) {
    const sortLabel = SORT_OPTIONS.find((option) => option.value === filters.sortBy)?.label ?? filters.sortBy;
    activeFilterChips.push({ key: 'sort', label: `Sort: ${sortLabel} (${filters.sortOrder ?? 'asc'})` });
  }

  const removeFilter = (key: string) => {
    if (key === 'sort') {
      handleFilterChange('sortBy', undefined);
      handleFilterChange('sortOrder', undefined);
    } else {
      handleFilterChange(key as keyof ListingFilterParams, undefined);
    }
  };

  const priceHint =
    facets.minPrice !== undefined && facets.maxPrice !== undefined
      ? `Dataset range $${Math.round(facets.minPrice)} - $${Math.round(facets.maxPrice)}`
      : '';

  return (
    <div className={styles.filterBar}>
      <div className={styles.filterHeader}>
        <h3 className={styles.filterTitle}>Filter stays</h3>
        {hasActiveFilters && (
          <button className={styles.clearFiltersButton} onClick={handleClearFilters} type="button">
            Clear all
          </button>
        )}
      </div>

      <div className={styles.filterControls}>
        <div className={styles.filterGroup}>
          <label className={styles.filterLabel} htmlFor="propertyType">
            Property type
          </label>
          <select
            id="propertyType"
            className={styles.filterSelect}
            value={filters.propertyType ?? ''}
            onChange={(event) => handleFilterChange('propertyType', event.target.value)}
            disabled={isLoading || isLoadingFacets}
          >
            <option value="">{isLoadingFacets ? 'Loading...' : 'All types'}</option>
            {facets.propertyTypes.map((propertyType) => (
              <option key={propertyType} value={propertyType}>
                {propertyType}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel} htmlFor="roomType">
            Room type
          </label>
          <select
            id="roomType"
            className={styles.filterSelect}
            value={filters.roomType ?? ''}
            onChange={(event) => handleFilterChange('roomType', event.target.value)}
            disabled={isLoading || isLoadingFacets}
          >
            <option value="">{isLoadingFacets ? 'Loading...' : 'Any room type'}</option>
            {facets.roomTypes.map((roomType) => (
              <option key={roomType} value={roomType}>
                {roomType}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel} htmlFor="market">
            Market
          </label>
          <select
            id="market"
            className={styles.filterSelect}
            value={filters.market ?? ''}
            onChange={(event) => handleFilterChange('market', event.target.value)}
            disabled={isLoading || isLoadingFacets}
          >
            <option value="">{isLoadingFacets ? 'Loading...' : 'Anywhere'}</option>
            {facets.markets.map((market) => (
              <option key={market} value={market}>
                {market}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel} htmlFor="minPrice">
            Nightly price
          </label>
          {priceHint && <span className={styles.hint}>{priceHint}</span>}
          <div className={styles.rangeGroup}>
            <input
              id="minPrice"
              type="number"
              className={`${styles.filterInput} ${styles.rangeInput}`}
              placeholder="Min"
              value={filters.minPrice ?? ''}
              onChange={(event) =>
                handleFilterChange('minPrice', event.target.value ? parseFloat(event.target.value) : undefined)
              }
              disabled={isLoading}
              min={0}
            />
            <span className={styles.rangeDivider}>to</span>
            <input
              type="number"
              className={`${styles.filterInput} ${styles.rangeInput}`}
              placeholder="Max"
              value={filters.maxPrice ?? ''}
              onChange={(event) =>
                handleFilterChange('maxPrice', event.target.value ? parseFloat(event.target.value) : undefined)
              }
              disabled={isLoading}
              min={0}
            />
          </div>
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel} htmlFor="minAccommodates">
            Sleeps at least
          </label>
          <input
            id="minAccommodates"
            type="number"
            className={styles.filterInput}
            placeholder="e.g. 4"
            value={filters.minAccommodates ?? ''}
            onChange={(event) =>
              handleFilterChange('minAccommodates', event.target.value ? parseInt(event.target.value, 10) : undefined)
            }
            disabled={isLoading}
            min={1}
          />
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel} htmlFor="minRating">
            Minimum rating
          </label>
          <span className={styles.hint}>Scores run 0-100 in this dataset</span>
          <input
            id="minRating"
            type="number"
            className={styles.filterInput}
            placeholder="e.g. 90"
            value={filters.minRating ?? ''}
            onChange={(event) =>
              handleFilterChange('minRating', event.target.value ? parseInt(event.target.value, 10) : undefined)
            }
            disabled={isLoading}
            min={0}
            max={100}
          />
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel} htmlFor="sortBy">
            Sort by
          </label>
          <select
            id="sortBy"
            className={styles.filterSelect}
            value={filters.sortBy ?? 'name'}
            onChange={(event) =>
              handleFilterChange('sortBy', event.target.value as ListingFilterParams['sortBy'])
            }
            disabled={isLoading}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel} htmlFor="sortOrder">
            Order
          </label>
          <select
            id="sortOrder"
            className={styles.filterSelect}
            value={filters.sortOrder ?? 'asc'}
            onChange={(event) => handleFilterChange('sortOrder', event.target.value as 'asc' | 'desc')}
            disabled={isLoading || !filters.sortBy}
          >
            <option value="asc">Ascending</option>
            <option value="desc">Descending</option>
          </select>
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.checkboxLabel}>
            <input
              type="checkbox"
              checked={filters.superhostOnly ?? false}
              onChange={(event) => handleFilterChange('superhostOnly', event.target.checked)}
              disabled={isLoading}
            />
            Superhosts only
          </label>
        </div>

        <button className={styles.applyButton} onClick={handleApplyFilters} disabled={isLoading} type="button">
          {isLoading ? 'Loading...' : 'Apply filters'}
        </button>
      </div>

      {activeFilterChips.length > 0 && (
        <div className={styles.activeFilters}>
          {activeFilterChips.map((chip) => (
            <span key={chip.key} className={styles.filterChip}>
              {chip.label}
              <button
                className={styles.chipRemove}
                onClick={() => removeFilter(chip.key)}
                type="button"
                aria-label={`Remove filter: ${chip.label}`}
              >
                &times;
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
