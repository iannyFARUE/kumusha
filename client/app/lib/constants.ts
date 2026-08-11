/**
 * Application constants
 */

export const APP_CONFIG = {
  name: 'Kumusha',
  tagline: 'Find a place that feels like home',
  description: 'Browse stays from the MongoDB sample_airbnb dataset',
  defaultListingLimit: 20,
  maxListingLimit: 100,
  vectorSearchPageSize: 20,
  /** Review scores in this dataset are stored on a 0-100 scale */
  ratingScale: 100,
} as const;

export const ROUTES = {
  home: '/',
  listings: '/listings',
  listing: (id: string) => `/listing/${encodeURIComponent(id)}`,
  aggregations: '/aggregations',
} as const;

export const API_ENDPOINTS = {
  listings: '/api/listings',
  listing: (id: string) => `/api/listings/${encodeURIComponent(id)}`,
} as const;
