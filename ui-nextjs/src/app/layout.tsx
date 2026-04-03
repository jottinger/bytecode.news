import type { Metadata } from "next";
import { IBM_Plex_Mono, Instrument_Serif, Source_Serif_4 } from "next/font/google";
import Link from "next/link";
import "./globals.css";
import { getFeatures, listPostsByCategory } from "@/lib/api";
import { AuthNav } from "@/components/auth-nav";
import { GoogleAnalytics } from "@/components/google-analytics";
import { RefreshHomeLink } from "@/components/refresh-home-link";
import { ThemeProvider } from "@/components/layout/theme-provider";
import { ThemeToggle } from "@/components/layout/theme-toggle";

const instrumentSerif = Instrument_Serif({
  variable: "--font-display",
  subsets: ["latin"],
  weight: "400",
});

const sourceSerif = Source_Serif_4({
  variable: "--font-body",
  subsets: ["latin"],
  weight: ["300", "400", "600", "700"],
});

const ibmPlexMono = IBM_Plex_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["300", "400", "500"],
});

export const metadata: Metadata = {
  metadataBase: new URL(
    process.env.BLOG_BASE_URL || process.env.SITE_URL || "https://bytecode.news",
  ),
  title: {
    default: "bytecode.news",
    template: "%s | bytecode.news",
  },
  description: "ByteCode.News: articles, knowledge, and community context.",
  alternates: {
    types: {
      "application/rss+xml": "/feed.xml",
    },
  },
  openGraph: {
    title: "bytecode.news",
    description: "ByteCode.News: articles, knowledge, and community context.",
    type: "website",
    siteName: "bytecode.news",
  },
  twitter: {
    card: "summary_large_image",
    title: "bytecode.news",
    description: "ByteCode.News: articles, knowledge, and community context.",
  },
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  let siteName = "bytecode.news";
  let backendVersionText = "unknown";
  let factoidsEnabled = false;
  let anonymousSubmissionEnabled = true;
  let sidebarPosts: Array<{ id: string; title: string; slug: string }> = [];
  const frontendCommit = (process.env.NEXT_PUBLIC_UI_COMMIT || "unknown").substring(0, 7);
  const frontendBranch = process.env.NEXT_PUBLIC_UI_BRANCH || "unknown";
  const frontendVersionText = `${frontendCommit} (${frontendBranch})`;

  try {
    const features = await getFeatures();
    siteName = features.siteName;
    factoidsEnabled =
      features.operationGroups.includes("factoid") ||
      features.adapters.some((adapter) => adapter.toLowerCase().includes("factoid"));
    anonymousSubmissionEnabled = features.anonymousSubmission;
    const commit = features.version.commit ? features.version.commit.substring(0, 7) : "unknown";
    const branch = features.version.branch || "unknown";
    backendVersionText = `${commit} (${branch})`;
  } catch {
    // Keep rendering during backend interruptions.
  }

  try {
    const sidebar = await listPostsByCategory("_sidebar", 0, 20);
    sidebarPosts = (sidebar.posts || []).map((post) => ({
      id: post.id,
      title: post.title,
      slug: post.slug,
    }));
  } catch {
    // Sidebar content is optional.
  }

  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${instrumentSerif.variable} ${sourceSerif.variable} ${ibmPlexMono.variable} antialiased`}
      >
        <GoogleAnalytics />
        <ThemeProvider>
          <div className="noise-overlay">
            <div className="relative flex min-h-screen flex-col">
              {/* Sticky header */}
              <header className="sticky top-0 z-50 w-full">
                <div className="h-[2px] bg-amber" />
                <div className="bg-background/95 supports-[backdrop-filter]:bg-background/80 backdrop-blur border-b border-border/40">
                  <div className="container flex h-14 max-w-screen-xl items-center">
                    {/* Left: brand + nav */}
                    <div className="hidden md:flex items-center min-w-0">
                      <RefreshHomeLink className="mr-6 flex items-center shrink-0">
                        <span className="font-display text-xl tracking-normal">
                          <span className="text-foreground">
                            {siteName.split(".")[0]}
                          </span>
                          <span className="text-amber">.</span>
                          <span className="text-foreground/50">
                            {siteName.split(".").slice(1).join(".") || "news"}
                          </span>
                        </span>
                      </RefreshHomeLink>
                      <div className="h-5 w-px bg-border/40 mr-5 shrink-0" />
                      <nav className="flex items-center gap-5 min-w-0">
                        <Link
                          href="/"
                          className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 whitespace-nowrap"
                        >
                          Front Page
                        </Link>
                        <Link
                          href="/tags"
                          className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 whitespace-nowrap"
                        >
                          Tags
                        </Link>
                      </nav>
                    </div>

                    {/* Mobile brand */}
                    <div className="md:hidden">
                      <RefreshHomeLink className="flex items-center">
                        <span className="font-display text-lg tracking-normal">
                          <span className="text-foreground">
                            {siteName.split(".")[0]}
                          </span>
                          <span className="text-amber">.</span>
                          <span className="text-foreground/50">
                            {siteName.split(".").slice(1).join(".") || "news"}
                          </span>
                        </span>
                      </RefreshHomeLink>
                    </div>

                    {/* Right: RSS, theme toggle, auth — pushed to far right */}
                    <div className="flex items-center gap-1 ml-auto">
                      <form action="/search" method="get" className="hidden lg:flex items-center gap-1 px-2">
                        <label htmlFor="header-search" className="sr-only">
                          Search posts
                        </label>
                        <input
                          id="header-search"
                          name="q"
                          type="search"
                          placeholder="Search posts..."
                          className="h-8 w-40 rounded border border-border/60 bg-background px-2 text-sm text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:ring-1 focus:ring-amber"
                        />
                        <button
                          type="submit"
                          className="section-label text-muted-foreground hover:text-amber transition-colors duration-200"
                          aria-label="Search"
                        >
                          🔍
                        </button>
                      </form>
                      <div className="hidden lg:block h-4 w-px bg-border/40" />
                      <a
                        className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
                        href="/feed.xml"
                      >
                        RSS
                      </a>
                      <div className="h-4 w-px bg-border/40" />
                      <ThemeToggle />
                      <div className="h-4 w-px bg-border/40" />
                      <AuthNav allowAnonymousSubmission={anonymousSubmissionEnabled} />
                    </div>
                  </div>

                  {/* Mobile nav row */}
                  <nav className="md:hidden flex items-center gap-4 overflow-x-auto px-6 pb-2">
                    <Link
                      href="/"
                      className="section-label text-muted-foreground hover:text-amber transition-colors whitespace-nowrap"
                    >
                      Front Page
                    </Link>
                    <Link
                      href="/tags"
                      className="section-label text-muted-foreground hover:text-amber transition-colors whitespace-nowrap"
                    >
                      Tags
                    </Link>
                    <form action="/search" method="get" className="flex items-center gap-1">
                      <label htmlFor="mobile-header-search" className="sr-only">
                        Search posts
                      </label>
                      <input
                        id="mobile-header-search"
                        name="q"
                        type="search"
                        placeholder="Search..."
                        className="h-8 w-28 rounded border border-border/60 bg-background px-2 text-sm text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:ring-1 focus:ring-amber"
                      />
                      <button
                        type="submit"
                        className="section-label text-muted-foreground hover:text-amber transition-colors whitespace-nowrap"
                        aria-label="Search"
                      >
                        🔍
                      </button>
                    </form>
                  </nav>
                </div>
              </header>

              {/* Main content */}
              <main className="flex-1">{children}</main>

              {/* Editorial footer */}
              <footer className="border-t border-border/40">
                <div className="container max-w-screen-xl">
                  <div className="border-t border-amber/30 mt-px" />

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-8 py-10">
                    <div>
                      <span className="font-display text-xl tracking-normal">
                        <span className="text-foreground">bytecode</span>
                        <span className="text-amber">.</span>
                        <span className="text-foreground/40">news</span>
                      </span>
                      <p className="text-muted-foreground/60 text-sm mt-3 leading-relaxed max-w-xs">
                        Technical writing, software engineering insights, and the craft of
                        building things that work.
                      </p>
                    </div>

                    <div>
                      <h4 className="section-label text-amber mb-4">Sections</h4>
                      <nav className="flex flex-col gap-2">
                        <Link href="/" className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                          Articles
                        </Link>
                        {factoidsEnabled && (
                          <Link href="/factoids" className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                            Factoids
                          </Link>
                        )}
                        <Link href="/karma" className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                          Karma
                        </Link>
                        <Link href="/logs" className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                          Logs
                        </Link>
                        {sidebarPosts.map((post) => (
                          <Link key={post.id} href={`/posts/${post.slug}`} className="text-muted-foreground hover:text-foreground text-sm transition-colors">
                            {post.title}
                          </Link>
                        ))}
                      </nav>
                    </div>

                    <div>
                      <h4 className="section-label text-amber mb-4">System</h4>
                      <div className="flex flex-col gap-2">
                        <span className="dateline text-muted-foreground/40">
                          Backend {backendVersionText}
                        </span>
                        <span className="dateline text-muted-foreground/40">
                          Frontend {frontendVersionText}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="border-t border-border/40 py-5 flex flex-col sm:flex-row items-center justify-between gap-2">
                    <p className="dateline text-muted-foreground/40">
                      &copy; {new Date().getFullYear()} bytecode.news
                    </p>
                    <p className="dateline text-muted-foreground/30">
                      All rights reserved
                    </p>
                  </div>
                </div>
              </footer>
            </div>
          </div>
        </ThemeProvider>
      </body>
    </html>
  );
}
