'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  AddListingForm,
  BatchEditListingForm,
  FilterBar,
  ListingCard,
  PageSizeSelector,
  Pagination,
  SearchListingModal,
  type SearchParams,
} from '../components';
import { ErrorDisplay, LoadingSpinner } from '../components/ui';
import {
  createListing,
  createListingsBatch,
  deleteListingsBatch,
  fetchListings,
  fetchNearbyListings,
  searchListings,
  updateListingsBatch,
  vectorSearchListings,
  type ListingFilterParams,
} from '../lib/api';
import { APP_CONFIG, ROUTES } from '../lib/constants';
import { formatDistance } from '../lib/utils';
import { Listing } from '@/types/listing';
import pageStyles from './page.module.css';
import listingStyles from './listings.module.css';

/**
 * Listings page
 *
 * Browsing state lives in the URL so filters and pagination survive a refresh or a shared
 * link. Search results deliberately do not: they are transient and are held in component
 * state until the user returns to browsing.
 */
export default function ListingsClient() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const [listings, setListings] = useState<Listing[]>([]);
  const [hasNextPage, setHasNextPage] = useState(false);
  const [hasPrevPage, setHasPrevPage] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const [isCreating, setIsCreating] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);
  const [isSearching, setIsSearching] = useState(false);

  const [showAddForm, setShowAddForm] = useState(false);
  const [showBatchEditForm, setShowBatchEditForm] = useState(false);
  const [showSearchModal, setShowSearchModal] = useState(false);
  const [showDeleteConfirmation, setShowDeleteConfirmation] = useState(false);

  const [selectedListings, setSelectedListings] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [isSearchMode, setIsSearchMode] = useState(false);
  const [searchResults, setSearchResults] = useState<Listing[]>([]);
  const [distanceLabels, setDistanceLabels] = useState<Map<string, string>>(new Map());
  const [currentSearchParams, setCurrentSearchParams] = useState<SearchParams | null>(null);
  const [searchPage, setSearchPage] = useState(1);
  const [searchLimit, setSearchLimit] = useState(20);
  const [searchHasNextPage, setSearchHasNextPage] = useState(false);
  const [searchHasPrevPage, setSearchHasPrevPage] = useState(false);
  const [searchTotalCount, setSearchTotalCount] = useState(0);

  const page = parseInt(searchParams.get('page') || '1', 10);
  const limit = Math.min(
    parseInt(searchParams.get('limit') || APP_CONFIG.defaultListingLimit.toString(), 10),
    APP_CONFIG.maxListingLimit
  );
  const skip = (page - 1) * limit;

  /**
   * Loading is derived rather than stored.
   *
   * The URL (plus a manual refresh counter) identifies which result set the page should be
   * showing; comparing it against the set that actually loaded tells us whether a fetch is in
   * flight. That keeps the effect free of synchronous state updates and cascading renders.
   */
  const [refreshToken, setRefreshToken] = useState(0);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const requestKey = `${searchParams.toString()}|${refreshToken}`;
  const isLoading = loadedKey !== requestKey;

  /** Re-runs the current query, for example after a create, update or delete. */
  const refresh = useCallback(() => setRefreshToken((token) => token + 1), []);

  /**
   * Reads filters out of the URL so a shared link reproduces the same view.
   */
  const parseFiltersFromUrl = useCallback((): ListingFilterParams => {
    const filters: ListingFilterParams = {};

    const propertyType = searchParams.get('propertyType');
    const roomType = searchParams.get('roomType');
    const market = searchParams.get('market');
    const amenity = searchParams.get('amenity');
    const minPrice = searchParams.get('minPrice');
    const maxPrice = searchParams.get('maxPrice');
    const minBedrooms = searchParams.get('minBedrooms');
    const minAccommodates = searchParams.get('minAccommodates');
    const minRating = searchParams.get('minRating');
    const superhostOnly = searchParams.get('superhostOnly');
    const sortBy = searchParams.get('sortBy');
    const sortOrder = searchParams.get('sortOrder');

    if (propertyType) filters.propertyType = propertyType;
    if (roomType) filters.roomType = roomType;
    if (market) filters.market = market;
    if (amenity) filters.amenity = amenity;
    if (minPrice) filters.minPrice = parseFloat(minPrice);
    if (maxPrice) filters.maxPrice = parseFloat(maxPrice);
    if (minBedrooms) filters.minBedrooms = parseInt(minBedrooms, 10);
    if (minAccommodates) filters.minAccommodates = parseInt(minAccommodates, 10);
    if (minRating) filters.minRating = parseInt(minRating, 10);
    if (superhostOnly === 'true') filters.superhostOnly = true;
    if (sortBy && ['name', 'price', 'reviewScore', 'numberOfReviews', 'accommodates'].includes(sortBy)) {
      filters.sortBy = sortBy as ListingFilterParams['sortBy'];
    }
    if (sortOrder && ['asc', 'desc'].includes(sortOrder)) {
      filters.sortOrder = sortOrder as 'asc' | 'desc';
    }

    return filters;
  }, [searchParams]);

  const urlFilters = parseFiltersFromUrl();
  const hasUrlFilters = Object.keys(urlFilters).length > 0;

  const buildUrlWithFilters = (newPage: number, newLimit: number, filters: ListingFilterParams): string => {
    const params = new URLSearchParams();
    params.set('page', newPage.toString());
    params.set('limit', newLimit.toString());

    if (filters.propertyType) params.set('propertyType', filters.propertyType);
    if (filters.roomType) params.set('roomType', filters.roomType);
    if (filters.market) params.set('market', filters.market);
    if (filters.amenity) params.set('amenity', filters.amenity);
    if (filters.minPrice !== undefined) params.set('minPrice', filters.minPrice.toString());
    if (filters.maxPrice !== undefined) params.set('maxPrice', filters.maxPrice.toString());
    if (filters.minBedrooms !== undefined) params.set('minBedrooms', filters.minBedrooms.toString());
    if (filters.minAccommodates !== undefined) params.set('minAccommodates', filters.minAccommodates.toString());
    if (filters.minRating !== undefined) params.set('minRating', filters.minRating.toString());
    if (filters.superhostOnly) params.set('superhostOnly', 'true');
    if (filters.sortBy) params.set('sortBy', filters.sortBy);
    if (filters.sortOrder) params.set('sortOrder', filters.sortOrder);

    return `${ROUTES.listings}?${params.toString()}`;
  };

  useEffect(() => {
    // Guards against a slower earlier request landing after a newer one
    let isCurrent = true;

    const load = async () => {
      const filters = parseFiltersFromUrl();
      const hasFilters = Object.keys(filters).length > 0;

      try {
        const result = await fetchListings(limit, skip, hasFilters ? filters : undefined);

        if (!isCurrent) return;

        setListings(result.listings);
        setHasNextPage(result.hasNextPage);
        setHasPrevPage(result.hasPrevPage);
        setTotalCount(result.totalCount);
        setError(null);
      } catch {
        if (!isCurrent) return;

        setListings([]);
        setHasNextPage(false);
        setHasPrevPage(false);
        setTotalCount(0);
        setError('Failed to load stays. Make sure the server is running on port 3001.');
      }

      if (isCurrent) {
        setLoadedKey(requestKey);
      }
    };

    load();

    return () => {
      isCurrent = false;
    };
  }, [requestKey, limit, skip, parseFiltersFromUrl]);

  const handleFilterChange = (filters: ListingFilterParams) => {
    // Any filter change invalidates the current page number
    router.push(buildUrlWithFilters(1, limit, filters));
  };

  // ==================== CREATE ====================

  const handleAddListing = () => {
    setShowAddForm(true);
    setError(null);
    setSuccessMessage(null);
  };

  const handleCancelAdd = () => {
    setShowAddForm(false);
    setError(null);
    setSuccessMessage(null);
  };

  const handleSaveListing = async (listingsData: Record<string, unknown>[]) => {
    setIsCreating(true);
    setError(null);
    setSuccessMessage(null);

    if (listingsData.length === 1) {
      const result = await createListing(listingsData[0]);

      if (result.success && result.listingId) {
        setSuccessMessage('Stay created. Opening it now...');
        setShowAddForm(false);
        setTimeout(() => router.push(ROUTES.listing(result.listingId!)), 1200);
      } else {
        setError(result.error || 'Failed to create the stay');
      }
    } else {
      const result = await createListingsBatch(listingsData);

      if (result.success) {
        setSuccessMessage(`Created ${result.insertedCount} stays.`);
        setShowAddForm(false);
        setTimeout(refresh, 1200);
      } else {
        setError(result.error || 'Failed to create the stays');
      }
    }

    setIsCreating(false);
  };

  // ==================== SELECTION AND BATCH OPERATIONS ====================

  const handleSelectionChange = (listingId: string, isSelected: boolean) => {
    setSelectedListings((previous) => {
      const next = new Set(previous);
      if (isSelected) {
        next.add(listingId);
      } else {
        next.delete(listingId);
      }
      return next;
    });
  };

  const handleBatchUpdate = () => {
    if (selectedListings.size > 0) {
      setShowBatchEditForm(true);
      setError(null);
      setSuccessMessage(null);
    }
  };

  const handleSaveBatchEdit = async (updateData: Record<string, unknown>) => {
    setIsUpdating(true);
    setError(null);
    setSuccessMessage(null);

    const result = await updateListingsBatch(Array.from(selectedListings), updateData);

    if (result.success) {
      setSuccessMessage(
        `Updated ${result.modifiedCount} of ${result.matchedCount} matching stays.`
      );
      setShowBatchEditForm(false);
      setSelectedListings(new Set());
      setTimeout(refresh, 1200);
    } else {
      setError(result.error || 'Failed to update the selected stays');
    }

    setIsUpdating(false);
  };

  const confirmBatchDelete = async () => {
    setIsDeleting(true);
    setError(null);
    setSuccessMessage(null);
    setShowDeleteConfirmation(false);

    const result = await deleteListingsBatch(Array.from(selectedListings));

    if (result.success) {
      setSuccessMessage(`Deleted ${result.deletedCount} stays.`);
      setSelectedListings(new Set());
      setTimeout(refresh, 1200);
    } else {
      setError(result.error || 'Failed to delete the selected stays');
    }

    setIsDeleting(false);
  };

  // ==================== SEARCH ====================

  const runSearch = async (params: SearchParams, pageNumber: number) => {
    const requestLimit = params.limit || 20;
    const requestSkip = (pageNumber - 1) * requestLimit;

    if (params.searchType === 'vector-search') {
      const result = await vectorSearchListings({ q: params.q!, limit: requestLimit });
      return {
        success: result.success,
        error: result.error,
        listings: result.listings ?? [],
        labels: new Map<string, string>(),
        // Vector search returns a single ranked set rather than pages
        hasNext: false,
        hasPrev: false,
        totalCount: result.listings?.length ?? 0,
      };
    }

    if (params.searchType === 'nearby') {
      const result = await fetchNearbyListings({
        longitude: params.longitude!,
        latitude: params.latitude!,
        maxDistanceMeters: params.maxDistanceMeters,
        limit: requestLimit,
      });

      const labels = new Map<string, string>();
      const mapped: Listing[] = (result.listings ?? []).map((nearby) => {
        labels.set(nearby._id, formatDistance(nearby.distanceMeters));
        return {
          _id: nearby._id,
          name: nearby.name ?? '',
          propertyType: nearby.propertyType,
          roomType: nearby.roomType,
          price: nearby.price,
          accommodates: nearby.accommodates,
          images: nearby.pictureUrl ? { pictureUrl: nearby.pictureUrl } : undefined,
          address: nearby.market ? { market: nearby.market } : undefined,
          reviewScores: nearby.reviewScore !== undefined ? { rating: nearby.reviewScore } : undefined,
        };
      });

      return {
        success: result.success,
        error: result.error,
        listings: mapped,
        labels,
        hasNext: false,
        hasPrev: false,
        totalCount: mapped.length,
      };
    }

    const result = await searchListings({
      summary: params.summary,
      description: params.description,
      neighborhood: params.neighborhood,
      name: params.name,
      host: params.host,
      amenities: params.amenities,
      searchOperator: params.searchOperator,
      limit: requestLimit,
      skip: requestSkip,
    });

    return {
      success: result.success,
      error: result.error,
      listings: result.listings ?? [],
      labels: new Map<string, string>(),
      hasNext: result.hasNextPage ?? false,
      hasPrev: result.hasPrevPage ?? false,
      totalCount: result.totalCount ?? 0,
    };
  };

  const handleSearchSubmit = async (params: SearchParams) => {
    setIsSearching(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const result = await runSearch(params, 1);

      if (!result.success) {
        setError(result.error || 'Search failed');
        setIsSearching(false);
        return;
      }

      setSearchResults(result.listings);
      setDistanceLabels(result.labels);
      setSearchHasNextPage(result.hasNext);
      setSearchHasPrevPage(result.hasPrev);
      setSearchTotalCount(result.totalCount);
      setIsSearchMode(true);
      setSearchPage(1);
      setSearchLimit(params.limit || 20);
      setCurrentSearchParams(params);
      setShowSearchModal(false);
      setSelectedListings(new Set());

      if (result.listings.length === 0) {
        setSuccessMessage('The search ran, but nothing matched. Try different terms.');
      } else {
        const label =
          params.searchType === 'vector-search'
            ? 'semantic search'
            : params.searchType === 'nearby'
              ? 'proximity search'
              : 'full-text search';
        setSuccessMessage(`Found ${result.listings.length} stays using ${label}.`);
      }
    } catch {
      setError('An unexpected error occurred while searching');
    }

    setIsSearching(false);
  };

  const handleSearchPageChange = async (newPage: number) => {
    if (!currentSearchParams || currentSearchParams.searchType !== 'mongodb-search') return;
    if (newPage < 1 || isSearching) return;
    if (newPage > searchPage && !searchHasNextPage) return;

    setIsSearching(true);
    setError(null);

    const result = await runSearch(currentSearchParams, newPage);

    if (result.success) {
      setSearchResults(result.listings);
      setSearchHasNextPage(result.hasNext);
      setSearchHasPrevPage(result.hasPrev);
      setSearchTotalCount(result.totalCount);
      setSearchPage(newPage);
      setSelectedListings(new Set());
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } else {
      setError(result.error || 'Failed to load the next page of results');
    }

    setIsSearching(false);
  };

  const handleClearSearch = () => {
    setIsSearchMode(false);
    setSearchResults([]);
    setDistanceLabels(new Map());
    setSearchHasNextPage(false);
    setSearchHasPrevPage(false);
    setSearchTotalCount(0);
    setSearchPage(1);
    setCurrentSearchParams(null);
    setSelectedListings(new Set());
    setError(null);
    setSuccessMessage(null);
  };

  const displayListings = isSearchMode ? searchResults : listings;
  const formsOpen = showAddForm || showBatchEditForm || showSearchModal;

  if (isLoading && !formsOpen && !isSearchMode) {
    return (
      <div className={pageStyles.page}>
        <main className={pageStyles.main}>
          <LoadingSpinner message="Loading stays..." />
        </main>
      </div>
    );
  }

  return (
    <div className={pageStyles.page}>
      <main className={pageStyles.main}>
        <div className={listingStyles.pageHeader}>
          <h1 className={listingStyles.pageTitle}>
            {isSearchMode ? 'Search results' : hasUrlFilters ? 'Filtered stays' : 'Stays'}
          </h1>

          <div className={listingStyles.headerActions}>
            {!formsOpen && (
              <div className={listingStyles.searchControls}>
                {isSearchMode && (
                  <button onClick={handleClearSearch} className={listingStyles.clearSearchButton} type="button">
                    &larr; Back to all stays
                  </button>
                )}
                <button
                  onClick={() => {
                    setShowSearchModal(true);
                    setError(null);
                    setSuccessMessage(null);
                  }}
                  disabled={isSearching}
                  className={listingStyles.searchButton}
                  type="button"
                >
                  {isSearching ? 'Searching...' : 'Search stays'}
                </button>
              </div>
            )}

            {!isSearchMode && (
              <button
                onClick={handleAddListing}
                disabled={formsOpen || isCreating}
                className={listingStyles.addButton}
                type="button"
              >
                {isCreating ? 'Creating...' : '+ Add stay'}
              </button>
            )}
          </div>
        </div>

        {successMessage && <div className={listingStyles.successMessage}>{successMessage}</div>}
        {error && <div className={listingStyles.errorMessage}>{error}</div>}

        {showAddForm && (
          <AddListingForm onSave={handleSaveListing} onCancel={handleCancelAdd} isLoading={isCreating} />
        )}

        {showSearchModal && (
          <SearchListingModal
            onSearch={handleSearchSubmit}
            onCancel={() => setShowSearchModal(false)}
            isLoading={isSearching}
          />
        )}

        {showBatchEditForm && (
          <BatchEditListingForm
            selectedCount={selectedListings.size}
            onSave={handleSaveBatchEdit}
            onCancel={() => setShowBatchEditForm(false)}
            isLoading={isUpdating}
          />
        )}

        {!formsOpen && !isSearchMode && <PageSizeSelector currentLimit={limit} />}

        {!formsOpen && !isSearchMode && (
          <FilterBar onFilterChange={handleFilterChange} isLoading={isLoading} initialFilters={urlFilters} />
        )}

        {!formsOpen && (
          <>
            {error && displayListings.length === 0 ? (
              <ErrorDisplay
                message={error}
                onRetry={
                  isSearchMode
                    ? () => handleSearchSubmit(currentSearchParams!)
                    : refresh
                }
              />
            ) : displayListings.length === 0 ? (
              <div className={listingStyles.noListings}>
                <p>
                  {isSearchMode
                    ? 'No stays matched your search. Try different terms.'
                    : hasUrlFilters
                      ? 'No stays matched your filters. Try loosening them.'
                      : 'No stays found. Make sure the server is running on port 3001.'}
                </p>
              </div>
            ) : (
              <>
                <div className={listingStyles.listingsGrid}>
                  {displayListings.map((listing) => (
                    <ListingCard
                      key={listing._id}
                      listing={listing}
                      isSelected={selectedListings.has(listing._id)}
                      onSelectionChange={handleSelectionChange}
                      showCheckbox={!formsOpen}
                      distanceLabel={distanceLabels.get(listing._id)}
                    />
                  ))}
                </div>

                {isSearchMode ? (
                  currentSearchParams?.searchType === 'mongodb-search' ? (
                    <nav className={listingStyles.pagination} aria-label="Search results pagination">
                      <div className={listingStyles.paginationContainer}>
                        {searchHasPrevPage && !isSearching ? (
                          <button
                            onClick={() => handleSearchPageChange(searchPage - 1)}
                            className={listingStyles.pageButton}
                            type="button"
                          >
                            &larr; Previous
                          </button>
                        ) : (
                          <span className={`${listingStyles.pageButton} ${listingStyles.disabled}`}>
                            &larr; Previous
                          </span>
                        )}

                        <div className={listingStyles.pageInfo}>Page {searchPage}</div>

                        {searchHasNextPage && !isSearching ? (
                          <button
                            onClick={() => handleSearchPageChange(searchPage + 1)}
                            className={listingStyles.pageButton}
                            type="button"
                          >
                            Next &rarr;
                          </button>
                        ) : (
                          <span className={`${listingStyles.pageButton} ${listingStyles.disabled}`}>
                            Next &rarr;
                          </span>
                        )}
                      </div>

                      <div className={listingStyles.additionalInfo}>
                        {searchLimit} stays per page &middot; {searchTotalCount} on this page
                      </div>
                    </nav>
                  ) : (
                    <div className={listingStyles.searchInfo}>
                      Showing {displayListings.length} results (
                      {currentSearchParams?.searchType === 'nearby' ? 'proximity search' : 'semantic search'})
                    </div>
                  )
                ) : (
                  <Pagination
                    currentPage={page}
                    hasNextPage={hasNextPage}
                    hasPrevPage={hasPrevPage}
                    limit={limit}
                    totalCount={totalCount}
                  />
                )}
              </>
            )}
          </>
        )}

        {showDeleteConfirmation && (
          <div className={listingStyles.confirmationOverlay}>
            <div className={listingStyles.confirmationDialog}>
              <h3 className={listingStyles.confirmationTitle}>Confirm delete</h3>
              <p className={listingStyles.confirmationMessage}>
                Delete {selectedListings.size} selected stay
                {selectedListings.size === 1 ? '' : 's'}? This cannot be undone.
              </p>
              <div className={listingStyles.confirmationActions}>
                <button
                  onClick={() => setShowDeleteConfirmation(false)}
                  className={listingStyles.cancelButton}
                  type="button"
                >
                  Cancel
                </button>
                <button
                  onClick={confirmBatchDelete}
                  className={listingStyles.confirmDeleteButton}
                  type="button"
                  disabled={isDeleting}
                >
                  {isDeleting ? 'Deleting...' : 'Delete'}
                </button>
              </div>
            </div>
          </div>
        )}

        {selectedListings.size > 0 && !formsOpen && (
          <div className={listingStyles.selectionBar}>
            <div className={listingStyles.selectionBarContent}>
              <div className={listingStyles.selectionInfo}>
                <span className={listingStyles.selectionCount}>
                  {selectedListings.size} selected
                </span>
                <button
                  onClick={() => setSelectedListings(new Set())}
                  className={listingStyles.deselectAllButton}
                  type="button"
                >
                  Deselect all
                </button>
              </div>
              <div className={listingStyles.selectionActions}>
                <button
                  onClick={handleBatchUpdate}
                  disabled={isUpdating}
                  className={listingStyles.editSelectedButton}
                  type="button"
                >
                  {isUpdating ? 'Updating...' : 'Edit selected'}
                </button>
                <button
                  onClick={() => setShowDeleteConfirmation(true)}
                  disabled={isDeleting}
                  className={listingStyles.deleteSelectedButton}
                  type="button"
                >
                  {isDeleting ? 'Deleting...' : 'Delete selected'}
                </button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
