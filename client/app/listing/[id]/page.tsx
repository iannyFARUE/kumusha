'use client';

import { useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ActionButtons, EditListingForm, ListingCard } from '../../components';
import { ErrorDisplay, LoadingSpinner } from '../../components/ui';
import { deleteListing, fetchListingById, findSimilarListings, updateListing } from '@/lib/api';
import { ROUTES } from '@/lib/constants';
import { formatCapacity, formatDate, formatPrice, formatRating, isValidImageUrl } from '@/lib/utils';
import { Listing } from '@/types/listing';
import styles from './page.module.css';

interface ListingDetailsPageProps {
  params: Promise<{
    id: string;
  }>;
}

/** Review score labels, keyed by the field they read from */
const SCORE_FIELDS: { key: keyof NonNullable<Listing['reviewScores']>; label: string }[] = [
  { key: 'accuracy', label: 'Accuracy' },
  { key: 'cleanliness', label: 'Cleanliness' },
  { key: 'checkin', label: 'Check-in' },
  { key: 'communication', label: 'Communication' },
  { key: 'location', label: 'Location' },
  { key: 'value', label: 'Value' },
];

export default function ListingDetailsPage({ params }: ListingDetailsPageProps) {
  const router = useRouter();

  const [listingId, setListingId] = useState<string>('');
  const [listing, setListing] = useState<Listing | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isUpdating, setIsUpdating] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [similarListings, setSimilarListings] = useState<Listing[]>([]);
  const [similarError, setSimilarError] = useState<string | null>(null);
  const [isLoadingSimilar, setIsLoadingSimilar] = useState(false);

  useEffect(() => {
    params.then(({ id }) => setListingId(id));
  }, [params]);

  useEffect(() => {
    if (!listingId) return;

    const loadListing = async () => {
      setIsLoading(true);
      setError(null);

      const data = await fetchListingById(listingId);

      if (data) {
        setListing(data);
      } else {
        setError('Stay not found');
      }

      setIsLoading(false);
    };

    loadListing();
  }, [listingId]);

  const handleFindSimilar = async () => {
    if (!listing) return;

    setIsLoadingSimilar(true);
    setSimilarError(null);

    const result = await findSimilarListings(listing._id, 6);

    if (result.success) {
      setSimilarListings(result.listings ?? []);
      if ((result.listings ?? []).length === 0) {
        setSimilarError('No similar stays came back for this listing.');
      }
    } else {
      setSimilarError(result.error ?? 'Could not load similar stays');
    }

    setIsLoadingSimilar(false);
  };

  const handleSave = async (updateData: Record<string, unknown>) => {
    if (!listing) return;

    setIsUpdating(true);
    setError(null);
    setSuccessMessage(null);

    const result = await updateListing(listing._id, updateData);

    if (result.success) {
      const updated = await fetchListingById(listing._id);
      if (updated) {
        setListing(updated);
        setSuccessMessage('Stay updated.');
        setIsEditMode(false);
      }
    } else {
      setError(result.error || 'Failed to update the stay');
    }

    setIsUpdating(false);
  };

  const handleDelete = async () => {
    if (!listing) return;

    if (!confirm(`Delete "${listing.name}"? This cannot be undone.`)) {
      return;
    }

    setIsUpdating(true);
    setError(null);
    setSuccessMessage(null);

    const result = await deleteListing(listing._id);

    if (result.success) {
      setSuccessMessage('Stay deleted. Returning to the list...');
      setTimeout(() => router.push(ROUTES.listings), 1500);
    } else {
      setError(result.error || 'Failed to delete the stay');
      setIsUpdating(false);
    }
  };

  if (isLoading) {
    return (
      <div className={styles.page}>
        <main className={styles.main}>
          <LoadingSpinner message="Loading stay..." />
        </main>
      </div>
    );
  }

  if (!listing) {
    return (
      <div className={styles.page}>
        <main className={styles.main}>
          <div className={styles.backLink}>
            <Link href={ROUTES.listings}>&larr; Back to stays</Link>
          </div>
          <ErrorDisplay
            message={error ?? 'Stay not found'}
            onRetry={() => window.location.reload()}
          />
        </main>
      </div>
    );
  }

  const pictureUrl = listing.images?.pictureUrl;
  const scores = listing.reviewScores;

  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <div className={styles.backLink}>
          <Link href={ROUTES.listings}>&larr; Back to stays</Link>
        </div>

        {successMessage && <div className={styles.successMessage}>{successMessage}</div>}
        {error && <div className={styles.errorMessage}>{error}</div>}

        <ActionButtons
          onEdit={() => {
            setIsEditMode(true);
            setError(null);
            setSuccessMessage(null);
          }}
          onDelete={handleDelete}
          isLoading={isUpdating}
          disabled={isEditMode}
        />

        {isEditMode ? (
          <EditListingForm
            listing={listing}
            onSave={handleSave}
            onCancel={() => setIsEditMode(false)}
            isLoading={isUpdating}
          />
        ) : (
          <div className={styles.listingDetails}>
            <div className={styles.photoSection}>
              {isValidImageUrl(pictureUrl) ? (
                <div className={styles.photoContainer}>
                  <Image
                    src={pictureUrl!}
                    alt={`${listing.name} photo`}
                    fill
                    sizes="(max-width: 768px) 100vw, 460px"
                    className={styles.photoImage}
                  />
                </div>
              ) : (
                <div className={styles.photoPlaceholder}>No photo available</div>
              )}

              <div className={styles.priceCard}>
                <div className={styles.priceValue}>{formatPrice(listing.price)}</div>
                {listing.cleaningFee !== undefined && (
                  <div className={styles.priceMeta}>Cleaning fee ${listing.cleaningFee}</div>
                )}
                {listing.minimumNights && (
                  <div className={styles.priceMeta}>Minimum {listing.minimumNights} nights</div>
                )}
              </div>
            </div>

            <div className={styles.listingInfo}>
              <h1 className={styles.title}>{listing.name}</h1>

              {listing.address?.market && (
                <p className={styles.subtitle}>
                  {[listing.address.suburb, listing.address.market, listing.address.country]
                    .filter(Boolean)
                    .join(', ')}
                </p>
              )}

              <div className={styles.basicInfo}>
                {listing.propertyType && (
                  <div className={styles.infoItem}>
                    <strong>Property:</strong> {listing.propertyType}
                  </div>
                )}
                {listing.roomType && (
                  <div className={styles.infoItem}>
                    <strong>Room:</strong> {listing.roomType}
                  </div>
                )}
                {formatCapacity(listing.accommodates, listing.bedrooms) && (
                  <div className={styles.infoItem}>
                    <strong>Sleeps:</strong> {formatCapacity(listing.accommodates, listing.bedrooms)}
                  </div>
                )}
                {listing.beds !== undefined && (
                  <div className={styles.infoItem}>
                    <strong>Beds:</strong> {listing.beds}
                  </div>
                )}
                {listing.bathrooms !== undefined && (
                  <div className={styles.infoItem}>
                    <strong>Bathrooms:</strong> {listing.bathrooms}
                  </div>
                )}
                {listing.cancellationPolicy && (
                  <div className={styles.infoItem}>
                    <strong>Cancellation:</strong> {listing.cancellationPolicy}
                  </div>
                )}
              </div>

              {scores && (
                <div className={styles.section}>
                  <h2 className={styles.sectionTitle}>Review scores</h2>
                  <p className={styles.overallScore}>
                    Overall {formatRating(scores.rating)}
                    {listing.numberOfReviews ? ` from ${listing.numberOfReviews} reviews` : ''}
                  </p>
                  <div className={styles.scoreGrid}>
                    {SCORE_FIELDS.filter((field) => scores[field.key] !== undefined).map((field) => (
                      <div key={field.key} className={styles.scoreCard}>
                        <strong>{field.label}</strong>
                        <div className={styles.scoreValue}>{scores[field.key]}/10</div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {listing.amenities && listing.amenities.length > 0 && (
                <div className={styles.section}>
                  <h2 className={styles.sectionTitle}>Amenities</h2>
                  <div className={styles.tagList}>
                    {listing.amenities.map((amenity) => (
                      <span key={amenity} className={styles.tag}>
                        {amenity}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {listing.summary && (
                <div className={styles.section}>
                  <h2 className={styles.sectionTitle}>Summary</h2>
                  <p className={styles.prose}>{listing.summary}</p>
                </div>
              )}

              {listing.description && listing.description !== listing.summary && (
                <div className={styles.section}>
                  <h2 className={styles.sectionTitle}>Description</h2>
                  <p className={styles.prose}>{listing.description}</p>
                </div>
              )}

              {listing.neighborhoodOverview && (
                <div className={styles.section}>
                  <h2 className={styles.sectionTitle}>The neighbourhood</h2>
                  <p className={styles.prose}>{listing.neighborhoodOverview}</p>
                </div>
              )}

              {listing.transit && (
                <div className={styles.section}>
                  <h2 className={styles.sectionTitle}>Getting around</h2>
                  <p className={styles.prose}>{listing.transit}</p>
                </div>
              )}

              {listing.host && (
                <div className={styles.section}>
                  <h2 className={styles.sectionTitle}>Host</h2>
                  <div className={styles.infoGrid}>
                    {listing.host.hostName && (
                      <div className={styles.infoItem}>
                        <strong>Name:</strong> {listing.host.hostName}
                        {listing.host.hostIsSuperhost ? ' (Superhost)' : ''}
                      </div>
                    )}
                    {listing.host.hostLocation && (
                      <div className={styles.infoItem}>
                        <strong>Based in:</strong> {listing.host.hostLocation}
                      </div>
                    )}
                    {listing.host.hostListingsCount !== undefined && (
                      <div className={styles.infoItem}>
                        <strong>Listings:</strong> {listing.host.hostListingsCount}
                      </div>
                    )}
                    {listing.host.hostIdentityVerified !== undefined && (
                      <div className={styles.infoItem}>
                        <strong>Identity verified:</strong>{' '}
                        {listing.host.hostIdentityVerified ? 'Yes' : 'No'}
                      </div>
                    )}
                  </div>
                  {listing.host.hostAbout && <p className={styles.prose}>{listing.host.hostAbout}</p>}
                </div>
              )}

              {listing.reviews && listing.reviews.length > 0 && (
                <div className={styles.section}>
                  <h2 className={styles.sectionTitle}>
                    Recent reviews ({listing.reviews.length} stored on this document)
                  </h2>
                  <div className={styles.reviewList}>
                    {listing.reviews
                      .slice(-5)
                      .reverse()
                      .map((review, index) => (
                        <div key={review._id ?? index} className={styles.review}>
                          <p className={styles.reviewBody}>{review.comments}</p>
                          <p className={styles.reviewMeta}>
                            {review.reviewerName ?? 'Guest'}
                            {review.date ? ` · ${formatDate(review.date)}` : ''}
                          </p>
                        </div>
                      ))}
                  </div>
                </div>
              )}

              <div className={styles.section}>
                <h2 className={styles.sectionTitle}>Similar stays</h2>
                <p className={styles.sectionNote}>
                  Uses vector search over description embeddings. It needs the embedding backfill
                  to have run for this listing.
                </p>

                <button
                  className={styles.similarButton}
                  onClick={handleFindSimilar}
                  disabled={isLoadingSimilar}
                  type="button"
                >
                  {isLoadingSimilar ? 'Searching...' : 'Find similar stays'}
                </button>

                {similarError && <p className={styles.similarError}>{similarError}</p>}

                {similarListings.length > 0 && (
                  <div className={styles.similarGrid}>
                    {similarListings.map((similar) => (
                      <ListingCard key={similar._id} listing={similar} />
                    ))}
                  </div>
                )}
              </div>

              <div className={styles.section}>
                <h2 className={styles.sectionTitle}>Record details</h2>
                <div className={styles.infoGrid}>
                  <div className={styles.infoItem}>
                    <strong>Listing id:</strong> {listing._id}
                  </div>
                  {listing.firstReview && (
                    <div className={styles.infoItem}>
                      <strong>First review:</strong> {formatDate(listing.firstReview)}
                    </div>
                  )}
                  {listing.lastReview && (
                    <div className={styles.infoItem}>
                      <strong>Last review:</strong> {formatDate(listing.lastReview)}
                    </div>
                  )}
                  {listing.address?.location?.coordinates && (
                    <div className={styles.infoItem}>
                      <strong>Coordinates:</strong>{' '}
                      {listing.address.location.coordinates[1]}, {listing.address.location.coordinates[0]}
                    </div>
                  )}
                  {listing.listingUrl && (
                    <div className={styles.infoItem}>
                      <strong>Source:</strong>{' '}
                      <a href={listing.listingUrl} target="_blank" rel="noopener noreferrer">
                        Original listing
                      </a>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
