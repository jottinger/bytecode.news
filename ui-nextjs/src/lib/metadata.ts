import type { Metadata } from "next";

// @lat: [[http-api#Public Metadata]]
function normalizeBaseUrl(baseUrl: string): string {
  return baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
}

export function getSiteUrl(): string {
  return normalizeBaseUrl(
    process.env.BLOG_BASE_URL || process.env.SITE_URL || "https://bytecode.news",
  );
}

export function absoluteSiteUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${getSiteUrl()}${normalizedPath}`;
}

type PublicMetadataOptions = {
  title: string;
  description: string;
  path: string;
  type?: "website" | "article";
};

export function buildPublicMetadata({
  title,
  description,
  path,
  type = "website",
}: PublicMetadataOptions): Metadata {
  const canonicalUrl = absoluteSiteUrl(path);

  return {
    title,
    description,
    alternates: {
      canonical: canonicalUrl,
    },
    openGraph: {
      title,
      description,
      type,
      url: canonicalUrl,
      siteName: "bytecode.news",
      images: [
        {
          url: absoluteSiteUrl("/opengraph-image"),
          alt: "bytecode.news",
        },
      ],
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images: [
        {
          url: absoluteSiteUrl("/twitter-image"),
          alt: "bytecode.news",
        },
      ],
    },
  };
}
