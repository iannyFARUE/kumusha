import React from 'react';
import styles from './LoadingSkeleton.module.css';

interface SkeletonProps {
  variant?: 'text' | 'title' | 'largeTitle' | 'button' | 'card' | 'input';
  size?: 'small' | 'medium' | 'large' | 'full' | 'half';
  width?: string;
  height?: string;
  className?: string;
}

const VARIANT_CLASSES: Record<NonNullable<SkeletonProps['variant']>, string> = {
  text: styles.skeletonText,
  title: styles.skeletonTitle,
  largeTitle: styles.skeletonLargeTitle,
  button: styles.skeletonButton,
  card: styles.skeletonCard,
  input: styles.skeletonInput,
};

const SIZE_CLASSES: Record<NonNullable<SkeletonProps['size']>, string> = {
  small: styles.sizeSmall,
  medium: styles.sizeMedium,
  large: styles.sizeLarge,
  full: styles.sizeFull,
  half: styles.sizeHalf,
};

export function Skeleton({ variant = 'text', size, width, height, className = '' }: SkeletonProps) {
  const style: React.CSSProperties = {};
  if (width) style.width = width;
  if (height) style.height = height;

  const classes = [VARIANT_CLASSES[variant], size ? SIZE_CLASSES[size] : '', className]
    .filter(Boolean)
    .join(' ');

  return <div className={classes} style={style} />;
}

export function ListingCardSkeleton() {
  return (
    <div className={styles.listingCardSkeleton}>
      <Skeleton variant="card" className={styles.photoSkeleton} />
      <div className={styles.infoSkeleton}>
        <Skeleton variant="title" size="large" />
        <Skeleton variant="text" size="medium" />
        <Skeleton variant="text" size="small" />
        <Skeleton variant="button" />
      </div>
    </div>
  );
}

export function PageSelectorSkeleton() {
  return (
    <div className={styles.pageSelectorSkeleton}>
      <Skeleton variant="text" width="120px" />
      <Skeleton variant="input" width="60px" />
    </div>
  );
}

export function PaginationSkeleton() {
  return (
    <div className={styles.paginationSkeleton}>
      <Skeleton variant="button" width="90px" />
      <Skeleton variant="text" width="60px" />
      <Skeleton variant="button" width="90px" />
    </div>
  );
}

export function ScoreCardSkeleton() {
  return (
    <div className={styles.scoreCardSkeleton}>
      <Skeleton variant="text" width="70px" height="14px" />
      <Skeleton variant="text" width="45px" height="20px" />
    </div>
  );
}

export function ListingDetailsSkeleton() {
  return (
    <>
      <Skeleton variant="largeTitle" size="half" />

      <div className={styles.stack}>
        {[...Array(4)].map((_, index) => (
          <div key={index} className={styles.row}>
            <Skeleton variant="text" size="small" />
            <Skeleton variant="text" size="medium" />
          </div>
        ))}
      </div>

      <div className={styles.scoreGrid}>
        {[...Array(4)].map((_, index) => (
          <ScoreCardSkeleton key={index} />
        ))}
      </div>

      <div className={styles.stack}>
        <Skeleton variant="title" width="80px" />
        <Skeleton variant="text" size="full" />
        <Skeleton variant="text" size="large" />
        <Skeleton variant="text" size="large" />
      </div>
    </>
  );
}
