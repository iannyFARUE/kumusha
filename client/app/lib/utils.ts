/**
 * Utility functions for the application
 */

/**
 * Formats a nightly price for display.
 */
export function formatPrice(price?: number): string {
  if (price === undefined || price === null) return 'Price on request';
  return `$${price.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 })} / night`;
}

/**
 * Formats a review score for display.
 *
 * Scores in this dataset run 0-100 rather than 0-5, so they are shown as a percentage.
 */
export function formatRating(rating?: number): string {
  if (rating === undefined || rating === null) return 'No rating yet';
  return `${rating}%`;
}

/**
 * Summarises how many guests a listing sleeps.
 */
export function formatCapacity(accommodates?: number, bedrooms?: number): string {
  const parts: string[] = [];
  if (accommodates) parts.push(`${accommodates} guest${accommodates === 1 ? '' : 's'}`);
  if (bedrooms !== undefined && bedrooms !== null) {
    parts.push(bedrooms === 0 ? 'studio' : `${bedrooms} bedroom${bedrooms === 1 ? '' : 's'}`);
  }
  return parts.join(' · ');
}

/**
 * Formats the first few amenities for display.
 */
export function formatAmenities(amenities?: string[], maxAmenities: number = 3): string {
  if (!amenities || amenities.length === 0) return '';
  return amenities.slice(0, maxAmenities).join(', ');
}

/**
 * Formats a distance in metres as metres or kilometres, whichever reads better.
 */
export function formatDistance(meters?: number): string {
  if (meters === undefined || meters === null) return '';
  if (meters < 1000) return `${Math.round(meters)} m away`;
  return `${(meters / 1000).toFixed(1)} km away`;
}

/**
 * Truncates text to a specified length.
 */
export function truncateText(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text;
  return `${text.substring(0, maxLength)}...`;
}

/**
 * Formats an ISO date string for display, tolerating missing values.
 */
export function formatDate(value?: string): string {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString();
}

/**
 * Checks that an image URL is usable by the Next.js Image component.
 * It must be absolute (http/https) or a root-relative path.
 */
export function isValidImageUrl(url: string | undefined): boolean {
  if (!url || typeof url !== 'string') return false;
  return url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/');
}
