import type { Metadata } from 'next';
import { fetchListingById } from '@/lib/api';
import { APP_CONFIG } from '@/lib/constants';
import { isValidImageUrl } from '@/lib/utils';
import ListingDetailClient from './ListingDetailClient';

/** Roughly the length a link preview will show before truncating anyway */
const DESCRIPTION_LIMIT = 200;

interface ListingDetailsPageProps {
  params: Promise<{
    id: string;
  }>;
}

/**
 * Collapses the whitespace in a listing's prose and trims it to a length suitable for a
 * description meta tag. The dataset's summaries contain hard line breaks, which render badly
 * in link previews.
 */
function toPreviewText(...candidates: (string | undefined)[]): string | undefined {
  const text = candidates.find((candidate) => candidate && candidate.trim().length > 0);

  if (!text) {
    return undefined;
  }

  const collapsed = text.replace(/\s+/g, ' ').trim();

  return collapsed.length > DESCRIPTION_LIMIT
    ? `${collapsed.slice(0, DESCRIPTION_LIMIT - 1).trimEnd()}…`
    : collapsed;
}

/**
 * Builds per-listing metadata so that sharing a link to a stay shows its name, a real
 * description and its photo rather than the generic site-wide title.
 *
 * <p>This is the reason the route is split: metadata can only be generated from a server
 * component, while the detail view itself is interactive and has to stay on the client.
 */
export async function generateMetadata({ params }: ListingDetailsPageProps): Promise<Metadata> {
  const { id } = await params;
  const listing = await fetchListingById(id);

  if (!listing) {
    return {
      title: 'Stay not found',
      description: 'This stay could not be found.',
    };
  }

  // The layout's title template appends the site name, but openGraph titles are not templated,
  // so those are built with it spelled out
  const title = listing.name;
  const description =
    toPreviewText(listing.summary, listing.description, listing.neighborhoodOverview) ??
    APP_CONFIG.description;

  const imageUrl = listing.images?.pictureUrl;
  const images = isValidImageUrl(imageUrl) ? [{ url: imageUrl as string }] : undefined;

  return {
    title,
    description,
    openGraph: {
      title: `${title} - ${APP_CONFIG.name}`,
      description,
      type: 'article',
      images,
    },
    twitter: {
      card: images ? 'summary_large_image' : 'summary',
      title: `${title} - ${APP_CONFIG.name}`,
      description,
      images,
    },
  };
}

export default async function ListingDetailsPage({ params }: ListingDetailsPageProps) {
  const { id } = await params;

  return <ListingDetailClient listingId={id} />;
}
