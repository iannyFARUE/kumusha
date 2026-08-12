import type { Metadata } from 'next';
import Link from 'next/link';
import './globals.css';
import styles from './layout.module.css';
import { APP_CONFIG, ROUTES } from './lib/constants';

export const metadata: Metadata = {
  title: 'Kumusha - MongoDB stay explorer',
  description: 'Explore stays from the MongoDB sample_airbnb dataset',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        <nav className={styles.navigation}>
          <div className={styles.navContainer}>
            <Link href={ROUTES.home} className={styles.logo}>
              {APP_CONFIG.name}
            </Link>
            <div className={styles.navLinks}>
              <Link href={ROUTES.home} className={styles.navLink}>
                Home
              </Link>
              <Link href={ROUTES.listings} className={styles.navLink}>
                Stays
              </Link>
              <Link href={ROUTES.aggregations} className={styles.navLink}>
                Insights
              </Link>
            </div>
          </div>
        </nav>
        {children}
      </body>
    </html>
  );
}
