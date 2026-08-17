import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Emits a self-contained server bundle with only the dependencies actually imported, so the
  // container image does not have to carry all of node_modules. Harmless outside Docker.
  output: 'standalone',
  images: {
    // Listing photos are hosted on third-party CDNs referenced by the dataset
    remotePatterns: [
      {
        protocol: 'https',
        hostname: '**',
      },
      {
        protocol: 'http',
        hostname: '**',
      },
    ],
    formats: ['image/avif', 'image/webp'],
    deviceSizes: [640, 750, 828, 1080, 1200, 1920, 2048, 3840],
    imageSizes: [16, 32, 48, 64, 96, 128, 256, 384],
  },
  compress: true,
};

export default nextConfig;
