import type { Metadata } from "next";
import Link from "next/link";
import { listPosts } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { ContentListResponse, ContentSummary } from "@/lib/types";

export const metadata: Metadata = {
  title: "bytecode.news",
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

function formatMastheadDate(): string {
  return new Date().toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  }).toUpperCase();
}

function buildPostHref(post: ContentSummary): string {
  return `/posts/${post.slug}`;
}

export default async function Home({
  searchParams,
}: {
  searchParams?: Promise<{ page?: string }>;
}) {
  const params = (await searchParams) || {};
  const page = Number.parseInt(params.page || "0", 10);
  const safePage = Number.isFinite(page) && page >= 0 ? page : 0;

  let list: ContentListResponse | null = null;
  try {
    list = await listPosts(safePage, 9);
  } catch {
    return (
      <section className="container max-w-screen-xl py-12">
        <div className="notice">
          <h2 className="font-display text-xl">Backend Temporarily Unavailable</h2>
          <p className="text-muted-foreground mt-2">The blog API is currently unreachable. Please refresh in a moment.</p>
        </div>
      </section>
    );
  }

  const posts = list.posts;
  const lead = posts[0] ?? null;
  const secondary = posts.slice(1, 3);
  const belowFold = posts.slice(3, 9);
  const isFrontPage = safePage === 0;

  return (
    <>
      {/* Masthead */}
      <section className="border-b border-border/30">
        <div className="container max-w-screen-xl">
          <div className="border-t-[3px] border-amber animate-rule-draw" />

          <div className="py-10 md:py-14 text-center">
            <p
              className="dateline text-amber-dim/70 mb-4 animate-fade-in"
              style={{ animationDelay: "100ms" }}
            >
              {formatMastheadDate()}
            </p>

            <h1
              className="masthead-name text-foreground animate-press-in"
              style={{ animationDelay: "200ms" }}
            >
              bytecode<span className="text-amber">.</span>news
            </h1>

            <div
              className="flex items-center justify-center gap-4 mt-5 animate-fade-in"
              style={{ animationDelay: "400ms" }}
            >
              <div className="h-px w-12 bg-amber/20" />
              <p className="section-label text-muted-foreground/50">
                Programming News &amp; Technical Writing
              </p>
              <div className="h-px w-12 bg-amber/20" />
            </div>
          </div>

          <div className="border-t border-amber/30" />
          <div className="border-t border-amber/30 mt-px" />
        </div>
      </section>

      {/* Content */}
      <section className="container max-w-screen-xl py-8 md:py-12">
        {/* Section header */}
        <div className="flex items-center gap-4 mb-8">
          <span className="section-label text-amber font-medium">
            Latest Dispatches
          </span>
          <div className="flex-1 border-t border-border/40" />
        </div>

        {posts.length === 0 ? (
          <EmptyEdition />
        ) : (
          <div className="animate-press-in">
            {isFrontPage ? (
              <>
                {/* Lead + secondary grid */}
                <div className="grid grid-cols-1 md:grid-cols-5 gap-0">
                  {lead && (
                    <div className="md:col-span-3 md:border-r md:border-border/30 md:pr-8">
                      <LeadCard post={lead} />
                    </div>
                  )}

                  {secondary.length > 0 && (
                    <div className="md:col-span-2 md:pl-8 mt-8 md:mt-0">
                      {secondary.map((post, i) => (
                        <div
                          key={post.id}
                          className={i > 0 ? "border-t border-border/30 pt-6 mt-6" : ""}
                        >
                          <SecondaryCard post={post} />
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Below fold */}
                {belowFold.length > 0 && (
                  <>
                    <div className="my-10 relative">
                      <div className="border-t-2 border-amber/20" />
                      <span className="section-label text-amber/40 absolute left-0 -top-2.5 bg-background px-2 pl-0">
                        More Stories
                      </span>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-0">
                      {belowFold.map((post, i) => (
                        <div
                          key={post.id}
                          className="border-t border-border/20 py-6 first:border-t-0 sm:[&:nth-child(-n+2)]:border-t-0 lg:[&:nth-child(-n+3)]:border-t-0"
                          style={{ animationDelay: `${(i + 3) * 80}ms` }}
                        >
                          <BriefCard post={post} />
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-0">
                {posts.map((post, i) => (
                  <div
                    key={post.id}
                    className="border-t border-border/20 py-6 first:border-t-0 sm:[&:nth-child(-n+2)]:border-t-0 lg:[&:nth-child(-n+3)]:border-t-0"
                    style={{ animationDelay: `${i * 80}ms` }}
                  >
                    <BriefCard post={post} />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Pagination */}
        <div className="pagination">
          <div>
            {safePage > 0 ? (
              <Link className="pagelink" href={`/?page=${safePage - 1}`}>
                &larr; Previous
              </Link>
            ) : null}
          </div>
          <div>
            {safePage + 1 < list.totalPages ? (
              <Link className="pagelink" href={`/?page=${safePage + 1}`}>
                Next &rarr;
              </Link>
            ) : null}
          </div>
        </div>
      </section>
    </>
  );
}

function LeadCard({ post }: { post: ContentSummary }) {
  const href = buildPostHref(post);
  return (
    <article className="group">
      <div className="byline text-muted-foreground mb-4 flex items-center gap-2">
        <span>By {post.authorDisplayName}</span>
        <span className="text-border">|</span>
        <time dateTime={post.publishedAt}>{formatDate(post.publishedAt)}</time>
        {post.commentCount > 0 && (
          <>
            <span className="text-border">|</span>
            <span>{post.commentCount} comment{post.commentCount === 1 ? "" : "s"}</span>
          </>
        )}
      </div>

      <h2 className="headline-lead text-foreground group-hover:text-amber transition-colors duration-200">
        <Link href={href}>{post.title}</Link>
      </h2>

      {post.excerpt && (
        <p className="article-excerpt mt-4 max-w-2xl">
          {post.excerpt}
        </p>
      )}

      {post.tags.length > 0 && (
        <div className="tags mt-4">
          {post.tags.map((tag) => (
            <a className="tag" key={`${post.id}-${tag}`} href={`/tags/${encodeURIComponent(tag)}`}>
              {tag}
            </a>
          ))}
        </div>
      )}

      <div className="mt-5 flex items-center gap-2">
        <div className="h-px w-6 bg-amber/40 transition-all duration-300 group-hover:w-10 group-hover:bg-amber" />
        <Link
          href={href}
          className="section-label text-amber-dim group-hover:text-amber transition-colors"
        >
          Read article
        </Link>
      </div>
    </article>
  );
}

function SecondaryCard({ post }: { post: ContentSummary }) {
  const href = buildPostHref(post);
  return (
    <article className="group">
      <div className="byline text-muted-foreground/70 mb-2.5 flex items-center gap-2">
        <time dateTime={post.publishedAt}>{formatDate(post.publishedAt)}</time>
      </div>

      <h3 className="headline-secondary text-foreground group-hover:text-amber transition-colors duration-200">
        <Link href={href}>{post.title}</Link>
      </h3>

      {post.excerpt && (
        <p className="text-muted-foreground text-sm leading-relaxed mt-2.5 line-clamp-3">
          {post.excerpt}
        </p>
      )}

      <p className="byline text-muted-foreground/50 mt-3">
        By {post.authorDisplayName}
      </p>
    </article>
  );
}

function BriefCard({ post }: { post: ContentSummary }) {
  const href = buildPostHref(post);
  return (
    <article className="group">
      <h4 className="headline-brief text-foreground group-hover:text-amber transition-colors duration-200">
        <Link href={href}>{post.title}</Link>
      </h4>

      {post.excerpt && (
        <p className="text-muted-foreground/70 text-sm leading-relaxed mt-2 line-clamp-2">
          {post.excerpt}
        </p>
      )}

      <div className="byline text-muted-foreground/50 mt-2.5 flex items-center gap-2">
        <span>{post.authorDisplayName}</span>
        <span className="text-border/40">|</span>
        <time dateTime={post.publishedAt}>{formatDate(post.publishedAt)}</time>
      </div>
    </article>
  );
}

function EmptyEdition() {
  return (
    <div className="animate-fade-in py-16 max-w-md mx-auto text-center">
      <div className="border-t-2 border-amber/30 mb-8" />
      <p className="section-label text-amber/60 mb-4">Notice</p>
      <p className="font-display text-2xl text-foreground/80 tracking-tight leading-snug">
        The editorial team is preparing the first edition.
      </p>
      <p className="text-muted-foreground/50 text-sm mt-4 leading-relaxed">
        Articles will appear here once published. Check back soon for
        technical writing, engineering insights, and dispatches from the
        world of software.
      </p>
      <div className="border-t-2 border-amber/30 mt-8" />
    </div>
  );
}
