import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";
import { getFeatures, listPostsByCategory } from "@/lib/api";
import { AuthNav } from "@/components/auth-nav";
import { RefreshHomeLink } from "@/components/refresh-home-link";

export const metadata: Metadata = {
  title: {
    default: "bytecode.news",
    template: "%s | bytecode.news",
  },
  description: "ByteCode.News: articles, knowledge, and community context.",
  openGraph: {
    title: "bytecode.news",
    description: "ByteCode.News: articles, knowledge, and community context.",
    type: "website",
  },
  twitter: {
    card: "summary",
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
  let anonymousSubmissionEnabled = false;
  let factoidsEnabled = false;
  let karmaEnabled = false;
  let sidebarPosts: Array<{ id: string; title: string; slug: string }> = [];
  const frontendCommit = (process.env.NEXT_PUBLIC_UI_COMMIT || "unknown").substring(0, 7);
  const frontendBranch = process.env.NEXT_PUBLIC_UI_BRANCH || "unknown";
  const frontendVersionText = `${frontendCommit} (${frontendBranch})`;

  try {
    const features = await getFeatures();
    siteName = features.siteName;
    anonymousSubmissionEnabled = features.anonymousSubmission;
    factoidsEnabled =
      features.operationGroups.includes("factoid") ||
      features.adapters.some((adapter) => adapter.toLowerCase().includes("factoid"));
    karmaEnabled =
      features.operationGroups.includes("karma") ||
      features.adapters.some((adapter) => adapter.toLowerCase().includes("karma"));
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
    <html lang="en">
      <body>
        <div className="app-shell">
          <div className="site-topline" />
          <header className="site-header">
            <div className="container site-header-inner">
              <div>
                <RefreshHomeLink className="brand">
                  {siteName.split(".")[0]}
                  <span className="brand-dot">.</span>
                  {siteName.split(".").slice(1).join(".") || "news"}
                </RefreshHomeLink>
              </div>
              <div>
                <nav className="site-nav">
                  <a className="nav-link" href="/feed.xml">
                    RSS
                  </a>
                  <AuthNav />
                </nav>
              </div>
            </div>
            <div className="container">
              <nav className="feature-tabs" aria-label="Features">
                <RefreshHomeLink className="feature-tab">Blog</RefreshHomeLink>
                {factoidsEnabled ? (
                  <Link className="feature-tab" href="/factoids">
                    Factoids
                  </Link>
                ) : null}
                {karmaEnabled ? (
                  <Link className="feature-tab" href="/karma">
                    Karma
                  </Link>
                ) : null}
                <Link className="feature-tab" href="/logs">
                  Logs
                </Link>
                {anonymousSubmissionEnabled ? (
                  <Link className="feature-tab" href="/submit">
                    Submit Post
                  </Link>
                ) : null}
              </nav>
            </div>
          </header>

          <main className="main">
            <div className="container">{children}</div>
          </main>

          <footer className="site-footer">
            <div className="container footer-inner">
              <div className="brand">
                bytecode<span className="brand-dot">.</span>news
              </div>
              <div className="footer-meta">
                <div className="versionline">
                  Backend {backendVersionText} || Frontend {frontendVersionText}
                </div>
                {sidebarPosts.length > 0 ? (
                  <nav className="site-nav" aria-label="Footer pages">
                    {sidebarPosts.map((post) => (
                      <Link key={post.id} className="nav-link" href={`/posts/${post.slug}`}>
                        {post.title}
                      </Link>
                    ))}
                  </nav>
                ) : null}
              </div>
            </div>
          </footer>
        </div>
      </body>
    </html>
  );
}
