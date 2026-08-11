/**
 * Shared type definitions for the Kumusha application.
 *
 * These types match the JSON produced by the backend. Note that the API speaks camelCase
 * even though the underlying documents use snake_case: Jackson serialises the Java property
 * names, and the server translates between the two on every write path.
 */

export interface ListingImages {
  thumbnailUrl?: string;
  mediumUrl?: string;
  pictureUrl?: string;
  xlPictureUrl?: string;
}

export interface ListingHost {
  hostId?: string;
  hostUrl?: string;
  hostName?: string;
  hostLocation?: string;
  hostAbout?: string;
  hostThumbnailUrl?: string;
  hostPictureUrl?: string;
  hostNeighbourhood?: string;
  hostListingsCount?: number;
  hostTotalListingsCount?: number;
  hostVerifications?: string[];
  hostIsSuperhost?: boolean;
  hostHasProfilePic?: boolean;
  hostIdentityVerified?: boolean;
}

/**
 * A GeoJSON point. Coordinates are in [longitude, latitude] order.
 */
export interface ListingLocation {
  type?: string;
  coordinates?: number[];
  isLocationExact?: boolean;
}

export interface ListingAddress {
  street?: string;
  suburb?: string;
  governmentArea?: string;
  market?: string;
  country?: string;
  countryCode?: string;
  location?: ListingLocation;
}

export interface ListingAvailability {
  availability30?: number;
  availability60?: number;
  availability90?: number;
  availability365?: number;
}

/**
 * Review scores. `rating` is on a 0-100 scale; the rest are 0-10.
 */
export interface ListingReviewScores {
  accuracy?: number;
  cleanliness?: number;
  checkin?: number;
  communication?: number;
  location?: number;
  value?: number;
  rating?: number;
}

export interface ListingReview {
  _id?: string;
  date?: string;
  listingId?: string;
  reviewerId?: string;
  reviewerName?: string;
  comments?: string;
}

/**
 * A stay listing. Mirrors the Listing model on the backend.
 */
export interface Listing {
  _id: string;
  name: string;
  listingUrl?: string;
  summary?: string;
  space?: string;
  description?: string;
  neighborhoodOverview?: string;
  notes?: string;
  transit?: string;
  access?: string;
  interaction?: string;
  houseRules?: string;
  propertyType?: string;
  roomType?: string;
  bedType?: string;
  cancellationPolicy?: string;
  accommodates?: number;
  bedrooms?: number;
  beds?: number;
  bathrooms?: number;
  numberOfReviews?: number;
  amenities?: string[];
  price?: number;
  securityDeposit?: number;
  cleaningFee?: number;
  extraPeople?: number;
  guestsIncluded?: number;
  minimumNights?: string;
  maximumNights?: string;
  firstReview?: string;
  lastReview?: string;
  images?: ListingImages;
  host?: ListingHost;
  address?: ListingAddress;
  availability?: ListingAvailability;
  reviewScores?: ListingReviewScores;
  reviews?: ListingReview[];
  /** Similarity score, present only on vector search results */
  score?: number;
}

/**
 * The filter values the listings page offers, in a single response.
 */
export interface ListingFacets {
  propertyTypes: string[];
  roomTypes: string[];
  markets: string[];
  minPrice?: number;
  maxPrice?: number;
}

/**
 * A listing returned by the proximity endpoint, with its distance from the query point.
 */
export interface NearbyListing {
  _id: string;
  name?: string;
  propertyType?: string;
  roomType?: string;
  price?: number;
  pictureUrl?: string;
  reviewScore?: number;
  accommodates?: number;
  market?: string;
  distanceMeters?: number;
}

/**
 * Progress report from the embedding backfill endpoint.
 */
export interface EmbeddingBackfillResult {
  embeddedCount: number;
  skippedCount: number;
  remainingCount: number;
  embeddingField: string;
  dimensions: number;
}

/**
 * The response envelope every endpoint returns.
 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
  pagination?: {
    page: number;
    limit: number;
    total: number;
    pages: number;
  };
}
