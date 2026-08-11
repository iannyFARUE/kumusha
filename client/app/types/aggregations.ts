// Type definitions for the aggregations page

export interface ReviewInfo {
  id?: string;
  reviewerName?: string;
  comments?: string;
  date?: string;
}

export interface ListingWithReviews {
  _id: string;
  name?: string;
  propertyType?: string;
  market?: string;
  price?: number;
  pictureUrl?: string;
  reviewScore?: number;
  recentReviews?: ReviewInfo[];
  totalReviews?: number;
  mostRecentReviewDate?: string;
}

export interface PropertyTypeStats {
  propertyType: string;
  listingCount: number;
  averagePrice?: number;
  highestPrice?: number;
  lowestPrice?: number;
  averageRating?: number;
  averageAccommodates?: number;
  totalReviews?: number;
}

export interface AmenityStats {
  amenity: string;
  listingCount: number;
  averagePrice?: number;
  averageRating?: number;
}
