import {
  ApiResponse,
  EmbeddingBackfillResult,
  Listing,
  ListingFacets,
  NearbyListing,
} from '@/types/listing';
import { AmenityStats, ListingWithReviews, PropertyTypeStats } from '@/types/aggregations';

/**
 * API configuration and helper functions
 */

/**
 * Resolves the backend base URL.
 *
 * The NEXT_PUBLIC_ prefix is required: this module is imported by client components, and Next.js
 * only inlines prefixed variables into the browser bundle. Without it the value is undefined in
 * the browser and every request silently falls back to localhost.
 *
 * Next.js inlines the value at build time, so a production build with the variable unset would
 * bake the localhost fallback into the shipped bundle and quietly talk to the wrong host. Throwing
 * here turns that into a build failure instead, while development keeps the localhost default so
 * `npm run dev` works with no configuration.
 */
function resolveApiBaseUrl(): string {
  const configured = process.env.NEXT_PUBLIC_API_URL;

  if (configured) {
    return configured;
  }

  if (process.env.NODE_ENV === 'production') {
    throw new Error(
      'NEXT_PUBLIC_API_URL is not set. A production build must point at a real backend: copy ' +
        'client/.env.example to client/.env.local and set NEXT_PUBLIC_API_URL before building.',
    );
  }

  return 'http://localhost:3001';
}

const API_BASE_URL = resolveApiBaseUrl();

/** Aggregation endpoints scan the whole collection, so they get a generous timeout */
const AGGREGATION_TIMEOUT_MS = 15000;

/**
 * Filter parameters for the listings endpoint.
 * These map onto MongoDB find() query operators on the server.
 */
export interface ListingFilterParams {
  propertyType?: string;
  roomType?: string;
  market?: string;
  amenity?: string;
  minPrice?: number;
  maxPrice?: number;
  minBedrooms?: number;
  minAccommodates?: number;
  minRating?: number;
  superhostOnly?: boolean;
  sortBy?: 'name' | 'price' | 'reviewScore' | 'numberOfReviews' | 'accommodates';
  sortOrder?: 'asc' | 'desc';
}

/**
 * Result shape shared by every mutating call.
 */
interface MutationResult {
  success: boolean;
  error?: string;
}

/**
 * Extracts the most specific error message the API offered.
 */
function extractError(result: unknown, fallback: string): string {
  if (result && typeof result === 'object') {
    const body = result as { message?: string; error?: { message?: string } };
    return body.error?.message || body.message || fallback;
  }
  return fallback;
}

/**
 * Runs a fetch with an abort timeout so a hanging backend cannot hang the page.
 */
async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * Appends a query parameter when the value is set.
 */
function appendParam(params: URLSearchParams, key: string, value: string | number | boolean | undefined): void {
  if (value !== undefined && value !== null && value !== '') {
    params.append(key, value.toString());
  }
}

/**
 * Fetches listings with pagination and filtering, using MongoDB find() with query filters.
 *
 * One extra document is requested so the caller can tell whether a next page exists without
 * paying for a separate count query.
 */
export async function fetchListings(
  limit: number = 20,
  skip: number = 0,
  filters?: ListingFilterParams
): Promise<{ listings: Listing[]; hasNextPage: boolean; hasPrevPage: boolean }> {
  try {
    const queryParams = new URLSearchParams();

    const requestLimit = Math.min(limit + 1, 100);
    queryParams.append('limit', requestLimit.toString());
    queryParams.append('skip', skip.toString());

    if (filters) {
      appendParam(queryParams, 'propertyType', filters.propertyType);
      appendParam(queryParams, 'roomType', filters.roomType);
      appendParam(queryParams, 'market', filters.market);
      appendParam(queryParams, 'amenity', filters.amenity);
      appendParam(queryParams, 'minPrice', filters.minPrice);
      appendParam(queryParams, 'maxPrice', filters.maxPrice);
      appendParam(queryParams, 'minBedrooms', filters.minBedrooms);
      appendParam(queryParams, 'minAccommodates', filters.minAccommodates);
      appendParam(queryParams, 'minRating', filters.minRating);
      if (filters.superhostOnly) queryParams.append('superhostOnly', 'true');
      appendParam(queryParams, 'sortBy', filters.sortBy);
      appendParam(queryParams, 'sortOrder', filters.sortOrder);
    }

    const response = await fetch(`${API_BASE_URL}/api/listings?${queryParams}`, {
      next: { revalidate: 300 },
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch listings: ${response.status}`);
    }

    const result: ApiResponse<Listing[]> = await response.json();

    if (!result.success) {
      throw new Error('API returned error response');
    }

    const hasNextPage = result.data.length > limit;
    const listings = hasNextPage ? result.data.slice(0, limit) : result.data;

    return { listings, hasNextPage, hasPrevPage: skip > 0 };
  } catch (error) {
    console.error('Error fetching listings:', error);

    // Surface the failure while developing; degrade gracefully in production
    if (process.env.NODE_ENV === 'development') {
      throw error;
    }

    return { listings: [], hasNextPage: false, hasPrevPage: false };
  }
}

/**
 * Fetches the filter facets (property types, room types, markets and price range).
 */
export async function fetchFacets(): Promise<ListingFacets> {
  const empty: ListingFacets = { propertyTypes: [], roomTypes: [], markets: [] };

  try {
    const response = await fetch(`${API_BASE_URL}/api/listings/facets`, {
      // Facet values change only when the collection does
      next: { revalidate: 3600 },
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch facets: ${response.status}`);
    }

    const result: ApiResponse<ListingFacets> = await response.json();
    return result.success ? result.data : empty;
  } catch (error) {
    console.error('Error fetching facets:', error);
    return empty;
  }
}

/**
 * Fetches all distinct amenities, demonstrating distinct() over an array field.
 */
export async function fetchAmenities(): Promise<string[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/listings/amenities`, {
      next: { revalidate: 3600 },
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch amenities: ${response.status}`);
    }

    const result: ApiResponse<string[]> = await response.json();
    return result.success ? result.data : [];
  } catch (error) {
    console.error('Error fetching amenities:', error);
    return [];
  }
}

/**
 * Fetches a single listing by id.
 */
export async function fetchListingById(id: string): Promise<Listing | null> {
  try {
    if (!id) {
      return null;
    }

    const response = await fetch(`${API_BASE_URL}/api/listings/${encodeURIComponent(id)}`, {
      next: { revalidate: 300 },
    });

    if (!response.ok) {
      console.warn(`Failed to fetch listing ${id}: ${response.status}`);
      return null;
    }

    const result: ApiResponse<Listing> = await response.json();
    return result.success ? result.data : null;
  } catch (error) {
    console.error('Error fetching listing:', error);
    return null;
  }
}

/**
 * Creates a listing.
 */
export async function createListing(
  listingData: Record<string, unknown>
): Promise<MutationResult & { listingId?: string }> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/listings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(listingData),
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Failed to create listing: ${response.status}`) };
    }

    return { success: true, listingId: result.data?._id };
  } catch (error) {
    console.error('Error creating listing:', error);
    return { success: false, error: 'Network error occurred while creating the listing' };
  }
}

/**
 * Creates several listings in one insertMany call.
 */
export async function createListingsBatch(
  listingsData: Record<string, unknown>[]
): Promise<MutationResult & { insertedCount?: number; insertedIds?: string[] }> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/listings/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(listingsData),
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Failed to create listings: ${response.status}`) };
    }

    return {
      success: true,
      insertedCount: result.data?.insertedCount,
      insertedIds: result.data?.insertedIds ?? [],
    };
  } catch (error) {
    console.error('Error creating listings batch:', error);
    return { success: false, error: 'Network error occurred while creating listings' };
  }
}

/**
 * Updates a listing. Keys use the camelCase names of the update request.
 */
export async function updateListing(
  id: string,
  updateData: Record<string, unknown>
): Promise<MutationResult> {
  try {
    if (!id) {
      return { success: false, error: 'Listing id is required' };
    }

    const response = await fetch(`${API_BASE_URL}/api/listings/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(updateData),
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Failed to update listing: ${response.status}`) };
    }

    return { success: true };
  } catch (error) {
    console.error('Error updating listing:', error);
    return { success: false, error: 'Network error occurred while updating the listing' };
  }
}

/**
 * Deletes a listing by id.
 */
export async function deleteListing(id: string): Promise<MutationResult> {
  try {
    if (!id) {
      return { success: false, error: 'Listing id is required' };
    }

    const response = await fetch(`${API_BASE_URL}/api/listings/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Failed to delete listing: ${response.status}`) };
    }

    return { success: true };
  } catch (error) {
    console.error('Error deleting listing:', error);
    return { success: false, error: 'Network error occurred while deleting the listing' };
  }
}

/**
 * Deletes several listings with a single deleteMany call.
 */
export async function deleteListingsBatch(
  listingIds: string[]
): Promise<MutationResult & { deletedCount?: number }> {
  try {
    const filter = { _id: { $in: listingIds } };

    const response = await fetch(`${API_BASE_URL}/api/listings`, {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filter }),
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Failed to delete listings: ${response.status}`) };
    }

    return { success: true, deletedCount: result.data?.deletedCount };
  } catch (error) {
    console.error('Error deleting listings batch:', error);
    return { success: false, error: 'Network error occurred while deleting listings' };
  }
}

/**
 * Updates several listings with a single updateMany call.
 */
export async function updateListingsBatch(
  listingIds: string[],
  updateData: Record<string, unknown>
): Promise<MutationResult & { matchedCount?: number; modifiedCount?: number }> {
  try {
    const filter = { _id: { $in: listingIds } };

    const response = await fetch(`${API_BASE_URL}/api/listings`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filter, update: updateData }),
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Failed to update listings: ${response.status}`) };
    }

    return {
      success: true,
      matchedCount: result.data?.matchedCount,
      modifiedCount: result.data?.modifiedCount,
    };
  } catch (error) {
    console.error('Error updating listings batch:', error);
    return { success: false, error: 'Network error occurred while updating listings' };
  }
}

/**
 * Aggregation API functions
 */

async function fetchAggregation<T>(
  path: string,
  label: string
): Promise<{ success: boolean; error?: string; data?: T }> {
  try {
    const response = await fetchWithTimeout(
      `${API_BASE_URL}${path}`,
      {
        next: { revalidate: 300 },
        headers: { Accept: 'application/json' },
      } as RequestInit,
      AGGREGATION_TIMEOUT_MS
    );

    if (!response.ok) {
      const errorText = await response.text().catch(() => 'Unable to read error response');
      throw new Error(`HTTP ${response.status}: ${errorText || 'Unknown error'}`);
    }

    const result: ApiResponse<T> = await response.json();

    if (!result.success) {
      return { success: false, error: result.message || 'API returned error response' };
    }

    return { success: true, data: result.data };
  } catch (error) {
    console.error(`Error fetching ${label}:`, error);

    if (error instanceof Error && error.name === 'AbortError') {
      return { success: false, error: `Request timed out after ${AGGREGATION_TIMEOUT_MS / 1000} seconds` };
    }

    return {
      success: false,
      error: error instanceof Error ? error.message : `Network error occurred while fetching ${label}`,
    };
  }
}

/**
 * Fetches the most recently reviewed listings along with their latest reviews.
 */
export async function fetchListingsWithReviews(limit: number = 5, listingId?: string) {
  const params = new URLSearchParams();
  params.append('limit', limit.toString());
  if (listingId) params.append('listingId', listingId);

  return fetchAggregation<ListingWithReviews[]>(
    `/api/listings/aggregations/reportingByReviews?${params}`,
    'listings with reviews'
  );
}

/**
 * Fetches price and rating statistics grouped by property type.
 */
export async function fetchPropertyTypeStats(limit: number = 20) {
  return fetchAggregation<PropertyTypeStats[]>(
    `/api/listings/aggregations/reportingByPropertyType?limit=${limit}`,
    'property type statistics'
  );
}

/**
 * Fetches the most common amenities.
 */
export async function fetchAmenityStats(limit: number = 20) {
  return fetchAggregation<AmenityStats[]>(
    `/api/listings/aggregations/reportingByAmenities?limit=${limit}`,
    'amenity statistics'
  );
}

/**
 * Finds listings near a coordinate using the $geoNear pipeline.
 */
export async function fetchNearbyListings(params: {
  longitude: number;
  latitude: number;
  maxDistanceMeters?: number;
  limit?: number;
}): Promise<{ success: boolean; error?: string; listings?: NearbyListing[] }> {
  try {
    const queryParams = new URLSearchParams();
    queryParams.append('longitude', params.longitude.toString());
    queryParams.append('latitude', params.latitude.toString());
    queryParams.append('maxDistanceMeters', (params.maxDistanceMeters ?? 5000).toString());
    queryParams.append('limit', (params.limit ?? 20).toString());

    const response = await fetch(`${API_BASE_URL}/api/listings/nearby?${queryParams}`, {
      headers: { Accept: 'application/json' },
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Proximity search failed: ${response.status}`) };
    }

    return { success: true, listings: result.data ?? [] };
  } catch (error) {
    console.error('Error running proximity search:', error);
    return { success: false, error: 'Network error occurred while searching nearby listings' };
  }
}

/**
 * Searches listings with MongoDB Search across several fields, with server-side pagination.
 */
export async function searchListings(searchParams: {
  summary?: string;
  description?: string;
  neighborhood?: string;
  name?: string;
  host?: string;
  amenities?: string;
  limit?: number;
  skip?: number;
  searchOperator?: 'must' | 'should' | 'mustNot' | 'filter';
}): Promise<{
  success: boolean;
  error?: string;
  listings?: Listing[];
  hasNextPage?: boolean;
  hasPrevPage?: boolean;
  totalCount?: number;
}> {
  try {
    const limit = searchParams.limit || 20;
    const skip = searchParams.skip || 0;

    const queryParams = new URLSearchParams();
    appendParam(queryParams, 'summary', searchParams.summary);
    appendParam(queryParams, 'description', searchParams.description);
    appendParam(queryParams, 'neighborhood', searchParams.neighborhood);
    appendParam(queryParams, 'name', searchParams.name);
    appendParam(queryParams, 'host', searchParams.host);
    appendParam(queryParams, 'amenities', searchParams.amenities);
    queryParams.append('limit', limit.toString());
    queryParams.append('skip', skip.toString());
    if (searchParams.searchOperator) queryParams.append('searchOperator', searchParams.searchOperator);

    const response = await fetch(`${API_BASE_URL}/api/listings/search?${queryParams}`, {
      headers: { Accept: 'application/json' },
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Failed to search listings: ${response.status}`) };
    }

    const listings: Listing[] = result.data?.listings ?? [];

    // The server returns one page at a time, so a full page implies there may be another
    return {
      success: true,
      listings,
      hasNextPage: listings.length === limit,
      hasPrevPage: skip > 0,
      totalCount: result.data?.totalCount ?? listings.length,
    };
  } catch (error) {
    console.error('Error searching listings:', error);
    return { success: false, error: 'Network error occurred while searching listings' };
  }
}

/**
 * Searches listings semantically with MongoDB Vector Search.
 */
export async function vectorSearchListings(searchParams: {
  q: string;
  limit?: number;
}): Promise<{ success: boolean; error?: string; listings?: Listing[] }> {
  try {
    const queryParams = new URLSearchParams();
    queryParams.append('q', searchParams.q);
    queryParams.append('limit', (searchParams.limit || 20).toString());

    const response = await fetch(`${API_BASE_URL}/api/listings/vector-search?${queryParams}`, {
      headers: { Accept: 'application/json' },
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      const errorCode = result?.error?.code;

      if (errorCode === 'VOYAGE_AUTH_ERROR') {
        return {
          success: false,
          error: 'Semantic search unavailable: your Voyage AI API key is missing or invalid. Add a valid VOYAGE_API_KEY to the server .env file and restart it.',
        };
      }

      if (errorCode === 'SERVICE_UNAVAILABLE' || errorCode === 'VOYAGE_API_ERROR') {
        return {
          success: false,
          error: extractError(result, 'Semantic search is currently unavailable. Please try again later.'),
        };
      }

      return { success: false, error: extractError(result, `Semantic search failed: ${response.status}`) };
    }

    // Vector results are a projection, so widen them back into the Listing shape the grid uses
    const listings: Listing[] = (result.data ?? []).map((item: Record<string, unknown>) => ({
      _id: item.id as string,
      name: (item.name as string) ?? '',
      summary: item.summary as string | undefined,
      propertyType: item.propertyType as string | undefined,
      roomType: item.roomType as string | undefined,
      price: item.price as number | undefined,
      amenities: (item.amenities as string[]) ?? [],
      images: item.pictureUrl ? { pictureUrl: item.pictureUrl as string } : undefined,
      address: item.market ? { market: item.market as string } : undefined,
      score: item.score as number | undefined,
    }));

    return { success: true, listings };
  } catch (error) {
    console.error('Error performing semantic search:', error);
    return { success: false, error: 'Network error occurred while performing semantic search' };
  }
}

/**
 * Finds listings similar to a given listing using its stored description embedding.
 */
export async function findSimilarListings(
  listingId: string,
  limit: number = 6
): Promise<{ success: boolean; error?: string; listings?: Listing[] }> {
  try {
    const queryParams = new URLSearchParams();
    queryParams.append('listingId', listingId);
    queryParams.append('limit', limit.toString());

    const response = await fetch(`${API_BASE_URL}/api/listings/find-similar-listings?${queryParams}`, {
      headers: { Accept: 'application/json' },
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Similar listings unavailable: ${response.status}`) };
    }

    return { success: true, listings: result.data ?? [] };
  } catch (error) {
    console.error('Error finding similar listings:', error);
    return { success: false, error: 'Network error occurred while finding similar listings' };
  }
}

/**
 * Generates the description embeddings that vector search depends on.
 */
export async function backfillEmbeddings(
  limit: number = 50
): Promise<{ success: boolean; error?: string; result?: EmbeddingBackfillResult }> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/listings/embeddings/backfill?limit=${limit}`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
    });

    const result = await response.json();

    if (!response.ok || !result.success) {
      return { success: false, error: extractError(result, `Embedding backfill failed: ${response.status}`) };
    }

    return { success: true, result: result.data };
  } catch (error) {
    console.error('Error backfilling embeddings:', error);
    return { success: false, error: 'Network error occurred while generating embeddings' };
  }
}
