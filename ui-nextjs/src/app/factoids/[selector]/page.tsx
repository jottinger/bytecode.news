import Link from "next/link";
import { ApiError, getFactoid } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { FactoidAttribute } from "@/lib/types";

function uniqueAttributes(attributes: FactoidAttribute[]): FactoidAttribute[] {
  const seen = new Set<string>();
  const unique: FactoidAttribute[] = [];
  for (const attribute of attributes || []) {
    const key = String(attribute?.type || "").toLowerCase();
    if (!key || seen.has(key)) {
      continue;
    }
    seen.add(key);
    unique.push(attribute);
  }
  return unique;
}

function prettyType(type: string): string {
  const normalized = type.toLowerCase();
  return normalized === "seealso" ? "See Also" : normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function strippedValue(type: string, rendered: string): string {
  const normalized = type.toLowerCase();
  if (normalized === "tags") {
    return rendered.replace(/^tags?:\s*/i, "").trim();
  }
  if (normalized === "seealso") {
    return rendered.replace(/^see also:\s*/i, "").trim();
  }
  return rendered;
}

export default async function FactoidDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ selector: string }>;
  searchParams?: Promise<{ page?: string; q?: string }>;
}) {
  const { selector } = await params;
  const query = ((await searchParams)?.q || "").trim();
  const page = Number.parseInt((await searchParams)?.page || "0", 10);
  const safePage = Number.isFinite(page) && page >= 0 ? page : 0;
  const decodedSelector = decodeURIComponent(selector);
  const backParams = new URLSearchParams({ page: String(safePage) });
  if (query.length > 0) {
    backParams.set("q", query);
  }
  const backHref = `/factoids?${backParams.toString()}`;

  try {
    const detail = await getFactoid(decodedSelector);
    const attributes = uniqueAttributes(detail.attributes || []);

    return (
      <section className="py-8 md:py-12">
        <div className="max-w-3xl mx-auto px-6 md:px-8">
          {/* Back link */}
          <Link
            href={backHref}
            className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 inline-flex items-center gap-1.5 mb-6"
          >
            <span>&larr;</span> Knowledge Base
          </Link>

          {/* Header */}
          <article>
            <header className="mb-8">
              <div className="border-t-2 border-amber mb-6" />
              <h1
                className="font-display text-foreground leading-tight mb-4"
                style={{ fontSize: "clamp(1.8rem, 4vw, 2.5rem)", letterSpacing: "-0.02em" }}
              >
                {detail.selector}
              </h1>
              <div className="byline text-muted-foreground/60 flex items-center gap-1.5 flex-wrap">
                {detail.updatedBy && (
                  <>
                    <span>By {detail.updatedBy}</span>
                    <span className="text-border/40 mx-0.5">|</span>
                  </>
                )}
                <time>{formatDate(detail.updatedAt)}</time>
                <span className="text-border/40 mx-0.5">|</span>
                <span>{detail.accessCount || 0} lookups</span>
                {detail.locked && (
                  <>
                    <span className="text-border/40 mx-0.5">|</span>
                    <span className="text-amber-dim">Locked</span>
                  </>
                )}
              </div>
            </header>

            <div className="border-t border-border/40 mb-8" />

            {/* Attributes */}
            {attributes.length === 0 ? (
              <p className="text-muted-foreground">No attributes defined.</p>
            ) : (
              <dl className="space-y-6">
                {attributes.map((attribute, index) => {
                  const type = String(attribute.type || "");
                  const rendered = String(attribute.rendered || "").trim();
                  const value = String(attribute.value || "");
                  const text = strippedValue(type, rendered || value);
                  return (
                    <div key={`${type}-${index}`} className="group">
                      <dt className="section-label text-amber mb-1.5">
                        {prettyType(type)}
                      </dt>
                      <dd className="text-foreground leading-relaxed" style={{ fontFamily: "var(--font-body), Georgia, serif" }}>
                        {text}
                      </dd>
                    </div>
                  );
                })}
              </dl>
            )}

            {/* Footer */}
            <div className="border-t border-border/40 mt-10 pt-6">
              <Link
                href={backHref}
                className="pagelink inline-flex items-center gap-1.5"
              >
                <span>&larr;</span> Back to Knowledge Base
              </Link>
            </div>
          </article>
        </div>
      </section>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <section className="py-8 md:py-12">
          <div className="max-w-3xl mx-auto px-6 md:px-8">
            <Link
              href={backHref}
              className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 inline-flex items-center gap-1.5 mb-6"
            >
              <span>&larr;</span> Knowledge Base
            </Link>
            <article>
              <header className="mb-6">
                <div className="border-t-2 border-amber mb-6" />
                <h1 className="font-display text-foreground leading-tight mb-3" style={{ fontSize: "clamp(1.6rem, 4vw, 2.2rem)", letterSpacing: "-0.02em" }}>
                  Factoid not found
                </h1>
                <p className="text-muted-foreground leading-relaxed">
                  <code>{decodedSelector}</code> does not exist yet.
                </p>
              </header>
              <div className="border-t border-border/40 mb-6" />
              <p className="text-muted-foreground leading-relaxed">
                You can create this factoid through bot operations (for example, in IRC or other connected adapters).
              </p>
              <p className="text-muted-foreground leading-relaxed mt-3">
                Try: <code>!{decodedSelector} is &lt;value&gt;</code>
              </p>
            </article>
          </div>
        </section>
      );
    }
    return (
      <section className="container max-w-screen-xl py-12">
        <div className="notice">
          <h2 className="font-display text-xl">Factoid Unavailable</h2>
          <p className="text-muted-foreground mt-2">
            The factoid API is currently unreachable. Please refresh shortly.
          </p>
        </div>
      </section>
    );
  }
}
