import { ListingCardSkeleton, PageSelectorSkeleton, PaginationSkeleton } from '../components';
import pageStyles from './page.module.css';
import listingStyles from './listings.module.css';

/**
 * Shown by Next.js while the listings page loads.
 */
export default function Loading() {
  return (
    <div className={pageStyles.page}>
      <main className={pageStyles.main}>
        <h1 className={listingStyles.pageTitle}>Stays</h1>
        <p className={listingStyles.loadingNote}>Loading stays from the sample_airbnb dataset...</p>

        <PageSelectorSkeleton />

        <div className={listingStyles.listingsGrid}>
          {[...Array(12)].map((_, index) => (
            <ListingCardSkeleton key={index} />
          ))}
        </div>

        <PaginationSkeleton />
      </main>
    </div>
  );
}
