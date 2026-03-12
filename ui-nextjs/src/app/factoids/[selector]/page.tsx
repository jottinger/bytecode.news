import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError, getFactoid } from "@/lib/api";
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
  return normalized === "seealso" ? "see also" : normalized;
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
      <article className="story-card">
        <header>
          <h1>{detail.selector}</h1>
          <p className="meta">
            Updated by {detail.updatedBy || "unknown"} | {new Date(detail.updatedAt).toLocaleString()} |{" "}
            {detail.accessCount || 0} lookups{detail.locked ? " | locked" : ""}
          </p>
        </header>

        {attributes.length === 0 ? (
          <p>No attributes.</p>
        ) : (
          <dl className="factoid-detail-list">
            {attributes.map((attribute, index) => {
              const type = String(attribute.type || "");
              const rendered = String(attribute.rendered || "").trim();
              const value = String(attribute.value || "");
              const text = strippedValue(type, rendered || value);
              return (
                <div key={`${type}-${index}`} className="factoid-detail-row">
                  <dt>{prettyType(type)}</dt>
                  <dd>{text}</dd>
                </div>
              );
            })}
          </dl>
        )}

        <footer>
          <Link className="pagelink" href={backHref}>
            Back to Knowledge Base
          </Link>
        </footer>
      </article>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    return (
      <section className="notice">
        <h2>Factoid Unavailable</h2>
        <p>The factoid API is currently unreachable. Please refresh shortly.</p>
      </section>
    );
  }
}
