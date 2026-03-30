import type { Metadata } from "next";
import Link from "next/link";
import { listSearchPosts } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { ContentListResponse, ContentSummary } from "@/lib/types";
import { TagLink } from "@/components/tag-link";

export const metadata: Metadata = {
  title: "Search",
  description: "Search published posts on bytecode.news",
  openGraph: {
    title: "Search",
    description: "Search published posts on bytecode.news",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "Search",
    description: "Search published posts on bytecode.news",
  },
};

function buildPostHref(post: ContentSummary): string {
  return `/posts/${post.slug}`;
}

export default async function SearchPage({
  searchParams,
}: {
  searchParams?: Promise<{ q?: string; page?: string }>;
}) {
  const sp = (await searchParams) || {};
  const query = (sp.q || "").trim();
  const page = Number.parseInt(sp.page || "0", 10);
  const safePage = Number.isFinite(page) && page >= 0 ? page : 0;

  if (query.length === 0) {
    return (
      <section className="py-8 md:py-12">
        <div className="max-w-3xl mx-auto px-6 md:px-8">
          <header className="mb-8">
            <div className="border-t-2 border-amber animate-rule-draw" />
            <div className="pt-6 pb-8 border-b border-border/40">
              <p className="section-label text-amber mb-3">Search</p>
              <h1
                className="font-display text-foreground leading-tight"
                style={{ fontSize: "clamp(2rem, 5vw, 3rem)", letterSpacing: "-0.025em" }}
              >
                Search Posts
              </h1>
              <p className="text-muted-foreground/60 text-sm mt-2">
                Enter a search term in the header search box.
              </p>
            </div>
          </header>
        </div>
      </section>
    );
  }

  let list: ContentListResponse | null = null;
  try {
    list = await listSearchPosts(query, safePage, 20);
  } catch {
    return (
      <section className="container max-w-screen-xl py-12">
        <div className="notice">
          <h2 className="font-display text-xl">Search Temporarily Unavailable</h2>
          <p className="text-muted-foreground mt-2">
            The blog API is currently unreachable. Please refresh in a moment.
          </p>
        </div>
      </section>
    );
  }

  const posts = list.posts;

  return (
    <section className="py-8 md:py-12">
      <div className="max-w-3xl mx-auto px-6 md:px-8">
        <header className="mb-10">
          <div className="border-t-2 border-amber animate-rule-draw" />
          <div className="pt-6 pb-8 border-b border-border/40">
            <p className="section-label text-amber mb-3">Search</p>
            <h1
              className="font-display text-foreground leading-tight"
              style={{ fontSize: "clamp(2rem, 5vw, 3rem)", letterSpacing: "-0.025em" }}
            >
              Results for &quot;{query}&quot;
            </h1>
            <p className="text-muted-foreground/60 text-sm mt-2">
              {list.totalCount} match{list.totalCount === 1 ? "" : "es"}
            </p>
          </div>
        </header>

        {posts.length === 0 ? (
          <div className="py-12 text-center">
            <p className="section-label text-muted-foreground/50 mb-3">No results</p>
            <p className="text-muted-foreground/40 text-sm">
              No posts matched &quot;{query}&quot;.
            </p>
          </div>
        ) : (
          <div className="animate-fade-in">
            {posts.map((post, i) => (
              <ArticleRow key={post.id} post={post} first={i === 0} />
            ))}
          </div>
        )}

        {list.totalPages > 1 && (
          <div className="mt-10 flex justify-between items-center border-t border-border/40 pt-6">
            <div>
              {safePage > 0 && (
                <Link
                  className="pagelink"
                  href={`/search?q=${encodeURIComponent(query)}&page=${safePage - 1}`}
                >
                  &larr; Previous
                </Link>
              )}
            </div>
            <span className="dateline text-muted-foreground/40">
              Page {safePage + 1} of {list.totalPages}
            </span>
            <div>
              {safePage + 1 < list.totalPages && (
                <Link
                  className="pagelink"
                  href={`/search?q=${encodeURIComponent(query)}&page=${safePage + 1}`}
                >
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

function ArticleRow({ post, first }: { post: ContentSummary; first: boolean }) {
  const href = buildPostHref(post);
  return (
    <article className={`group py-6 ${first ? "" : "border-t border-border/30"}`}>
      <div className="flex items-start gap-6">
        <div className="flex-1 min-w-0">
          <h2 className="headline-secondary text-foreground group-hover:text-amber transition-colors duration-200 mb-2">
            <Link href={href}>{post.title}</Link>
          </h2>
          {post.excerpt && (
            <p className="text-muted-foreground text-sm leading-relaxed line-clamp-2 mb-3">
              {post.excerpt}
            </p>
          )}
          <div className="byline text-muted-foreground/50 flex items-center gap-1.5 flex-wrap">
            <span>By {post.authorDisplayName}</span>
            <span className="text-border/40 mx-0.5">|</span>
            <time dateTime={post.publishedAt}>{formatDate(post.publishedAt)}</time>
            {post.commentCount > 0 && (
              <>
                <span className="text-border/40 mx-0.5">|</span>
                <span>{post.commentCount} comment{post.commentCount === 1 ? "" : "s"}</span>
              </>
            )}
          </div>
        </div>
        <div className="hidden sm:flex items-center pt-2 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
          <span className="text-amber text-lg">&rarr;</span>
        </div>
      </div>
      {post.tags.length > 0 && (
        <div className="tags mt-3">
          {post.tags.map((tag) => (
            <TagLink key={`${post.id}-${tag}`} tag={tag} postId={post.id} />
          ))}
        </div>
      )}
    </article>
  );
}
