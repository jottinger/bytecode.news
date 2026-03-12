import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";
import { getFeatures } from "@/lib/api";
import { AuthNav } from "@/components/auth-nav";
import { RefreshHomeLink } from "@/components/refresh-home-link";

export const metadata: Metadata = {
  title: "bytecode.news",
  description: "Next.js SSR UI for bytecode.news",
};

function formatEditionDate(): string {
  return new Date().toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  let siteName = "bytecode.news";
  let backendVersionText = "unknown";
  const frontendCommit = (process.env.NEXT_PUBLIC_UI_COMMIT || "unknown").substring(0, 7);
  const frontendBranch = process.env.NEXT_PUBLIC_UI_BRANCH || "unknown";
  const frontendVersionText = `${frontendCommit} (${frontendBranch})`;

  try {
    const features = await getFeatures();
    siteName = features.siteName;
    const commit = features.version.commit ? features.version.commit.substring(0, 7) : "unknown";
    const branch = features.version.branch || "unknown";
    backendVersionText = `${commit} (${branch})`;
  } catch {
    // Keep rendering during backend interruptions.
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
                <div className="dateline">{formatEditionDate()}</div>
                <nav className="site-nav">
                  <RefreshHomeLink className="nav-link">Front Page</RefreshHomeLink>
                  <Link className="nav-link" href="/submit">
                    Submit
                  </Link>
                  <a className="nav-link" href="/feed.xml">
                    RSS
                  </a>
                  <AuthNav />
                </nav>
              </div>
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
                <div className="dateline">Read-only Next.js view over the public API</div>
                <div className="versionline">
                  Backend {backendVersionText} || Frontend {frontendVersionText}
                </div>
              </div>
            </div>
          </footer>
        </div>
      </body>
    </html>
  );
}
