'use client';

import Image from 'next/image';
import Link from 'next/link';
import React from 'react';
import { ROUTES } from '@/lib/constants';
import { formatAmenities, formatCapacity, formatPrice, formatRating, isValidImageUrl } from '@/lib/utils';
import { Listing } from '@/types/listing';
import styles from './ListingCard.module.css';

/**
 * Listing Card Component
 *
 * Renders one stay in the listings grid. It doubles as a selection target when the page is
 * in batch-edit mode, so clicks anywhere except the details link toggle selection.
 */

interface ListingCardProps {
  listing: Listing;
  isSelected?: boolean;
  onSelectionChange?: (listingId: string, isSelected: boolean) => void;
  showCheckbox?: boolean;
  /** Distance from the query point, shown only for proximity search results */
  distanceLabel?: string;
}

export default function ListingCard({
  listing,
  isSelected = false,
  onSelectionChange,
  showCheckbox = false,
  distanceLabel,
}: ListingCardProps) {
  const pictureUrl = listing.images?.pictureUrl;
  const capacity = formatCapacity(listing.accommodates, listing.bedrooms);

  const handleCardClick = (event: React.MouseEvent<HTMLDivElement>) => {
    // Leave the details link alone; everything else toggles selection
    const target = event.target as HTMLElement;
    if (target.closest('a')) {
      return;
    }

    if (showCheckbox && onSelectionChange) {
      onSelectionChange(listing._id, !isSelected);
    }
  };

  return (
    <div
      className={`${styles.listingCard} ${isSelected ? styles.selected : ''} ${showCheckbox ? styles.selectable : ''}`}
      onClick={handleCardClick}
    >
      <div className={styles.photo}>
        {isValidImageUrl(pictureUrl) ? (
          <Image
            src={pictureUrl!}
            alt={`${listing.name} photo`}
            fill
            sizes="(max-width: 480px) 100vw, (max-width: 768px) 50vw, 300px"
            className={styles.photoImage}
          />
        ) : (
          <div className={styles.photoPlaceholder}>No photo available</div>
        )}

        {listing.host?.hostIsSuperhost && <span className={styles.superhostBadge}>Superhost</span>}
      </div>

      <div className={styles.info}>
        <h3 className={styles.name}>{listing.name}</h3>

        {listing.address?.market && <p className={styles.market}>{listing.address.market}</p>}

        {distanceLabel && <p className={styles.distance}>{distanceLabel}</p>}

        {listing.score !== undefined && (
          <p className={styles.score}>Similarity {listing.score.toFixed(4)}</p>
        )}

        <p className={styles.price}>{formatPrice(listing.price)}</p>

        {listing.propertyType && (
          <p className={styles.meta}>
            {listing.propertyType}
            {capacity ? ` · ${capacity}` : ''}
          </p>
        )}

        {listing.reviewScores?.rating !== undefined && (
          <p className={styles.rating}>
            {formatRating(listing.reviewScores.rating)}
            {listing.numberOfReviews ? ` · ${listing.numberOfReviews} reviews` : ''}
          </p>
        )}

        {listing.amenities && listing.amenities.length > 0 && (
          <p className={styles.amenities}>{formatAmenities(listing.amenities)}</p>
        )}
      </div>

      <Link href={ROUTES.listing(listing._id)} className={styles.detailsButton}>
        View stay
      </Link>
    </div>
  );
}
