import type { Metadata } from 'next';
import { Suspense } from 'react';
import ListingsClient from './ListingsClient';
import Loading from './loading';

export const metadata: Metadata = {
  title: 'Stays',
  description:
    'Browse and filter stays by property type, room type, market, amenity, price, bedrooms, ' +
    'capacity and review score.',
};

/**
 * The listings view keeps its filters and pagination in the URL, so the interactive part
 * reads useSearchParams and must sit behind a Suspense boundary for the page to prerender.
 */
export default function ListingsPage() {
  return (
    <Suspense fallback={<Loading />}>
      <ListingsClient />
    </Suspense>
  );
}
