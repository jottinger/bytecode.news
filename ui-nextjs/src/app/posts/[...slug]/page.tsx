import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { cache } from "react";
import { ApiError, getCommentsBySlug, getPageBySlug, getPostBySlug } from "@/lib/api";
import { formatDate, formatUpdatedTime } from "@/lib/format";
import { CommentThreadResponse, ContentDetail } from "@/lib/types";
import { CommentCreateForm } from "@/components/comment-create-form";
import { CommentThread } from "@/components/comment-thread";
import { HighlightedHtml } from "@/components/highlighted-html";
import { PostActions } from "@/components/post-actions";

const SUMMARY_FALLBACK = "Read the latest content on bytecode.news.";

const getResolvedPost = cache(async (slugPath: string, isDatedPostPath: boolean) => {
  return isDatedPostPath ? getPostBySlug(slugPath) : getPageBySlug(slugPath);
});

function normalizePath(slug: string[]) {
  const slugPath = slug.join("/");
  const [year = "", month = "", ...slugParts] = slug;
  const shortSlug = slugParts.join("/");
  const isDatedPostPath = Boolean(year && month && shortSlug);
  return { slugPath, year, month, shortSlug, isDatedPostPath };
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}): Promise<Metadata> {
  const resolved = await params;
  const { slugPath, isDatedPostPath } = normalizePath(resolved.slug);

  try {
    const post = await getResolvedPost(slugPath, isDatedPostPath);
    const description = post.excerpt?.trim() || SUMMARY_FALLBACK;

    return {
      title: post.title,
      description,
      openGraph: {
        title: post.title,
        description,
        type: "article",
      },
      twitter: {
        card: "summary_large_image",
        title: post.title,
        description,
      },
    };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return {
        title: "Post Not Found",
        description: "The requested post could not be found.",
      };
    }

    return {
      title: "Post Unavailable",
      description: "The requested post is temporarily unavailable.",
    };
  }
}

export default async function PostPage({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}) {
  const resolved = await params;
  const { slugPath, year, month, shortSlug, isDatedPostPath } = normalizePath(resolved.slug);

  let post: ContentDetail;
  try {
    post = await getResolvedPost(slugPath, isDatedPostPath);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    return (
      <section className="container max-w-screen-xl py-12">
        <div className="notice">
          <h2 className="font-display text-xl">Post Unavailable</h2>
          <p className="text-muted-foreground mt-2">The backend is temporarily unavailable. Please refresh shortly.</p>
        </div>
      </section>
    );
  }

  let thread: CommentThreadResponse | null = null;
  const commentsAllowed = !post.categories.includes("_sidebar");
  if (commentsAllowed) {
    try {
      thread = await getCommentsBySlug(slugPath);
    } catch {
      // Show post even if comments endpoint is currently unavailable.
    }
  }

  const updatedTime = formatUpdatedTime(post.updatedAt);

  return (
    <section className="py-8 md:py-12">
      <article className="max-w-3xl mx-auto px-6 md:px-8">
        <header>
          <div className="border-t-2 border-amber mb-6" />
          <h1 className="headline-lead text-foreground mb-4">{post.title}</h1>
          <div className="byline text-muted-foreground flex items-center gap-1.5 flex-wrap">
            <span>By {post.authorDisplayName}</span>
            <span className="text-border/60 mx-1">|</span>
            <time dateTime={post.publishedAt}>{formatDate(post.publishedAt)}</time>
            {updatedTime && (
              <>
                <span className="text-border/60 mx-1">|</span>
                <span>{updatedTime}</span>
              </>
            )}
          </div>
        </header>

        {post.tags.length > 0 && (
          <div className="tags mt-4">
            {post.tags.map((tag) => (
              <a className="tag" key={tag} href={`/tags/${encodeURIComponent(tag)}`}>{tag}</a>
            ))}
          </div>
        )}

        <div className="border-t border-border/40 mt-6 mb-6" />

        <HighlightedHtml className="post-body" html={post.renderedHtml} />

        <PostActions postId={post.id} />

        {commentsAllowed && (
          <section className="mt-10 border-t border-border/40 pt-6">
            <h2 className="font-display text-xl mb-4">
              Comments ({thread?.totalActiveCount ?? 0})
            </h2>
            {isDatedPostPath && <CommentCreateForm year={year} month={month} slug={shortSlug} />}
            {thread === null ? (
              <p className="text-muted-foreground">Comments are temporarily unavailable.</p>
            ) : thread.comments.length === 0 ? (
              <p className="text-muted-foreground">No comments yet.</p>
            ) : (
              <CommentThread comments={thread.comments} year={year} month={month} slug={shortSlug} />
            )}
          </section>
        )}
      </article>
    </section>
  );
}
