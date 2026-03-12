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
      <section className="notice">
        <h2>Knowledge Base Unavailable</h2>
        <p>The factoid API is currently unreachable. Please refresh in a moment.</p>
      </section>
    );
  }

  return (
    <section className="story-list">
      <header className="factoid-header">
        <h1>Knowledge Base</h1>
        <form action="/factoids" method="get" className="factoid-search" role="search">
          <input
            className="auth-input"
            type="search"
            name="q"
            defaultValue={query}
            placeholder="Search factoids..."
            aria-label="Search factoids"
          />
          <button className="auth-button" type="submit">
            Search
          </button>
        </form>
      </header>

      {list.factoids.length === 0 ? (
        <section className="notice">
          <p>{query ? `No factoids matching "${query}".` : "No factoids yet."}</p>
        </section>
      ) : (
        <table className="factoid-table" role="grid">
          <thead>
            <tr>
              <th>Name</th>
              <th>Updated by</th>
              <th>Updated</th>
              <th>Hits</th>
            </tr>
          </thead>
          <tbody>
            {list.factoids.map((factoid) => (
              <tr key={factoid.selector}>
                <td>
                  <Link href={detailHref(factoid.selector, safePage, query)}>{factoid.selector}</Link>
                </td>
                <td>{factoid.updatedBy || ""}</td>
                <td>{formatDate(factoid.updatedAt)}</td>
                <td>{factoid.accessCount || 0}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="pagination">
        <div>
          {safePage > 0 ? (
            <Link className="pagelink" href={pageHref(safePage - 1, query)}>
              Previous
            </Link>
          ) : null}
        </div>
        <div>
          {safePage + 1 < list.totalPages ? (
            <Link className="pagelink" href={pageHref(safePage + 1, query)}>
              Next
            </Link>
          ) : null}
        </div>
      </div>
    </section>
  );
}
