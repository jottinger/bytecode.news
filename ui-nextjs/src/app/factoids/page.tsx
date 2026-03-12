import Link from "next/link";
import { listFactoids } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { FactoidListResponse } from "@/lib/types";

function pageHref(page: number, query: string): string {
  const params = new URLSearchParams();
  params.set("page", String(page));
  if (query.trim().length > 0) {
    params.set("q", query.trim());
  }
  return `/factoids?${params.toString()}`;
}

function detailHref(selector: string, page: number, query: string): string {
  const params = new URLSearchParams();
  params.set("page", String(page));
  if (query.trim().length > 0) {
    params.set("q", query.trim());
  }
  return `/factoids/${encodeURIComponent(selector)}?${params.toString()}`;
}

export default async function FactoidsPage({
  searchParams,
}: {
  searchParams?: Promise<{ page?: string; q?: string }>;
}) {
  const params = (await searchParams) || {};
  const page = Number.parseInt(params.page || "0", 10);
  const safePage = Number.isFinite(page) && page >= 0 ? page : 0;
  const query = (params.q || "").trim();

  let list: FactoidListResponse | null = null;
  try {
    list = await listFactoids(safePage, 20, query);
  } catch {
    return (
      <section className="container max-w-screen-xl py-12">
        <div className="notice">
          <h2 className="font-display text-xl">Knowledge Base Unavailable</h2>
          <p className="text-muted-foreground mt-2">
            The factoid API is currently unreachable. Please refresh in a moment.
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="py-8 md:py-12">
      <div className="max-w-3xl mx-auto px-6 md:px-8">
        {/* Page header */}
        <header className="mb-10">
          <div className="border-t-2 border-amber animate-rule-draw" />
          <div className="pt-6 pb-8 border-b border-border/40">
            <p className="section-label text-amber mb-3">Reference</p>
            <h1
              className="font-display text-foreground leading-tight"
              style={{ fontSize: "clamp(2rem, 5vw, 3rem)", letterSpacing: "-0.025em" }}
            >
              Knowledge Base
            </h1>
            <p className="text-muted-foreground/60 text-sm mt-2">
              {list.totalCount} entr{list.totalCount === 1 ? "y" : "ies"}
              {query && <> matching &ldquo;{query}&rdquo;</>}
            </p>
          </div>
        </header>

        {/* Search */}
        <form
          action="/factoids"
          method="get"
          className="flex gap-3 mb-8"
          role="search"
        >
          <input
            className="flex-1 border border-border bg-background text-foreground px-3 py-2 text-sm focus:outline-none focus:border-amber transition-colors"
            style={{ borderRadius: 0, fontFamily: "var(--font-body), Georgia, serif" }}
            type="search"
            name="q"
            defaultValue={query}
            placeholder="Search the knowledge base..."
            aria-label="Search factoids"
          />
          <button
            className="px-4 py-2 text-white cursor-pointer text-sm transition-colors hover:opacity-90"
            style={{
              background: "var(--amber)",
              border: "1px solid var(--amber)",
              fontFamily: "var(--font-mono)",
              fontSize: "0.75rem",
              fontWeight: 600,
              textTransform: "uppercase",
              letterSpacing: "0.08em",
            }}
            type="submit"
          >
            Search
          </button>
        </form>

        {/* Results */}
        {list.factoids.length === 0 ? (
          <div className="py-12 text-center">
            <p className="section-label text-muted-foreground/50 mb-3">No entries found</p>
            <p className="text-muted-foreground/40 text-sm">
              {query ? "Try a different search term." : "The knowledge base is empty."}
            </p>
          </div>
        ) : (
          <div className="animate-fade-in">
            {list.factoids.map((factoid, i) => (
              <Link
                key={factoid.selector}
                href={detailHref(factoid.selector, safePage, query)}
                className="group block"
              >
                <div className={`py-5 ${i > 0 ? "border-t border-border/30" : ""}`}>
                  <div className="flex items-baseline justify-between gap-4">
                    <h2 className="headline-brief text-foreground group-hover:text-amber transition-colors duration-200 min-w-0 truncate">
                      {factoid.selector}
                    </h2>
                    <span className="byline text-muted-foreground/40 shrink-0">
                      {factoid.accessCount || 0} hits
                    </span>
                  </div>
                  <div className="byline text-muted-foreground/50 mt-1.5 flex items-center gap-1.5 flex-wrap">
                    {factoid.updatedBy && (
                      <>
                        <span>{factoid.updatedBy}</span>
                        <span className="text-border/40 mx-0.5">|</span>
                      </>
                    )}
                    <time>{formatDate(factoid.updatedAt)}</time>
                    {factoid.locked && (
                      <>
                        <span className="text-border/40 mx-0.5">|</span>
                        <span className="text-amber-dim">Locked</span>
                      </>
                    )}
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}

        {/* Pagination */}
        {list.totalPages > 1 && (
          <div className="mt-10 flex justify-between items-center border-t border-border/40 pt-6">
            <div>
              {safePage > 0 && (
                <Link className="pagelink" href={pageHref(safePage - 1, query)}>
                  &larr; Previous
                </Link>
              )}
            </div>
            <span className="dateline text-muted-foreground/40">
              Page {safePage + 1} of {list.totalPages}
            </span>
            <div>
              {safePage + 1 < list.totalPages && (
                <Link className="pagelink" href={pageHref(safePage + 1, query)}>
                  Next &rarr;
                </Link>
              )}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
