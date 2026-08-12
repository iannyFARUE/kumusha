import { ExpandableTable } from '@/components';
import { fetchAmenityStats, fetchListingsWithReviews, fetchPropertyTypeStats } from '@/lib/api';
import { formatDate } from '@/lib/utils';
import { AmenityStats, ListingWithReviews, PropertyTypeStats } from '@/types/aggregations';
import styles from './aggregations.module.css';

const RECENT_REVIEWS_LIMIT = 8;
const PROPERTY_TYPE_LIMIT = 20;
const AMENITY_LIMIT = 25;

/**
 * Render on every request rather than at build time.
 *
 * These reports read live data from the backend, which is not running during `next build`.
 * Prerendering them would otherwise bake a "failed to load" page into the build output and
 * serve it until the next revalidation.
 */
export const dynamic = 'force-dynamic';

/**
 * Insights page
 *
 * Renders the three aggregation reports side by side. Each is fetched independently with
 * Promise.allSettled so one slow or failing pipeline does not blank the whole page.
 */
export default async function AggregationsPage() {
  const [reviewsResult, propertyTypeResult, amenitiesResult] = await Promise.allSettled([
    fetchListingsWithReviews(RECENT_REVIEWS_LIMIT),
    fetchPropertyTypeStats(PROPERTY_TYPE_LIMIT),
    fetchAmenityStats(AMENITY_LIMIT),
  ]);

  const reviewsData =
    reviewsResult.status === 'fulfilled'
      ? reviewsResult.value
      : { success: false as const, error: 'Failed to fetch review data' };

  const propertyTypeData =
    propertyTypeResult.status === 'fulfilled'
      ? propertyTypeResult.value
      : { success: false as const, error: 'Failed to fetch property type data' };

  const amenitiesData =
    amenitiesResult.status === 'fulfilled'
      ? amenitiesResult.value
      : { success: false as const, error: 'Failed to fetch amenity data' };

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>Insights</h1>
      <p className={styles.subtitle}>
        Three aggregation pipelines over the same collection: embedded reviews flattened with
        $unwind, statistics grouped by property type, and the amenity array counted across every
        listing.
      </p>

      {/* Most recently reviewed listings */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Most recently reviewed stays</h2>
        <p className={styles.sectionNote}>
          Reviews are embedded inside each listing document, so this report uses $unwind, $group
          and $slice rather than a $lookup against a separate collection.
        </p>

        {reviewsData.success && reviewsData.data ? (
          <ExpandableTable
            initialRowCount={5}
            totalRowCount={(reviewsData.data as ListingWithReviews[]).length}
          >
            <div className={styles.tableContainer}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Stay</th>
                    <th>Market</th>
                    <th>Rating</th>
                    <th>Total reviews</th>
                    <th>Last review</th>
                    <th>Recent reviews</th>
                  </tr>
                </thead>
                <tbody>
                  {(reviewsData.data as ListingWithReviews[]).map((listing) => (
                    <tr key={listing._id}>
                      <td className={styles.primaryCell}>{listing.name}</td>
                      <td>{listing.market ?? 'N/A'}</td>
                      <td>{listing.reviewScore ?? 'N/A'}</td>
                      <td>{listing.totalReviews ?? 0}</td>
                      <td>{formatDate(listing.mostRecentReviewDate) || 'N/A'}</td>
                      <td>
                        <div className={styles.reviewsCell}>
                          {listing.recentReviews && listing.recentReviews.length > 0 ? (
                            listing.recentReviews.slice(0, 2).map((review, index) => (
                              <div key={`${listing._id}-${index}`} className={styles.review}>
                                <div className={styles.reviewText}>
                                  &ldquo;{(review.comments ?? 'No text').slice(0, 90)}
                                  {(review.comments?.length ?? 0) > 90 ? '...' : ''}&rdquo;
                                </div>
                                <div className={styles.reviewMeta}>
                                  {review.reviewerName ?? 'Guest'}
                                  {review.date ? ` on ${formatDate(review.date)}` : ''}
                                </div>
                              </div>
                            ))
                          ) : (
                            <div>No recent reviews</div>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ExpandableTable>
        ) : (
          <div className={styles.error}>
            Failed to load review activity: {reviewsData.error || 'Unknown error'}
          </div>
        )}
      </section>

      {/* Property type statistics */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Price and rating by property type</h2>
        <p className={styles.sectionNote}>
          A $group pipeline with count, average, minimum and maximum accumulators. Prices are
          converted from Decimal128 before averaging so every statistic is a plain number.
        </p>

        {propertyTypeData.success && propertyTypeData.data ? (
          <ExpandableTable
            initialRowCount={8}
            totalRowCount={(propertyTypeData.data as PropertyTypeStats[]).length}
          >
            <div className={styles.tableContainer}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Property type</th>
                    <th>Listings</th>
                    <th>Avg price</th>
                    <th>Lowest</th>
                    <th>Highest</th>
                    <th>Avg rating</th>
                    <th>Avg guests</th>
                    <th>Total reviews</th>
                  </tr>
                </thead>
                <tbody>
                  {(propertyTypeData.data as PropertyTypeStats[]).map((stats) => (
                    <tr key={stats.propertyType}>
                      <td className={styles.primaryCell}>{stats.propertyType}</td>
                      <td>{stats.listingCount.toLocaleString()}</td>
                      <td>{stats.averagePrice !== undefined ? `$${stats.averagePrice.toFixed(2)}` : 'N/A'}</td>
                      <td>{stats.lowestPrice !== undefined ? `$${stats.lowestPrice.toFixed(0)}` : 'N/A'}</td>
                      <td>{stats.highestPrice !== undefined ? `$${stats.highestPrice.toFixed(0)}` : 'N/A'}</td>
                      <td>{stats.averageRating !== undefined ? stats.averageRating.toFixed(1) : 'N/A'}</td>
                      <td>{stats.averageAccommodates !== undefined ? stats.averageAccommodates.toFixed(1) : 'N/A'}</td>
                      <td>{stats.totalReviews?.toLocaleString() ?? 'N/A'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ExpandableTable>
        ) : (
          <div className={styles.error}>
            Failed to load property type statistics: {propertyTypeData.error || 'Unknown error'}
          </div>
        )}
      </section>

      {/* Amenity statistics */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Most common amenities</h2>
        <p className={styles.sectionNote}>
          $unwind turns each listing into one document per amenity, so grouping counts how many
          listings offer each one and what they charge on average.
        </p>

        {amenitiesData.success && amenitiesData.data ? (
          <ExpandableTable
            initialRowCount={10}
            totalRowCount={(amenitiesData.data as AmenityStats[]).length}
          >
            <div className={styles.tableContainer}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Rank</th>
                    <th>Amenity</th>
                    <th>Listings offering it</th>
                    <th>Avg price</th>
                    <th>Avg rating</th>
                  </tr>
                </thead>
                <tbody>
                  {(amenitiesData.data as AmenityStats[]).map((stats, index) => (
                    <tr key={stats.amenity}>
                      <td className={styles.rank}>#{index + 1}</td>
                      <td className={styles.primaryCell}>{stats.amenity}</td>
                      <td>{stats.listingCount.toLocaleString()}</td>
                      <td>{stats.averagePrice !== undefined ? `$${stats.averagePrice.toFixed(2)}` : 'N/A'}</td>
                      <td>{stats.averageRating !== undefined ? stats.averageRating.toFixed(1) : 'N/A'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </ExpandableTable>
        ) : (
          <div className={styles.error}>
            Failed to load amenity statistics: {amenitiesData.error || 'Unknown error'}
          </div>
        )}
      </section>
    </div>
  );
}
