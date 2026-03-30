import type { Metadata } from "next";
import Link from "next/link";
import { getTaxonomySnapshot } from "@/lib/api";
import { buildPublicMetadata } from "@/lib/metadata";

export const metadata: Metadata = buildPublicMetadata({
  title: "Tags",
  description: "Browse articles by tag on bytecode.news",
  path: "/tags",
});

export default async function TagsPage() {
  let tags: Record<string, number> = {};

  try {
    const snapshot = await getTaxonomySnapshot();
    tags = snapshot.tags || {};
  } catch {
    return (
      <section className="container max-w-screen-xl py-12">
        <div className="border-t-2 border-amber pt-6 animate-fade-up">
          <p className="section-label text-amber mb-3">Notice</p>
          <h2 className="font-display text-xl">Backend Temporarily Unavailable</h2>
          <p className="text-muted-foreground mt-2">
            The blog API is currently unreachable. Please refresh in a moment.
          </p>
        </div>
      </section>
    );
  }

  const sorted = Object.entries(tags).sort((a, b) => a[0].localeCompare(b[0]));

  return (
    <section className="py-8 md:py-12">
      <div className="max-w-3xl mx-auto px-6 md:px-8">
        <header className="mb-10">
          <div className="border-t-2 border-amber animate-rule-draw" />
          <div className="pt-6 pb-8 border-b border-border/40">
            <p className="section-label text-amber mb-3">Index</p>
            <h1
              className="font-display text-foreground leading-tight"
              style={{ fontSize: "clamp(2rem, 5vw, 3rem)", letterSpacing: "-0.025em" }}
            >
              Tags
            </h1>
            <p className="text-muted-foreground/60 text-sm mt-2">
              {sorted.length} tag{sorted.length === 1 ? "" : "s"} across all articles
            </p>
          </div>
        </header>

        {sorted.length === 0 ? (
          <div className="py-12 text-center">
            <p className="section-label text-muted-foreground/50 mb-3">No tags yet</p>
            <p className="text-muted-foreground/40 text-sm">
              Tags will appear here as articles are published.
            </p>
          </div>
        ) : (
          <div className="animate-fade-in flex flex-wrap gap-3">
            {sorted.map(([tag, count]) => (
              <Link
                key={tag}
                href={`/tags/${encodeURIComponent(tag)}`}
                className="group inline-flex items-center gap-2 border border-border/60 px-4 py-2.5 hover:border-amber transition-colors"
              >
                <span className="font-mono text-sm text-foreground group-hover:text-amber transition-colors">
                  {tag}
                </span>
                <span className="font-mono text-xs text-muted-foreground/40">
                  {count}
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
