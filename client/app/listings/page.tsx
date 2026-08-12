import { Suspense } from 'react';
import ListingsClient from './ListingsClient';
import Loading from './loading';

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
